package com.karnataka.senseway

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class FallDetector(private val context: Context, private val onFallDetected: () -> Unit) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var lastUpdate: Long = 0
    private var lastGForce: Float = 0f
    
    // Thresholds
    private val FALL_THRESHOLD_G = 3.5f // High G-force impact
    private val QUIET_TIME_MS = 2000 // Time to wait to confirm lack of movement (simplified)

    fun start() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val curTime = System.currentTimeMillis()
            
            // Simple G-force calculation
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            
            val gForce = sqrt((x*x + y*y + z*z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

            if (gForce > FALL_THRESHOLD_G) {
                if (curTime - lastUpdate > QUIET_TIME_MS) {
                    lastUpdate = curTime
                    // Fall detected!
                    onFallDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
