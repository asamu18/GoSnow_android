package com.gosnow.app.ui.record

import android.app.Application
import android.content.*
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.record.service.ForegroundRecordingService
import com.gosnow.app.ui.record.party.PartyRideManager
import com.gosnow.app.ui.record.track.LiveTrackController
import com.gosnow.app.ui.record.track.StaticTrackGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID
import com.gosnow.app.ui.snowcircle.model.PartyState // 👈 添加这一行

// ...

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

    // ✅ 新增：用于强行触发地图 UI 刷新的计数器
    var trackUpdateTick by mutableIntStateOf(0)
        private set

    val partyManager = PartyRideManager(viewModelScope)
    val partyState = partyManager.state
    val trackController = LiveTrackController()

    private var service: ForegroundRecordingService? = null
    private var collectJob: Job? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? ForegroundRecordingService.LocalBinder ?: return
            val srv = b.getService()
            service = srv
            bound = true

            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                srv.stateFlow.collect { s ->
                    isRecording = s.isRecording
                    distanceKm = s.distanceKm
                    maxSpeedKmh = s.topSpeedKmh
                    verticalDropM = s.verticalDropM
                    motionMode = s.motionMode
                    durationText = formatDuration(s.durationSec)
                }
            }

            srv.onLocationUpdate = { loc, speedKmh ->
                // ✅ 核心修改：逻辑拆分

                // 1. 只有“录制中”才记录轨迹数据
                if (srv.stateFlow.value.isRecording) {
                    trackController.addPoint(loc, speedKmh)
                }

                // 2. ✅ 无论是否录制，都强制刷新 UI 计数器 (为了让地图重绘当前位置和队友)
                trackUpdateTick++

                // 3. ✅ 无论是否录制，都向小队广播我的位置 (只要我加入了小队)
                partyManager.onMyLocationUpdate(loc)

                Log.d("TrackDebug", "收到位置 [${trackUpdateTick}]: ${loc.latitude}, ${loc.longitude}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            collectJob?.cancel()
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
    }

    fun onToggleRecording() {
        if (!isRecording) startRecording() else stopRecording()
    }

    fun startRecording() {
        //trackController.reset()
        trackUpdateTick = 0
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun stopRecording() {
        val sessionId = UUID.randomUUID().toString()
        val segments = trackController.getSegmentsSnapshot()
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_STOP
        }
        ctx.startService(intent)
        if (segments.isNotEmpty()) {
            viewModelScope.launch {
                StaticTrackGenerator.generateAndSave(getApplication(), sessionId, segments)
            }
        }
    }

    private fun formatDuration(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    init {
        viewModelScope.launch {
            partyState.collect { state ->
                if (state is PartyState.Joined) {
                    // 当刚加入小队时，如果我有最近的位置，强制广播一次
                    // 这里需要 access 到当前的 location，或者等待下一次 location update
                    // 由于 Service 每秒都在回调，通常这个问题只要你在动就能解决。
                    // 为了稳妥，可以在 Service 连接时缓存 lastLocation
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}