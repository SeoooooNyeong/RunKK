package com.a207.runkk.app.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import com.a207.runkk.app.domain.run.InertialMotion
import com.a207.runkk.app.domain.run.InertialMotionDetector
import kotlin.math.abs
import kotlin.math.sqrt

/** 측정 서비스에서만 가속도와 자이로를 읽는다. 두 센서 시각은 같은 부팅 기준이다. */
internal class PhoneInertialMotion(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val detector = InertialMotionDetector()
    private var isMeasuring = false
    private var hasCheckedStepSensor = false

    fun start() {
        val sensors = manager ?: return
        val acceleration = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return
        if (!sensors.registerListener(this, acceleration, SAMPLE_PERIOD_MICROS)) return
        isMeasuring = true
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensors.registerListener(this, it, SAMPLE_PERIOD_MICROS)
        }
        ensureStepsStarted()
    }

    fun ensureStepsStarted() {
        val sensors = manager ?: return
        if (!isMeasuring || hasCheckedStepSensor) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        hasCheckedStepSensor = true
        sensors.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        manager?.unregisterListener(this)
        isMeasuring = false
        hasCheckedStepSensor = false
    }

    fun forLocation(elapsedRealtimeNanos: Long): InertialMotion {
        val now = SystemClock.elapsedRealtime()
        val fixAge = now - elapsedRealtimeNanos / NANOS_PER_MILLI
        val motion = detector.stateAt(now)
        if (motion == InertialMotion.STILL || motion == InertialMotion.WARMING_UP) return motion
        return if (fixAge in 0..MAX_FIX_AGE_MS) motion else InertialMotion.UNKNOWN
    }

    override fun onSensorChanged(event: SensorEvent) {
        val at = event.timestamp / NANOS_PER_MILLI
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            detector.onStep(at)
            return
        }
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            detector.onRotation(at, magnitude)
            return
        }
        // 선형 가속도 센서가 없는 폰은 방향에 무관한 크기에서 중력의 크기를 뺀다.
        val linear = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            abs(magnitude - SensorManager.GRAVITY_EARTH)
        } else {
            magnitude
        }
        detector.onSample(at, linear)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private companion object {
        const val SAMPLE_PERIOD_MICROS = 50_000
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_FIX_AGE_MS = 1_500L
    }
}
