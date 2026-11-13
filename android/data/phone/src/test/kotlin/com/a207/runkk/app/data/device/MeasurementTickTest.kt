package com.a207.runkk.app.data.device

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

/**
 * MR !91 확인 리뷰 CR2-T1: 측정 서비스의 1초 시계([measurementTick])가 매번 걸음 센서를 다시 시도하므로, 시작 때 신체 활동 권한을 거부했어도
 * 측정 중 허용하면 다음 시계에 센서를 구독한다(AN-4 배선).
 */
@RunWith(RobolectricTestRunner::class)
class MeasurementTickTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `권한 거부로 시작해도 측정 중 허용하면 다음 1초 시계에 걸음 센서를 구독한다`() {
        val manager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val steps = StepCadenceSensor(app)
        val ticks = mutableListOf<Instant>()
        val t0 = Instant.parse("2026-11-08T07:00:00Z")

        steps.start {}
        measurementTick(steps, onMotion = {}, tick = { ticks += it }, now = t0)
        assertFalse(steps.isListening, "권한이 없으면 구독하지 않고 측정만 이어 간다")

        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        measurementTick(steps, onMotion = {}, tick = { ticks += it }, now = t0.plusSeconds(1))

        assertTrue(steps.isListening)
        assertEquals(1, shadowOf(manager).getListeners().size, "한 번만 구독한다")
        measurementTick(steps, onMotion = {}, tick = { ticks += it }, now = t0.plusSeconds(2))
        assertEquals(1, shadowOf(manager).getListeners().size)
        assertEquals(listOf(t0, t0.plusSeconds(1), t0.plusSeconds(2)), ticks)
        steps.stop()
    }
}
