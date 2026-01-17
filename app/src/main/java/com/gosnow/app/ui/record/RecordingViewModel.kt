package com.gosnow.app.ui.record

import android.app.Application
import android.content.*
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.record.service.ForegroundRecordingService
import com.gosnow.app.ui.record.party.PartyRideManager
import com.gosnow.app.ui.record.track.LiveTrackController
import com.gosnow.app.ui.record.track.StaticTrackGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import java.util.UUID

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    var isRecording by mutableStateOf(false)
        private set
    var durationText by mutableStateOf("00:00")
        private set
    var distanceKm by mutableStateOf(0.0)
        private set
    var maxSpeedKmh by mutableStateOf(0.0)
        private set
    var verticalDropM by mutableStateOf(0)
        private set
    var motionMode by mutableStateOf(MotionMode.ACTIVE)
        private set

    // ✅ 新增：小队管理
    val partyManager = PartyRideManager(viewModelScope)
    val partyState = partyManager.state

    // ✅ 新增：轨迹管理
    val trackController = LiveTrackController()

    // ✅ 新增：通知 UI 更新地图的信号 (因为 Mapbox 不是 Compose State 驱动的)
    private val _trackUpdateEvent = MutableSharedFlow<Unit>(replay = 0)
    val trackUpdateEvent = _trackUpdateEvent.asSharedFlow()

    private var service: ForegroundRecordingService? = null
    private var collectJob: Job? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? ForegroundRecordingService.LocalBinder ?: return
            service = b.getService()
            bound = true

            // 1. 订阅 UI 状态更新
            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                service!!.stateFlow.collect { s ->
                    isRecording = s.isRecording
                    distanceKm = s.distanceKm
                    maxSpeedKmh = s.topSpeedKmh
                    verticalDropM = s.verticalDropM
                    motionMode = s.motionMode
                    durationText = formatDuration(s.durationSec)
                }
            }

            // 2. ✅ 连接高频位置数据到 Party 和 Track 模块
            service!!.onLocationUpdate = { loc, speedKmh ->
                if (isRecording) {
                    // 喂给小队：广播我的位置
                    partyManager.onMyLocationUpdate(loc)
                    // 喂给轨迹：绘制线段
                    trackController.addPoint(loc, speedKmh)
                    // 通知 UI 刷新 Mapbox
                    _trackUpdateEvent.tryEmit(Unit)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            collectJob?.cancel()
            collectJob = null
        }
    }

    fun bindService() {
        if (bound) return
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java)
        ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (!bound) return
        val ctx = getApplication<Application>()
        runCatching { ctx.unbindService(conn) }
        bound = false
        service = null
        collectJob?.cancel()
        collectJob = null
    }

    fun onToggleRecording() {
        if (!isRecording) startRecording() else stopRecording()
    }

    fun startRecording() {
        // 开始前重置轨迹
        trackController.reset()

        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun stopRecording() {
        val sessionId = UUID.randomUUID().toString()
        val segments = trackController.getSegmentsSnapshot()

        // 停止服务
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_STOP
        }
        ctx.startService(intent)

        // ✅ 录制结束：生成离线轨迹图
        if (segments.isNotEmpty()) {
            viewModelScope.launch {
                StaticTrackGenerator.generateAndSave(getApplication(), sessionId, segments)
            }
        }
    }

    private fun formatDuration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}