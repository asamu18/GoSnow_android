package com.gosnow.app.ui.record.party

import android.location.Location
import android.util.Log
import com.gosnow.app.datasupabase.CurrentUserStore
import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.snowcircle.data.party.PartyUserRepository
import com.gosnow.app.ui.snowcircle.model.PartyMember
import com.gosnow.app.ui.snowcircle.model.PartyState
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

class PartyRideManager(private val scope: CoroutineScope) {
    private val client get() = SupabaseClientProvider.supabaseClient
    private val myUserId get() = client.auth.currentUserOrNull()?.id ?: ""

    private val _state = MutableStateFlow<PartyState>(PartyState.Idle)
    val state = _state.asStateFlow()

    private val _statusEvent = MutableSharedFlow<String>(replay = 0)
    val statusEvent = _statusEvent.asSharedFlow()

    private var channel: RealtimeChannel? = null
    private var isConnecting = false

    // 心跳任务
    private var heartbeatJob: Job? = null
    private var lastKnownLocation: Pair<Double, Double>? = null

    // 本地缓存：userId -> Member
    private val memberCache = mutableMapOf<String, PartyMember>()

    companion object {
        private const val TAG = "PartyDebug"
    }

    fun createParty() {
        val code = String.format("%04d", (0..9999).random())
        joinParty(code, isCreating = true)
    }

