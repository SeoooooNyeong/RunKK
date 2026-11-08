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
import androidx.core.content.ContextCompat
import com.a207.runkk.app.domain.run.CadenceMeter
import com.a207.runkk.app.domain.run.MotionSample
import java.time.Instant

/**
 * 폰 걸음 센서(TYPE_STEP_COUNTER)로 케이던스를 잰다(S15P31A207-242). 업무 규칙은 domain CadenceMeter가 갖고, 이 클래스는 센서 값을
 * 넘기기만 한다. Android 10부터 걸음 센서는 신체 활동 권한(ACTIVITY_RECOGNITION)이 있어야 값을 준다. 권한이 없거나 센서가 없으면
 * 아무것도 하지 않는다(케이던스 없이 측정하고 서버가 비운다). 측정 서비스가 측정마다 [start]·[stop]한다.
 */
internal class StepCadenceSensor(private val context: Context) {
    private val sensors: SensorManager? = context.getSystemService(SensorManager::class.java)
    private var listener: SensorEventListener? = null

    /** 센서를 구독한다. 권한·센서가 없으면 거짓이고 아무것도 하지 않는다. */
    fun start(onMotion: (MotionSample) -> Unit): Boolean {
        if (listener != null) return true
        val manager = sensors ?: return false
        if (!isPermitted(context)) return false
        val stepCounter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return false
        val meter = CadenceMeter()
        val fresh = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val at = eventInstant(event.timestamp, SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis())
                meter.accept(at, event.values[0].toLong())?.let { onMotion(MotionSample(at, cadenceSpm = it)) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(fresh, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)) return false
        listener = fresh
        return true
    }

    fun stop() {
        listener?.let { sensors?.unregisterListener(it) }
        listener = null
    }

    companion object {
        private const val NANOS_PER_MILLI = 1_000_000L

        /** 걸음 센서 권한. Android 10(API 29) 전에는 권한 없이 쓴다. */
        fun isPermitted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

        /** 센서 시각(부팅 뒤 경과 나노초)을 벽시계로 바꾼다. */
        internal fun eventInstant(eventNanos: Long, nowElapsedNanos: Long, nowEpochMillis: Long): Instant =
            Instant.ofEpochMilli(nowEpochMillis - (nowElapsedNanos - eventNanos) / NANOS_PER_MILLI)
    }
}
