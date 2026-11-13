package com.a207.runkk.watch.run

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.a207.runkk.app.domain.run.InertialMotion
import com.a207.runkk.app.domain.run.InertialMotionDetector
import com.a207.runkk.app.domain.run.InertialMotionHistory
import kotlin.math.abs
import kotlin.math.sqrt

/** 묶여 도착한 운동 위치에도 GPS 측정 시각의 센서 판정을 사용한다. */
internal class WatchInertialMotion(private val sensors: SensorManager?) : SensorEventListener {
    private var detector = InertialMotionDetector(isGaitTrackingDuringRotationEnabled = false)
    private val history = InertialMotionHistory()

    fun start() {
        stop()
        val manager = sensors ?: return
        val acceleration = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        val rotation = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        if (!manager.registerListener(this, rotation, SAMPLE_PERIOD_MICROS) ||
            !manager.registerListener(this, acceleration, SAMPLE_PERIOD_MICROS)
        ) {
            stop()
        }
    }

    @Synchronized
    fun stop() {
        sensors?.unregisterListener(this)
        detector = InertialMotionDetector(isGaitTrackingDuringRotationEnabled = false)
        history.clear()
    }

    fun stateAt(at: Long, now: Long): InertialMotion = history.stateAt(at, now)

    @Synchronized
    internal fun onRotation(at: Long, angularSpeed: Double) {
        if (angularSpeed >= WRIST_HANDLING_RPS) detector.onRotation(at, angularSpeed)
    }

    @Synchronized
    internal fun onAcceleration(at: Long, linearAcceleration: Double) {
        detector.onSample(at, linearAcceleration)
        history.record(at, detector.stateAt(at))
    }

    override fun onSensorChanged(event: SensorEvent) {
        val at = event.timestamp / NANOS_PER_MILLI
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> onRotation(at, magnitude)
            Sensor.TYPE_ACCELEROMETER -> onAcceleration(at, abs(magnitude - SensorManager.GRAVITY_EARTH))
            Sensor.TYPE_LINEAR_ACCELERATION -> onAcceleration(at, magnitude)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val SAMPLE_PERIOD_MICROS = 50_000

        // shortcut: 손목의 빠른 회전 기준이며 워치 보행·흔들기 실측으로 조정한다.
        const val WRIST_HANDLING_RPS = 2.0
    }
}
