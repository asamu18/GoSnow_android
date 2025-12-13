package com.gosnow.app.ui.record

import android.app.Application
import android.content.*
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.record.service.ForegroundRecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.*

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

    private var service: ForegroundRecordingService? = null
    private var collectJob: Job? = null
    private var bound = false

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? ForegroundRecordingService.LocalBinder ?: return
            service = b.getService()
            bound = true

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
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(ctx, intent)
    }

    fun stopRecording() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, ForegroundRecordingService::class.java).apply {
            action = ForegroundRecordingService.ACTION_STOP
        }
        // 发一个 command 让 service 自己 stop
        ctx.startService(intent)
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
