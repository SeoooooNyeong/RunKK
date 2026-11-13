package com.a207.runkk.watch.run

import android.location.Location
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.domain.run.InertialMotion
import com.a207.runkk.app.domain.run.LocationSample
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchGnssSpeedTest {
    private val speeds = WatchGnssSpeed()
    private val sample = LocationSample(Instant.EPOCH, GeoPoint(37.0, 127.0), 10.0)

    @Test
    fun `속도는 가까운 측정 시각에만 붙이고 IMU 판정은 보존한다`() {
        speeds.onLocationChanged(fix(1_000, 0.1f, 0.2f))

        val moving = sample.copy(inertialMotion = InertialMotion.MOVING)
        assertEquals(
            moving.copy(speedMps = 0.1f.toDouble(), speedAccuracyMps = 0.2f.toDouble()),
            speeds.applyTo(moving, 1_100)
        )
        assertEquals(moving, speeds.applyTo(moving, 2_000))
    }

    @Test
    fun `위치 오차가 20m여도 속도 이력이 없으면 정지로 덮지 않는다`() {
        val walking = sample.copy(accuracyMeters = 20.0, inertialMotion = InertialMotion.MOVING)
        assertEquals(walking, speeds.applyTo(walking, 1_000))
    }

    private fun fix(at: Long, speed: Float, accuracy: Float) = Location("gps").apply {
        elapsedRealtimeNanos = at * 1_000_000
        this.speed = speed
        speedAccuracyMetersPerSecond = accuracy
    }
}
