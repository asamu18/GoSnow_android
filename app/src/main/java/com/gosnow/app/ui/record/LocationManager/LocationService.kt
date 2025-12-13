package com.gosnow.app.ui.record.LocationManager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat

enum class SamplingMode { Active, Idle }

interface LocationService {
    var onLocationSample: ((Location) -> Unit)?
    fun start()
    fun stop()
    fun setSamplingMode(mode: SamplingMode)
}
class SystemLocationService(
    private val context: Context
) : LocationService {

    override var onLocationSample: ((Location) -> Unit)? = null

    private val lm: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var isStarted = false

    // 轨迹记录建议用 GPS_PROVIDER（要精确定位权限）
    private val provider = LocationManager.GPS_PROVIDER

    private var currentMode: SamplingMode = SamplingMode.Active

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.w("GoSnowLoc", "onLocationChanged: lat=${location.latitude}, lon=${location.longitude}, provider=${location.provider}")
            onLocationSample?.invoke(location)
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun start() {
        if (isStarted) return
        if (!hasFineLocationPermission()) return
        isStarted = true
        requestUpdates(currentMode)
    }

    override fun stop() {
        if (!isStarted) return
        runCatching { lm.removeUpdates(listener) }
        isStarted = false
    }

    override fun setSamplingMode(mode: SamplingMode) {
        currentMode = mode
        if (!isStarted) return
        if (!hasFineLocationPermission()) return
        requestUpdates(mode)
    }

    private fun requestUpdates(mode: SamplingMode) {
        val (minTime, minDistance) = when (mode) {
            SamplingMode.Active -> 1000L to 5f      // 1s / 5m
            SamplingMode.Idle -> 5000L to 25f       // 5s / 25m
        }

        try {
            lm.removeUpdates(listener)
            lm.requestLocationUpdates(
                provider,
                minTime,
                minDistance,
                listener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
