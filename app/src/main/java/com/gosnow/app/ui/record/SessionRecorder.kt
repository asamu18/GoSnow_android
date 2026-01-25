package com.gosnow.app.ui.record

import com.gosnow.app.ui.record.classifier.MotionMode

interface SessionRecorder {
    val currentSpeedKmh: Double
    val distanceKm: Double
    val topSpeedKmh: Double
    val verticalDropM: Int
    val motionMode: MotionMode
    val state: RecordingState

    fun start()
    fun stop(): SkiSession?
}
