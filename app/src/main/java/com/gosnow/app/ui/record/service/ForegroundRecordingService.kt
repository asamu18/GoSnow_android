package com.gosnow.app.ui.record.service

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gosnow.app.R
import com.gosnow.app.MainActivity
import com.gosnow.app.ui.record.LocationManager.SamplingMode
import com.gosnow.app.ui.record.LocationManager.SystemLocationService
import com.gosnow.app.ui.record.SkiSession
import com.gosnow.app.ui.record.classifier.LiftRideClassifier
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.record.sensors.BarometerAltimeter
import com.gosnow.app.ui.record.storage.BasicMetricsComputer
import com.gosnow.app.ui.record.storage.FileSessionStore
import com.gosnow.app.ui.record.storage.SupabaseSessionRepository // 确保有这个
import com.gosnow.app.datasupabase.SupabaseClientProvider
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RecordingLiveState(
    val isRecording: Boolean = false,
    val durationSec: Int = 0,
    val distanceKm: Double = 0.0,
    val topSpeedKmh: Double = 0.0,
    val verticalDropM: Int = 0,
    val motionMode: MotionMode = MotionMode.ACTIVE
)

class ForegroundRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.gosnow.app.action.RECORD_START"
        const val ACTION_STOP = "com.gosnow.app.action.RECORD_STOP"
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 1001
    }

    inner class LocalBinder : Binder() {
        fun getService(): ForegroundRecordingService = this@ForegroundRecordingService
    }

    private val binder = LocalBinder()
    private val _state = MutableStateFlow(RecordingLiveState())
    val stateFlow: StateFlow<RecordingLiveState> = _state
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ✅ 新增：高频位置回调，供 ViewModel 连接 Party 和 Track 模块
    // 参数：Location, Speed (km/h)
    var onLocationUpdate: ((Location, Double) -> Unit)? = null

    private lateinit var locationService: SystemLocationService
    private lateinit var metrics: BasicMetricsComputer
    private lateinit var classifier: LiftRideClassifier
    private var barometer: BarometerAltimeter? = null
    private lateinit var store: FileSessionStore

    private var sessionStartMillis: Long = 0L
    private var lastSamplingMode: SamplingMode? = null
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        locationService = SystemLocationService(applicationContext)
        metrics = BasicMetricsComputer()
        classifier = LiftRideClassifier()
        store = FileSessionStore(applicationContext)

        val b = BarometerAltimeter(applicationContext)
        barometer = if (b.isAvailable()) b else null

        locationService.onLocationSample = { loc ->
            val altOverride = barometer?.altitudeM?.toDouble()
            val mode = classifier.consume(loc, altOverride)
            metrics.setLiftMode(mode == MotionMode.LIFT || mode == MotionMode.IDLE)

            val desired = if (mode == MotionMode.ACTIVE) SamplingMode.Active else SamplingMode.Idle
            if (desired != lastSamplingMode) {
                locationService.setSamplingMode(desired)
                lastSamplingMode = desired
            }

            metrics.consumeSample(loc, altitudeOverrideM = altOverride)

            // ✅ 核心修改：触发回调，把原始 Location 传给 ViewModel
            val speedKmh = metrics.currentSpeedKmh
            onLocationUpdate?.invoke(loc, speedKmh)

            if (_state.value.isRecording) {
                val dur = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).toInt().coerceAtLeast(0)
                _state.value = _state.value.copy(
                    durationSec = dur,
                    distanceKm = metrics.distanceKm,
                    topSpeedKmh = metrics.topSpeedKmh,
                    verticalDropM = metrics.verticalDropM,
                    motionMode = mode
                )
            }
        }
    }

    // 2. ✅ 新增: 绑定时立即开启定位 (为了地图预览和队友位置)
    override fun onBind(intent: Intent?): IBinder {
        // 绑定时开启定位采样（不开启录制状态）
        locationService.start()
        return binder
    }

    // 3. ✅ 新增: 解绑时，如果不在录制中，则停止定位以省电
    override fun onUnbind(intent: Intent?): Boolean {
        if (!_state.value.isRecording) {
            locationService.stop()
            barometer?.stop()
        }
        return super.onUnbind(intent)
    }
    // ✅ 核心修复：必须重写 onStartCommand 来接收 ViewModel 发来的指令
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording() // 👈 这里调用了 startRecording，它就不再是灰色的了
            ACTION_STOP -> stopRecording()
        }
        // START_STICKY 表示如果服务被意外杀死，系统会尝试重启它（这对录制服务很重要）
        return START_STICKY
    }

    private fun startRecording() {
        if (_state.value.isRecording) return
        sessionStartMillis = System.currentTimeMillis()
        metrics.reset()
        barometer?.start()
        lastSamplingMode = SamplingMode.Active
        locationService.setSamplingMode(SamplingMode.Active)
        locationService.start()

        _state.value = RecordingLiveState(
            isRecording = true,
            durationSec = 0, distanceKm = 0.0, topSpeedKmh = 0.0, verticalDropM = 0, motionMode = MotionMode.ACTIVE
        )
        startForeground(NOTIF_ID, buildNotification(_state.value))

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && _state.value.isRecording) {
                val dur = ((System.currentTimeMillis() - sessionStartMillis) / 1000L).toInt().coerceAtLeast(0)
                _state.value = _state.value.copy(durationSec = dur)
                updateNotification(_state.value)
                delay(1000L)
            }
        }
    }

    private fun stopRecording() {
        if (!_state.value.isRecording) {
            stopSelf()
            return
        }
        locationService.stop()
        barometer?.stop()

        val end = System.currentTimeMillis()
        val durationSec = ((end - sessionStartMillis) / 1000L).toInt().coerceAtLeast(0)

        val session = SkiSession(
            startAtMillis = sessionStartMillis,
            endAtMillis = end,
            durationSec = durationSec,
            distanceKm = metrics.distanceKm,
            topSpeedKmh = metrics.topSpeedKmh,
            avgSpeedKmh = if (durationSec > 0) metrics.distanceKm / (durationSec / 3600.0) else 0.0,
            verticalDropM = metrics.verticalDropM
        )

        serviceScope.launch(Dispatchers.IO) {
            // 1. 存本地
            runCatching { store.saveSession(session) }

            // 2. 传云端
            val client = SupabaseClientProvider.supabaseClient
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                runCatching {
                    SupabaseSessionRepository(client).uploadSession(session, user.id)
                }
            }
        }

        tickerJob?.cancel()
        tickerJob = null
        _state.value = _state.value.copy(isRecording = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(s: RecordingLiveState): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPI = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, ForegroundRecordingService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = "用时 ${formatDuration(s.durationSec)} · ${String.format("%.1f", s.distanceKm)} km · ${statusText(s)}"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("正在记录滑雪")
            .setContentText(content)
            .setContentIntent(openPI)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "结束", stopPI)
            .build()
    }

    private fun updateNotification(s: RecordingLiveState) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(s))
    }

    private fun statusText(s: RecordingLiveState): String {
        if (!s.isRecording) return "未开始"
        return when (s.motionMode) {
            MotionMode.LIFT -> "缆车中"
            MotionMode.IDLE -> "静止中"
            MotionMode.ACTIVE -> "滑行中"
        }
    }

    private fun formatDuration(sec: Int): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "滑雪记录", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(ch)
    }

    override fun onDestroy() {
        super.onDestroy()
        tickerJob?.cancel()
        serviceScope.cancel()
        runCatching { locationService.stop() }
        runCatching { barometer?.stop() }
    }
}