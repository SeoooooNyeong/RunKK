package com.a207.runkk.watch.heart

import androidx.health.services.client.data.ExerciseTrackedStatus
import com.a207.runkk.watch.testing.FakeExerciseSession
import com.a207.runkk.watch.testing.ok
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * 운동 세션 수명(13.2 워치, NFR-023): 프로세스가 죽어 남은 이 앱의 세션은 앱 시작·수집 시작 때 끝내고,
 * 다른 앱의 세션은 끝내지도 덮어쓰지도 않으며, 세션은 한 번에 하나만 연다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseHeartRateSensorTest {
    @Test
    fun `앱 시작 때 이 앱이 열고 남긴 세션을 끝낸다`() = runTest {
        val session = FakeExerciseSession(owner = ExerciseOwner.THIS_APP)

        val ended = ExerciseHeartRateSensor(session).endLeftoverSession()

        assertTrue(ended)
        assertEquals(listOf("end:THIS_APP"), session.events)
        assertEquals(ExerciseOwner.NONE, session.owner)
    }

    @Test
    fun `다른 앱의 세션은 앱 시작 때 끝내지 않는다`() = runTest {
        val session = FakeExerciseSession(owner = ExerciseOwner.OTHER_APP)

        val ended = ExerciseHeartRateSensor(session).endLeftoverSession()

        assertFalse(ended)
        assertEquals(emptyList(), session.events)
        assertEquals(ExerciseOwner.OTHER_APP, session.owner)
    }

    @Test
    fun `남은 세션이 없으면 아무것도 하지 않는다`() = runTest {
        val session = FakeExerciseSession()

        assertFalse(ExerciseHeartRateSensor(session).endLeftoverSession())
        assertEquals(emptyList(), session.events)
    }

    @Test
    fun `이 프로세스가 재는 중이면 정리 요청이 지금 세션을 끝내지 않는다`() = runTest {
        val session = FakeExerciseSession()
        val sensor = ExerciseHeartRateSensor(session)
        val received = mutableListOf<WatchHeartRate>()
        backgroundScope.launch { sensor.heartRates().collect { received += it } }
        runCurrent()

        val ended = sensor.endLeftoverSession()
        session.emit(ok(1_000, 140))
        runCurrent()

        assertFalse(ended)
        assertEquals(listOf("open"), session.events)
        assertEquals(listOf(ok(1_000, 140)), received)
    }

    @Test
    fun `수집을 시작하기 전에 남은 세션을 먼저 끝내고 연다`() = runTest {
        val session = FakeExerciseSession(owner = ExerciseOwner.THIS_APP)
        val sensor = ExerciseHeartRateSensor(session)

        backgroundScope.launch { sensor.heartRates().collect {} }
        runCurrent()

        assertEquals(listOf("end:THIS_APP", "open"), session.events)
    }

    @Test
    fun `다른 앱이 세션을 쓰는 중이면 열지 않고 예외로 끝난다`() = runTest {
        val session = FakeExerciseSession(owner = ExerciseOwner.OTHER_APP)

        val failure = runCatching { ExerciseHeartRateSensor(session).heartRates().collect {} }.exceptionOrNull()

        assertIs<ExerciseInUseException>(failure)
        assertEquals(0, session.opened)
        assertEquals(ExerciseOwner.OTHER_APP, session.owner)
    }

    @Test
    fun `앞 세션이 끝나기 전에는 다음 세션을 열지 않는다`() = runTest {
        val session = FakeExerciseSession()
        val sensor = ExerciseHeartRateSensor(session)
        val first = backgroundScope.launch { sensor.heartRates().collect {} }
        runCurrent()
        backgroundScope.launch { sensor.heartRates().collect {} }
        runCurrent()
        assertEquals(1, session.opened)

        first.cancel()
        runCurrent()

        assertEquals(2, session.opened)
        assertEquals(1, session.maxActive)
        assertEquals(listOf("open", "close", "open"), session.events)
    }

    @Test
    fun `수집이 끝난 뒤의 정리 요청은 다시 확인한다`() = runTest {
        val session = FakeExerciseSession()
        val sensor = ExerciseHeartRateSensor(session)
        val collecting = backgroundScope.launch { sensor.heartRates().collect {} }
        runCurrent()
        collecting.cancel()
        runCurrent()
        session.owner = ExerciseOwner.THIS_APP

        assertTrue(sensor.endLeftoverSession())
    }

    @Test
    fun `ExerciseTrackedStatus를 세션 주인으로 옮긴다`() {
        assertEquals(ExerciseOwner.THIS_APP, exerciseOwnerOf(ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS))
        assertEquals(ExerciseOwner.OTHER_APP, exerciseOwnerOf(ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS))
        assertEquals(ExerciseOwner.NONE, exerciseOwnerOf(ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS))
        assertEquals(ExerciseOwner.NONE, exerciseOwnerOf(ExerciseTrackedStatus.UNKNOWN))
    }
}
