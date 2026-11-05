package com.a207.runkk.app.data.device

import android.location.Location
import com.a207.runkk.app.core.model.GpsReadiness
import com.a207.runkk.app.domain.run.LocationPermission
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** FusedGpsWarmUp(W55): 위치 권한이 없으면 위치를 켜지 않고 끝나며, 정확도 기준 안의 위치만 준비됨이다. */
@RunWith(RobolectricTestRunner::class)
class FusedGpsWarmUpTest {
    @Test
    fun `위치 권한이 없으면 아무것도 내지 않고 위치 서비스를 부르지 않는다`() = runTest {
        var isClientRequested = false
        val warmUp =
            FusedGpsWarmUp(
                permission = object : LocationPermission {
                    override fun granted(): Boolean = false
                },
                client = {
                    isClientRequested = true
                    error("권한이 없으면 위치 서비스를 부르지 않는다")
                }
            )
        assertEquals(emptyList(), warmUp.readiness(30.0).toList())
        assertFalse(isClientRequested)
    }

    @Test
    fun `정확도가 기준 이하면 준비됨, 넘거나 없으면 찾는 중`() {
        assertEquals(GpsReadiness.READY, FusedGpsWarmUp.readinessOf(location(accuracy = 12f), 30.0))
        assertEquals(GpsReadiness.READY, FusedGpsWarmUp.readinessOf(location(accuracy = 30f), 30.0))
        assertEquals(GpsReadiness.SEARCHING, FusedGpsWarmUp.readinessOf(location(accuracy = 45f), 30.0))
        assertEquals(GpsReadiness.SEARCHING, FusedGpsWarmUp.readinessOf(location(accuracy = null), 30.0))
    }

    private fun location(accuracy: Float?): Location = Location("fused").apply {
        latitude = 37.5
        longitude = 127.0
        accuracy?.let { this.accuracy = it }
    }
}