    fun joinParty(code: String, isCreating: Boolean = false) {
        if (isConnecting) return
        val currentId = myUserId
        if (currentId.isEmpty()) {
            emitStatus("未登录，无法加入")
            return
        }

        isConnecting = true
        scope.launch {
            try {
                Log.d(TAG, "=== 开始加入流程: $code ===")
                emitStatus(if (isCreating) "正在创建..." else "正在加入...")

                // 1. 数据库操作
                if (isCreating) {
                    PartyRepository.createPartyInDb(currentId, code)
                } else {
                    val partyId = PartyRepository.findPartyIdByCode(code)
                        ?: throw IllegalStateException("无效的邀请码")
                    PartyRepository.joinPartyDb(partyId, currentId)
                }

                // 2. 清理旧状态
                cleanup()

                // 3. 更新 UI
                _state.value = PartyState.Joined(code, emptyList(), isHost = isCreating)

                // 4. 连接 Realtime
                connectRealtime(code, currentId)

                emitStatus("加入成功，等待队友...")

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "加入失败: ${e.message}")
                emitStatus("操作失败: ${e.message}")
                _state.value = PartyState.Idle
            } finally {
                isConnecting = false
            }
        }
    }

    private suspend fun connectRealtime(code: String, myId: String) {
        val ch = client.realtime.channel("room_$code")
        channel = ch

        // A. 监听队友状态变化
        scope.launch {
            ch.presenceChangeFlow().collect { action ->
                handlePresenceAction(action)
            }
        }

        // B. 监听连接状态
        scope.launch {
            ch.status.collect { status ->
                Log.d(TAG, "通道状态变更: $status")
            }
        }

        // C. 订阅并等待连接成功
        Log.d(TAG, "正在订阅通道...")
        ch.subscribe()

        try {
            // 阻塞等待直到状态变为 SUBSCRIBED
            withTimeout(10000L) {
                ch.status.filter { it == RealtimeChannel.Status.SUBSCRIBED }.first()
            }
            Log.d(TAG, "通道已连接 (SUBSCRIBED)，开始发送心跳")

            // D. 连接成功后，立即启动心跳广播
            startHeartbeat(myId)

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "连接超时！Realtime 可能未开启或网络受限")
            emitStatus("连接超时，请重试")
        }
    }

    private fun startHeartbeat(myId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            Log.d(TAG, "心跳任务启动")
            // 立即发第一次
            val (lat, lon) = lastKnownLocation ?: (0.0 to 0.0)
            broadcastLocation(myId, lat, lon, isInit = true)

            while (isActive) {
                delay(3000) // 每3秒一次
                val (currLat, currLon) = lastKnownLocation ?: (0.0 to 0.0)
                broadcastLocation(myId, currLat, currLon)
            }
        }
    }

    fun onMyLocationUpdate(location: Location) {
        val myId = myUserId
        if (_state.value !is PartyState.Joined || myId.isEmpty()) return

        lastKnownLocation = location.latitude to location.longitude

        // 有位置移动时，立即广播
        scope.launch(Dispatchers.IO) {
            broadcastLocation(myId, location.latitude, location.longitude)
        }
    }

    // ✅ 这就是你报错缺失的函数
    private suspend fun broadcastLocation(userId: String, lat: Double, lon: Double, isInit: Boolean = false) {
        val activeChannel = channel ?: return

        if (activeChannel.status.value != RealtimeChannel.Status.SUBSCRIBED) {
            return
        }

        val profile = CurrentUserStore.profile.value
        val name = profile?.userName ?: "雪友${userId.take(4)}"
        val avatar = profile?.avatarUrl ?: ""

        try {
            activeChannel.track(buildJsonObject {
                put("u", userId)
                put("lat", lat)
                put("lon", lon)
                put("n", name)
                put("a", avatar)
                if (isInit) put("init", true)
                put("ts", System.currentTimeMillis())
            })
        } catch (e: Exception) {
            Log.e(TAG, "广播失败: ${e.message}")
        }
    }

    // =========================================================
    // ✅ 核心修复：防止加入后立马退出的逻辑
    // =========================================================
    private suspend fun handlePresenceAction(action: PresenceAction) {
        val myId = myUserId

        Log.d(TAG, "收到 Presence 事件! Joins: ${action.joins.size}, Leaves: ${action.leaves.size}")

        // 1. 先收集这一波 "Joins" (正在更新/加入) 的所有用户 ID
        val joiningUserIds = mutableSetOf<String>()

        action.joins.values.forEach { presence ->
            val userId = parseAndCachePresence(presence, myId)
            if (userId != null) {
                joiningUserIds.add(userId)
            }
        }

        // 2. 处理离开
        action.leaves.values.forEach { presence ->
            try {
                val json = presence.state.jsonObject
                val userId = json["u"]?.jsonPrimitive?.content
                    ?: json["userId"]?.jsonPrimitive?.content

                // ✅ 只有当这个 userId 不在 joiningUserIds 集合里时，才说明是真的退出了
                if (userId != null && !joiningUserIds.contains(userId)) {
                    Log.d(TAG, "用户彻底离开: $userId")
                    memberCache.remove(userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. 刷新 UI
        val newList = memberCache.values.toList()
        Log.d(TAG, "刷新 UI 列表，当前队友数: ${newList.size}")

        _state.update { s ->
            if (s is PartyState.Joined) s.copy(members = newList) else s
        }
    }

    private fun parseAndCachePresence(presence: Presence, myId: String): String? {
        try {
            val json = presence.state.jsonObject
            val userId = json["u"]?.jsonPrimitive?.content
                ?: json["userId"]?.jsonPrimitive?.content

            if (userId == null) return null
            if (userId == myId) return userId // 返回自己的ID但不存缓存

            val lat = json["lat"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val lon = json["lon"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val name = json["n"]?.jsonPrimitive?.content ?: "队友"
            val avatar = json["a"]?.jsonPrimitive?.content

            val member = PartyMember(userId, lat, lon, avatar, name)
            memberCache[userId] = member

            return userId
        } catch (e: Exception) {
            Log.e(TAG, "解析 Presence JSON 出错: ${e.message}")
            return null
        }
    }

    fun leaveParty() {
        scope.launch {
            cleanup()
            _state.value = PartyState.Idle
            emitStatus("已离开")
        }
    }

    private suspend fun cleanup() {
        heartbeatJob?.cancel()
        try { channel?.unsubscribe() } catch (_: Exception) {}
        channel = null
        memberCache.clear()
        lastKnownLocation = null
    }

    private fun emitStatus(msg: String) {
        scope.launch { _statusEvent.emit(msg) }
    }
}