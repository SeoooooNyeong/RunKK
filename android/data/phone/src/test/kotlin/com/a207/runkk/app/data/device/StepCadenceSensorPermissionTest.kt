package com.a207.runkk.app.data.device

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.a207.runkk.app.domain.run.MotionSample
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSensor

/**
 * MR !91 AN-4: 측정을 시작할 때 신체 활동 권한이 없어 걸음 센서를 못 열었어도, 측정 중 권한을 허용하면 그 뒤 [StepCadenceSensor.ensureStarted]
 * (측정 서비스가 1초 시계마다 부른다)가 센서를 연다.
 */
@RunWith(RobolectricTestRunner::class)
class StepCadenceSensorPermissionTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `측정 중 권한을 허용하면 다음 시계 때 걸음 센서를 연다`() {
        val manager = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowOf(manager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val sensor = StepCadenceSensor(app)
        val onMotion: (MotionSample) -> Unit = {}

        assertFalse(sensor.ensureStarted(onMotion), "권한이 없으면 열지 않는다")
        assertFalse(sensor.isListening)

        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        assertTrue(sensor.ensureStarted(onMotion), "권한이 생기면 연다")
        assertTrue(sensor.isListening)
        assertTrue(sensor.ensureStarted(onMotion), "이미 열었으면 그대로")
        sensor.stop()
        assertFalse(sensor.isListening)
    }
}
