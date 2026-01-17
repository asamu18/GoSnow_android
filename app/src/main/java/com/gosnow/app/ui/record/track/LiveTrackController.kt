package com.gosnow.app.ui.record.track

import android.location.Location
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

data class TrackSegment(
    val points: MutableList<Point>,
    val isFast: Boolean // false=green, true=orange
)

class LiveTrackController {
    private val segments = mutableListOf<TrackSegment>()
    private var lastAcceptedLocation: Location? = null

    // 状态机参数
    private val MIN_DISTANCE_M = 3.0f
    private val ORANGE_ON_KMH = 50.0
    private val ORANGE_OFF_KMH = 48.0

    // 状态机内部状态
    private var isFastMode = false
    private var stateChangeStartTime = 0L
    private val DWELL_ON_MS = 2000L // 2s
    private val DWELL_OFF_MS = 1000L // 1s

    fun reset() {
        segments.clear()
        lastAcceptedLocation = null
        isFastMode = false
    }

    fun addPoint(location: Location, speedKmh: Double) {
        // 1. 采样过滤
        if (lastAcceptedLocation != null) {
            if (location.distanceTo(lastAcceptedLocation!!) < MIN_DISTANCE_M) return
        }
        lastAcceptedLocation = location

        // 2. 速度状态机 (滞回)
        val now = System.currentTimeMillis()
        if (!isFastMode) {
            if (speedKmh >= ORANGE_ON_KMH) {
                if (stateChangeStartTime == 0L) stateChangeStartTime = now
                if (now - stateChangeStartTime >= DWELL_ON_MS) {
                    isFastMode = true
                    stateChangeStartTime = 0L // reset
                }
            } else {
                stateChangeStartTime = 0L
            }
        } else {
            if (speedKmh <= ORANGE_OFF_KMH) {
                if (stateChangeStartTime == 0L) stateChangeStartTime = now
                if (now - stateChangeStartTime >= DWELL_OFF_MS) {
                    isFastMode = false
                    stateChangeStartTime = 0L
                }
            } else {
                stateChangeStartTime = 0L
            }
        }

        // 3. 构造 Segment
        val newPoint = Point.fromLngLat(location.longitude, location.latitude)

        if (segments.isEmpty()) {
            segments.add(TrackSegment(mutableListOf(newPoint), isFastMode))
        } else {
            val lastSeg = segments.last()
            if (lastSeg.isFast == isFastMode) {
                // 状态相同，追加
                lastSeg.points.add(newPoint)
            } else {
                // 状态不同，新建 Segment，连接点是上一个点
                val prevPoint = lastSeg.points.last()
                segments.add(TrackSegment(mutableListOf(prevPoint, newPoint), isFastMode))
            }
        }
    }

    // 获取用于 Mapbox 更新的 GeoJSON
    fun getGeoJsonData(): Pair<FeatureCollection, FeatureCollection> {
        val greenFeatures = segments.filter { !it.isFast }.map {
            Feature.fromGeometry(LineString.fromLngLats(it.points))
        }
        val orangeFeatures = segments.filter { it.isFast }.map {
            Feature.fromGeometry(LineString.fromLngLats(it.points))
        }

        return Pair(
            FeatureCollection.fromFeatures(greenFeatures),
            FeatureCollection.fromFeatures(orangeFeatures)
        )
    }

    // 获取原始数据用于生成静态图
    fun getSegmentsSnapshot(): List<TrackSegment> = segments.toList()
}