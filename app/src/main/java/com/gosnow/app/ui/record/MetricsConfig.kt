package com.gosnow.app.ui.record.storage

data class MetricsConfig(
    // 精度过滤
    val maxHorizontalAccuracyM: Double = 30.0,
    val minDtSec: Double = 0.2,

    // 速度边界
    val maxSpeedKmh: Double = 150.0,

    // 平滑
    val medianWindow: Int = 5,
    val lowPassAlpha: Double = 0.80,

    // 距离上限 clamp
    val clampOvershootRatio: Double = 1.5,

    // 里程累加阈值
    val minSpeedForDistanceKmh: Double = 2.5,

    // ✅ 核心修改 2：新增最小位移阈值
    // 如果两点之间距离小于 2 米，视为误差，不计入里程
    val minDistanceStepM: Double = 2.0,

    // 落差抖动阈值
    val minVerticalChangeM: Double = 2.0,

    // 跳点剔除
    val jumpPointOvershootRatio: Double = 2.0,
    val hardMaxStepDistanceM: Double = 200.0,

    // 最高速候选阈值
    val topSpeedMinCandidateKmh: Double = 10.0
)
