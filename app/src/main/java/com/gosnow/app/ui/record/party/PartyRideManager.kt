package com.gosnow.app.ui.record.party

import android.location.Location
import android.util.Log
import com.gosnow.app.datasupabase.SupabaseClientProvider
import com.gosnow.app.ui.snowcircle.data.party.PartyUserRepository
import com.gosnow.app.ui.snowcircle.model.PartyLocationMessage
import com.gosnow.app.ui.snowcircle.model.PartyMember
import com.gosnow.app.ui.snowcircle.model.PartyState
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PartyRideManager(private val scope: CoroutineScope) {
    private val client get() = SupabaseClientProvider.supabaseClient
    private val myUserId get() = client.auth.currentUserOrNull()?.id ?: ""

    private val _state = MutableStateFlow<PartyState>(PartyState.Idle)
    val state = _state.asStateFlow()

    private val _statusEvent = MutableSharedFlow<String>(replay = 0)
    val statusEvent = _statusEvent.asSharedFlow()

    private var isConnecting = false
    private var channel: RealtimeChannel? = null
    private var lastBroadcastTime = 0L
    private val THROTTLE_MS = 1000L

    fun createParty() {
        val code = String.format("%04d", (0..9999).random())
        joinParty(code)
    }

    fun joinParty(code: String) {
        if (isConnecting) return
        val currentId = myUserId
        if (currentId.isEmpty()) {
            scope.launch { _statusEvent.emit("错误：请先登录") }
            return
        }

        isConnecting = true

        scope.launch {
            try {
                _statusEvent.emit("正在连接小队 $code...")

                // 清理旧频道
                channel?.unsubscribe()

                val ch = client.realtime.channel("party:$code")

                // 设置监听
                val job = scope.launch {
                    ch.broadcastFlow<PartyLocationMessage>("loc").collect { msg ->
                        if (msg.user_id != currentId) {
                            handleTeammateLocation(msg)
                        }
                    }
                }

                // 尝试订阅，增加超时控制（10秒）
                withTimeout(10000L) {
                    ch.subscribe()
                }

                channel = ch
                _state.value = PartyState.Joined(code, emptyList())
                _statusEvent.emit("成功进入小队")
                Log.d("PartyRideManager", "Joined channel: $code")

            } catch (e: TimeoutCancellationException) {
                _statusEvent.emit("连接超时，请检查网络")
                _state.value = PartyState.Idle
            } catch (e: Exception) {
                Log.e("PartyRideManager", "Join error: ${e.message}")
                _statusEvent.emit("加入失败: ${e.localizedMessage}")
                _state.value = PartyState.Idle
            } finally {
                isConnecting = false
            }
        }
    }

    fun leaveParty() {
        scope.launch {
            try {
                channel?.unsubscribe()
                channel = null
                _state.value = PartyState.Idle
                isConnecting = false
                _statusEvent.emit("已退出小队")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onMyLocationUpdate(location: Location) {
        val currentState = _state.value
        if (currentState !is PartyState.Joined) return

        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime > THROTTLE_MS) {
            lastBroadcastTime = now
            val currentId = myUserId
            if (currentId.isEmpty()) return

            scope.launch(Dispatchers.IO) {
                try {
                    val payload = PartyLocationMessage(
                        user_id = currentId,
                        lat = location.latitude,
                        lon = location.longitude,
                        avatar_url = null
                    )
                    channel?.broadcast("loc", payload)
                } catch (e: Exception) {
                    Log.e("PartyRideManager", "Broadcast error: ${e.message}")
                }
            }
        }
    }

    private fun handleTeammateLocation(msg: PartyLocationMessage) {
        scope.launch {
            val user = PartyUserRepository.getUserInfo(msg.user_id)
            val name = user?.displayName ?: "雪友 ${msg.user_id.take(4)}"

            _state.update { s ->
                if (s is PartyState.Joined) {
                    val currentMembers = s.members.toMutableList()
                    val idx = currentMembers.indexOfFirst { it.userId == msg.user_id }
                    val newMember = PartyMember(
                        userId = msg.user_id,
                        lat = msg.lat, lon = msg.lon,
                        avatarUrl = user?.avatarUrl, userName = name
                    )
                    if (idx != -1) currentMembers[idx] = newMember
                    else if (currentMembers.size < 6) currentMembers.add(newMember)
                    s.copy(members = currentMembers)
                } else s
            }
        }
    }
}