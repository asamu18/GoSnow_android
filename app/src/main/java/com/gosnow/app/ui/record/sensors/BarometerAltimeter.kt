package com.gosnow.app.ui.record.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow

class BarometerAltimeter(
    context: Context,
    private val smoothAlpha: Float = 0.85f
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var p0: Float? = null
    private var _altitudeM: Float? = null

    val altitudeM: Float?
        get() = _altitudeM

    fun isAvailable(): Boolean = pressureSensor != null

    fun start() {
        p0 = null
        _altitudeM = null
        pressureSensor?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_PRESSURE) return
        val p = event.values.firstOrNull() ?: return
        if (p <= 0f) return

        val base = p0 ?: run {
            p0 = p
            p
        }

        val rawAlt = 44330f * (1f - (p / base).pow(0.1903f))
        val prev = _altitudeM

        _altitudeM = if (prev == null) rawAlt else smoothAlpha * prev + (1f - smoothAlpha) * rawAlt
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
