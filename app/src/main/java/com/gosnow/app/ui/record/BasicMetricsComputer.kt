package com.gosnow.app.ui.record.storage

import android.location.Location
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 标准规范版：速度/里程/最高速/累计落差
 * - 可接入气压计高度：altitudeOverrideM（更稳的落差）
 * - 可切 “缆车/静止” 模式：liftMode=true 时不计里程、不刷新最高速，落差也更保守
 */
class BasicMetricsComputer(
    private val cfg: MetricsConfig = MetricsConfig()
) {
    // --- public state ---
    var distanceKm: Double = 0.0
        private set

    var currentSpeedKmh: Double = 0.0
        private set

    var topSpeedKmh: Double = 0.0
        private set

    var verticalDropM: Int = 0
        private set

    // --- internal ---
    private var lastLocation: Location? = null
    private var lastAltitudeM: Double? = null
    private var verticalDropAccumM: Double = 0.0

    private val speedWindow: ArrayDeque<Double> = ArrayDeque()
    private var lastSmoothSpeedKmh: Double = 0.0

    private var liftMode: Boolean = false

    fun setLiftMode(enabled: Boolean) {
        liftMode = enabled
    }

    fun reset() {
        distanceKm = 0.0
        currentSpeedKmh = 0.0
        topSpeedKmh = 0.0
        verticalDropM = 0

        lastLocation = null
        lastAltitudeM = null
        verticalDropAccumM = 0.0

        speedWindow.clear()
        lastSmoothSpeedKmh = 0.0

        liftMode = false
    }

    /**
     * @param altitudeOverrideM 传气压计“相对高度”（m），不传则用 GPS altitude
     */
    fun consumeSample(loc: Location, altitudeOverrideM: Double? = null) {
        // 0) 基本合法性
        if (loc.latitude.isNaN() || loc.longitude.isNaN()) return

        // 1) 精度过滤（水平精度太差直接丢）
        val accM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null
        if (accM != null) {
            if (accM <= 0.0) return
            if (accM > cfg.maxHorizontalAccuracyM) return
        }

        val prev = lastLocation
        val dtSec = if (prev != null) ((loc.time - prev.time) / 1000.0) else 0.0
        if (prev != null && dtSec < cfg.minDtSec) return

        // 2) 点间距离
        val dm = if (prev != null) prev.distanceTo(loc).toDouble() else 0.0

        // 3) 跳点剔除：基于 maxSpeed 推导 + hard cap
        if (prev != null && dtSec > 0.0) {
            val maxBySpeedM = (cfg.maxSpeedKmh / 3.6) * dtSec * cfg.jumpPointOvershootRatio
            val allowedM = min(maxBySpeedM, cfg.hardMaxStepDistanceM)

            // 精度越差允许距离越小（更严格）
            val accPenalty = accM?.let { (it / cfg.maxHorizontalAccuracyM).coerceAtLeast(1.0) } ?: 1.0
            val finalAllowedM = allowedM / accPenalty

            if (dm > finalAllowedM) {
                // 丢弃跳点，但更新 lastLocation 防止卡住
                lastLocation = loc
                return
            }
        }

        // 4) 速度：优先系统 speed，否则 vDelta
        val rawSpeedKmh: Double? =
            if (loc.hasSpeed() && loc.speed.isFinite() && loc.speed >= 0f) (loc.speed * 3.6).toDouble() else null

        val vDeltaKmh: Double? =
            if (prev != null && dtSec > 0.0) ((dm / 1000.0) / (dtSec / 3600.0)) else null

        val observed = (rawSpeedKmh ?: vDeltaKmh ?: 0.0).coerceIn(0.0, cfg.maxSpeedKmh)

        // 5) 平滑：median + low-pass
        val median = pushAndMedian(observed)
        val smooth = lowPass(lastSmoothSpeedKmh, median, cfg.lowPassAlpha)
        lastSmoothSpeedKmh = smooth
        currentSpeedKmh = smooth

        // 6) 最高速：缆车/静止不刷新（更符合成熟 app）
        if (!liftMode) {
            if (smooth > topSpeedKmh && smooth >= cfg.topSpeedMinCandidateKmh) {
                topSpeedKmh = smooth
            }
        }

        // 7) 落差：优先气压计高度，否则 GPS altitude
        val alt = altitudeOverrideM ?: loc.altitude.takeIf { it.isFinite() }
        if (alt != null) {
            val lastAlt = lastAltitudeM
            if (lastAlt != null) {
                val dAlt = alt - lastAlt

                // 只统计向下；抖动 < minVerticalChangeM 不算
                if (dAlt < -cfg.minVerticalChangeM) {
                    // 缆车/静止更保守：再加一道门槛
                    val gate = if (liftMode) cfg.minVerticalChangeM * 1.5 else cfg.minVerticalChangeM
                    if (dAlt < -gate) {
                        verticalDropAccumM += -dAlt
                        verticalDropM = verticalDropAccumM.roundToInt()
                    }
                }
            }
            lastAltitudeM = alt
        }

        // 8) 里程：缆车/静止不计；低速不计
        if (!liftMode && prev != null && dtSec > 0.0 && smooth >= cfg.minSpeedForDistanceKmh) {
            val stepKm = dm / 1000.0

            // 用 smooth 推导一个上限，防止偶发速度异常导致距离暴增
            val maxBySmoothKm = (smooth / 3600.0) * dtSec * cfg.clampOvershootRatio
            distanceKm += min(stepKm, maxBySmoothKm)
        }

        lastLocation = loc
    }

    private fun pushAndMedian(v: Double): Double {
        val w = max(3, cfg.medianWindow).let { if (it % 2 == 0) it + 1 else it }
        speedWindow.addLast(v)
        while (speedWindow.size > w) speedWindow.removeFirst()
        val sorted = speedWindow.sorted()
        return sorted[sorted.size / 2]
    }

    private fun lowPass(prev: Double, current: Double, alpha: Double): Double {
        return alpha * prev + (1.0 - alpha) * current
    }
}
