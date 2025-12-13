package com.gosnow.app.ui.record.classifier

import android.location.Location
import kotlin.math.max
import kotlin.math.min

enum class MotionMode { ACTIVE, IDLE, LIFT }

data class AutoDetectConfig(
    val windowSec: Double = 25.0,

    // —— 静止判断 ——
    val idleEnterSpeedKmh: Double = 1.2,
    val idleExitSpeedKmh: Double = 2.5,
    val idleHoldSec: Double = 12.0,

    // —— 缆车判断（上升）——
    val liftEnterMinSpeedKmh: Double = 4.0,
    val liftEnterMaxSpeedKmh: Double = 28.0,
    val liftEnterMinAltGainM: Double = 12.0,
    val liftEnterMinUpRateMps: Double = 0.30,
    val liftHoldSec: Double = 10.0,

    // 离开缆车
    val liftExitDownRateMps: Double = -0.20,
    val liftExitSpeedHighKmh: Double = 35.0,
    val liftExitSpeedLowKmh: Double = 3.0
)

private data class Sample(
    val tMs: Long,
    val speedKmh: Double,
    val altM: Double?
)

class LiftRideClassifier(
    private val cfg: AutoDetectConfig = AutoDetectConfig()
) {
    private val buf = ArrayDeque<Sample>()
    private var lastLoc: Location? = null

    private var mode: MotionMode = MotionMode.ACTIVE
    fun currentMode(): MotionMode = mode

    /**
     * altitudeOverrideM：你传气压计“相对高度”（更稳）；没传就用 GPS altitude
     */
    fun consume(loc: Location, altitudeOverrideM: Double? = null): MotionMode {
        val now = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis()

        val speedKmh = computeSpeedKmh(loc)
        val alt = altitudeOverrideM ?: loc.altitude.takeIf { it.isFinite() }

        buf.addLast(Sample(now, speedKmh, alt))
        prune(now)

        val window = getWindowStats()
        if (window == null) {
            lastLoc = loc
            return mode
        }

        val medianSpeed = window.medianSpeedKmh
        val upRate = window.upRateMps
        val downRate = window.downRateMps
        val altGain = window.altGainM
        val windowSec = window.windowSec

        val idleCandidate =
            windowSec >= cfg.idleHoldSec && medianSpeed <= cfg.idleEnterSpeedKmh

        val liftCandidate =
            windowSec >= cfg.liftHoldSec &&
                    medianSpeed in cfg.liftEnterMinSpeedKmh..cfg.liftEnterMaxSpeedKmh &&
                    altGain >= cfg.liftEnterMinAltGainM &&
                    upRate >= cfg.liftEnterMinUpRateMps

        when (mode) {
            MotionMode.ACTIVE -> {
                when {
                    liftCandidate -> mode = MotionMode.LIFT
                    idleCandidate -> mode = MotionMode.IDLE
                }
            }

            MotionMode.IDLE -> {
                when {
                    liftCandidate -> mode = MotionMode.LIFT
                    medianSpeed >= cfg.idleExitSpeedKmh -> mode = MotionMode.ACTIVE
                }
            }

            MotionMode.LIFT -> {
                val shouldExitLift =
                    downRate <= cfg.liftExitDownRateMps ||
                            medianSpeed >= cfg.liftExitSpeedHighKmh ||
                            medianSpeed <= cfg.liftExitSpeedLowKmh

                if (shouldExitLift) {
                    mode = if (medianSpeed >= cfg.idleExitSpeedKmh) MotionMode.ACTIVE else MotionMode.IDLE
                }
            }
        }

        lastLoc = loc
        return mode
    }

    private data class WindowStats(
        val windowSec: Double,
        val medianSpeedKmh: Double,
        val altGainM: Double,
        val upRateMps: Double,
        val downRateMps: Double
    )

    private fun getWindowStats(): WindowStats? {
        if (buf.size < 4) return null

        val first = buf.first()
        val last = buf.last()
        val dtSec = max(0.5, (last.tMs - first.tMs) / 1000.0)

        // median speed
        val speeds = buf.map { it.speedKmh }.sorted()
        val median = speeds[speeds.size / 2]

        // 用循环找第一个/最后一个非空高度（避免 firstNotNullOfOrNull/lastNotNullOfOrNull）
        val firstAlt: Double? = run {
            for (s in buf) {
                val a = s.altM
                if (a != null) return@run a
            }
            null
        }

        val lastAlt: Double? = run {
            var lastNonNull: Double? = null
            for (s in buf) {
                val a = s.altM
                if (a != null) lastNonNull = a
            }
            lastNonNull
        }

        if (firstAlt == null || lastAlt == null) {
            // 没有高度：只做 idle/active，不判 lift（避免误判）
            return WindowStats(
                windowSec = dtSec,
                medianSpeedKmh = median,
                altGainM = 0.0,
                upRateMps = 0.0,
                downRateMps = 0.0
            )
        }

        val altDelta: Double = lastAlt - firstAlt
        val upRate = max(0.0, altDelta / dtSec)
        val downRate = min(0.0, altDelta / dtSec)

        return WindowStats(
            windowSec = dtSec,
            medianSpeedKmh = median,
            altGainM = max(0.0, altDelta),
            upRateMps = upRate,
            downRateMps = downRate
        )
    }

    private fun prune(nowMs: Long) {
        val maxAgeMs = (cfg.windowSec * 1000.0).toLong()
        while (buf.isNotEmpty() && nowMs - buf.first().tMs > maxAgeMs) {
            buf.removeFirst()
        }
    }

    private fun computeSpeedKmh(loc: Location): Double {
        // 优先用系统 speed
        if (loc.hasSpeed()) {
            val v = loc.speed
            if (!v.isNaN() && !v.isInfinite() && v >= 0f) return (v * 3.6).toDouble()
        }

        val prev = lastLoc ?: return 0.0
        val dt = (loc.time - prev.time) / 1000.0
        if (dt <= 0) return 0.0
        val dm = prev.distanceTo(loc).toDouble()
        return (dm / 1000.0) / (dt / 3600.0)
    }
}
