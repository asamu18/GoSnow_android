package com.gosnow.app.ui.record

import com.gosnow.app.ui.record.LocationManager.LocationService
import com.gosnow.app.ui.record.LocationManager.SamplingMode
import com.gosnow.app.ui.record.classifier.LiftRideClassifier
import com.gosnow.app.ui.record.classifier.MotionMode
import com.gosnow.app.ui.record.sensors.BarometerAltimeter
import com.gosnow.app.ui.record.storage.BasicMetricsComputer

class BasicSessionRecorder(
    private val locationService: LocationService,
    private val metrics: BasicMetricsComputer,
    private val barometer: BarometerAltimeter? = null,
    private val classifier: LiftRideClassifier = LiftRideClassifier()
) : SessionRecorder {

    private var _state: RecordingState = RecordingState.Idle
    override val state: RecordingState get() = _state

    override val currentSpeedKmh: Double get() = metrics.currentSpeedKmh
    override val distanceKm: Double get() = metrics.distanceKm
    override val topSpeedKmh: Double get() = metrics.topSpeedKmh
    override val verticalDropM: Int get() = metrics.verticalDropM

    private var _motionMode: MotionMode = MotionMode.ACTIVE
    override val motionMode: MotionMode get() = _motionMode

    private var sessionStartMillis: Long = 0L
    private var lastSamplingMode: SamplingMode? = null

    init {
        locationService.onLocationSample = { loc ->
            val altOverride = barometer?.altitudeM?.toDouble()

            // 1) 自动识别（高度优先用气压计）
            val mode = classifier.consume(loc, altOverride)
            _motionMode = mode

            // 2) 缆车/静止时：更保守（不刷最高速、不计里程、落差更严）
            metrics.setLiftMode(mode == MotionMode.LIFT || mode == MotionMode.IDLE)

            // 3) 自动切采样频率
            val desired = if (mode == MotionMode.ACTIVE) SamplingMode.Active else SamplingMode.Idle
            if (desired != lastSamplingMode) {
                locationService.setSamplingMode(desired)
                lastSamplingMode = desired
            }

            // 4) 喂给统计（落差优先气压计高度）
            metrics.consumeSample(loc, altitudeOverrideM = altOverride)
        }
    }

    override fun start() {
        if (_state != RecordingState.Idle) return

        metrics.reset()
        sessionStartMillis = System.currentTimeMillis()

        if (barometer?.isAvailable() == true) barometer.start()

        lastSamplingMode = SamplingMode.Active
        locationService.setSamplingMode(SamplingMode.Active)
        locationService.start()

        _motionMode = MotionMode.ACTIVE
        _state = RecordingState.Recording
    }

    override fun stop(): SkiSession? {
        if (_state != RecordingState.Recording) return null

        locationService.stop()
        barometer?.stop()

        val end = System.currentTimeMillis()
        _state = RecordingState.Idle

        val durationSec = ((end - sessionStartMillis) / 1000L).toInt().coerceAtLeast(0)
        val distance = metrics.distanceKm
        val topSpeed = metrics.topSpeedKmh
        val vertical = metrics.verticalDropM
        val avgSpeed = if (durationSec > 0) distance / (durationSec / 3600.0) else 0.0

        return SkiSession(
            startAtMillis = sessionStartMillis,
            endAtMillis = end,
            durationSec = durationSec,
            distanceKm = distance,
            topSpeedKmh = topSpeed,
            avgSpeedKmh = avgSpeed,
            verticalDropM = vertical
        )
    }
}
