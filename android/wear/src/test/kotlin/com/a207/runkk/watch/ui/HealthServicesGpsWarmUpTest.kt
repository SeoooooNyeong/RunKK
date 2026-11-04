package com.a207.runkk.watch.ui

import android.annotation.SuppressLint
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseState
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.LocationAvailability
import com.a207.runkk.watch.run.FakeExerciseClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** GPS 예열(W53): 모으는 동안만 Health Services 운동 준비를 켜고, 멈추면 끄며, 다른 운동과 다투지 않는다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HealthServicesGpsWarmUpTest {
    private val client = FakeExerciseClient()
    private var isPermitted = true
    private val warmUp = HealthServicesGpsWarmUp(client, isLocationPermitted = { isPermitted })

    private val callback: HealthServicesGpsWarmUp.WarmUpCallback
        get() = client.currentCallback as HealthServicesGpsWarmUp.WarmUpCallback

    @Test
    fun `모으면 달리기 위치만 예열하고 위치 가용성으로 찾는 중과 준비됨을 낸다`() = runTest {
        val seen = mutableListOf<GpsWarmUp.Readiness>()
        val job = backgroundScope.launch { warmUp.readiness().collect { seen += it } }
        runCurrent()

        assertEquals(listOf("callback", "prepare"), client.events)
        val config = checkNotNull(client.warmUp)
        assertEquals(ExerciseType.RUNNING, config.exerciseType)
        assertEquals(setOf(DataType.LOCATION), config.dataTypes)

        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.ACQUIRING)
        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.ACQUIRED_UNTETHERED)
        callback.onAvailabilityChanged(DataType.LOCATION, LocationAvailability.NO_GNSS)
        runCurrent()
        assertEquals(
            listOf(
                GpsWarmUp.Readiness.SEARCHING,
                GpsWarmUp.Readiness.READY,
                GpsWarmUp.Readiness.SEARCHING
            ),
            seen
        )
        job.cancel()
    }

    @Test
    fun `모으기를 멈추면 예열을 끝내고 콜백을 푼다`() = runTest {
        val job = backgroundScope.launch { warmUp.readiness().collect {} }
        runCurrent()
        client.events.clear()

        job.cancel()
        runCurrent()

        assertEquals(listOf("end", "clearCallback"), client.events)
    }

    @Test
    fun `예열이 측정 운동으로 이어졌으면 멈출 때 운동을 끝내지 않는다`() = runTest {
        val job = backgroundScope.launch { warmUp.readiness().collect {} }
        runCurrent()
        client.events.clear()

        callback.onState(ExerciseState.ACTIVE)
        job.cancel()
        runCurrent()

        assertEquals(listOf("clearCallback"), client.events)
    }

    @SuppressLint("RestrictedApi")
    @Test
    fun `운동 세션이 이미 있거나 위치 권한이 없으면 예열하지 않는다`() = runTest {
        client.trackedStatus = ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS
        backgroundScope.launch { warmUp.readiness().collect {} }
        runCurrent()
        client.trackedStatus = ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS
        backgroundScope.launch { warmUp.readiness().collect {} }
        runCurrent()
        client.trackedStatus = ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS
        isPermitted = false
        backgroundScope.launch { warmUp.readiness().collect {} }
        runCurrent()

        assertNull(client.warmUp)
        assertTrue(client.events.isEmpty())
    }

    @Test
    fun `상한 시간이 지나면 예열을 끝내고 흐름을 닫는다(배터리 NFR-048)`() = runTest {
        var isDone = false
        backgroundScope.launch {
            warmUp.readiness().collect {}
            isDone = true
        }
        runCurrent()
        client.events.clear()

        advanceTimeBy(HealthServicesGpsWarmUp.WARM_UP_LIMIT_MILLIS + 1)
        runCurrent()

        assertTrue(isDone)
        assertEquals(listOf("end", "clearCallback"), client.events)
    }
}
