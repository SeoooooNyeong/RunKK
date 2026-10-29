@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.LiveState
import com.a207.runkk.app.domain.live.OpponentProgress
import com.a207.runkk.app.domain.run.LocationSample
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BattleRunningViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val key = Routes.BattleRunning(9, "바람처럼", 5_000, OpponentKind.RANDOM.name)

    /** 랜덤 대결을 측정 중까지 진행한 진행 화면 ViewModel. */
    private fun TestScope.running(w: BattleWorld, vms: ViewModelCloser): BattleRunningViewModel {
        val match = matchFound()
        w.coordinator.onMatched(match)
        runCurrent()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        vm.bind(key)
        runCurrent()
        return vm
    }

    private fun progress(
        gap: Int,
        theirs: Long,
        connection: OpponentProgress.Connection = OpponentProgress.Connection.CONNECTED
    ) = OpponentProgress(
        battleId = 9,
        opponentName = "바람처럼",
        opponentDistance = Distance(theirs),
        opponentElapsed = Duration.ofSeconds(600),
        gapMeters = gap,
        connection = connection
    )

    /** 측정에 위치 표본과 1초 시계를 넣는다(시작 [start] 기준 [second]초, 북쪽으로 [meters]m). */
    private fun BattleWorld.runTo(second: Long, meters: Double) {
        val at = start.plusSeconds(second)
        clock.now = at
        tracker.onLocationSample(LocationSample(at, GeoPoint(37.0 + meters / METERS_PER_DEGREE, 127.0), 5.0))
        tracker.tick(at)
    }

    @Test
    fun `내 거리·경과·페이스는 측정 진행값에서, 격차·상대 거리는 상대 진행값에서 온다(FR-088)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
        w.runTo(1, 0.0)
        w.runTo(15, 45.0)
        runCurrent()
        w.channels.channel.push(progress(gap = 42, theirs = 2_800))
        runCurrent()
        val s = vm.state.value
        assertEquals(42, s.gapMeters)
        assertEquals(Distance(2_800), s.opponentDistance)
        assertEquals(w.tracker.progress.value.distance, s.myDistance, "격차로 거꾸로 셈하지 않는다")
        assertEquals(Duration.ofSeconds(15), s.elapsed, "시작 시각이 아니라 측정 경과")
        assertEquals(w.tracker.progress.value.averagePace, s.averagePace)
    }

    @Test
    fun `상대 연결이 끊기면 연결 확인 중을 보인다(FR-088)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        w.channels.channel.push(progress(gap = 5, theirs = 100))
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_running_connected), vm.state.value.connectionLabel)
        w.channels.channel.push(progress(gap = 5, theirs = 100, connection = OpponentProgress.Connection.CHECKING))
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_running_checking), vm.state.value.connectionLabel)
    }

    @Test
    fun `QA-5 실시간 연결이 끊기면 연결 끊김을 보이고 다시 연결되면 지운다`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        assertFalse(vm.state.value.isLiveLost)
        w.channels.channel.state.value = LiveState.CONNECTING
        runCurrent()
        assertTrue(vm.state.value.isLiveLost)
        w.channels.channel.state.value = LiveState.POLLING
        runCurrent()
        assertFalse(vm.state.value.isLiveLost, "폴링으로 이어지면 메시지는 오간다")
        w.channels.channel.state.value = LiveState.CLOSED
        runCurrent()
        assertTrue(vm.state.value.isLiveLost)
        w.channels.channel.state.value = LiveState.CONNECTED
        runCurrent()
        assertFalse(vm.state.value.isLiveLost)
    }

    @Test
    fun `추월 안내는 바뀐 때만 보이고 횟수를 센다(FR-188)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        w.channels.channel.push(BattleGuidance(9, BattleGuidance.Kind.OVERTOOK, 15))
        runCurrent()
        assertEquals(BattleGuidance.Kind.OVERTOOK, vm.state.value.guidance)
        assertEquals(1, vm.state.value.overtakes)
        w.channels.channel.push(progress(gap = 20, theirs = 3_000))
        runCurrent()
        assertEquals(1, vm.state.value.overtakes, "같은 안내를 다시 세지 않는다")
        advanceTimeBy(5_100)
        assertNull(vm.state.value.guidance, "안내는 잠깐 보였다 사라진다")
    }

    @Test
    fun `상대가 먼저 도착하면 종료 카운트다운을 보이고 끝나면 결과로(FR-107)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        vm.events.test {
            w.channels.channel.push(CountdownStarted(9, "바람처럼", w.clock.now.plusSeconds(10)))
            runCurrent()
            assertEquals(BattlePhase.CLOSING, vm.state.value.phase)
            assertEquals("바람처럼", vm.state.value.arrivedName)
            assertEquals(10L, vm.state.value.closingSeconds)
            w.clock.now = w.clock.now.plusSeconds(3)
            advanceTimeBy(1_100)
            assertEquals(10L, vm.state.value.closingLimitSeconds, "알리는 제한 시간은 처음 값으로 고정")
            w.channels.channel.push(BattleEnded(9, BattleEnded.Status.FINISHED))
            runCurrent()
            assertEquals(BattleRunningEvent.Finished(9), awaitItem())
        }
        assertEquals(BattlePhase.FINISHED, vm.state.value.phase)
    }

    @Test
    fun `그만두기 - 짧게 누르면 안내만, 길게 누르면 확인, 확인하면 forfeit(FR-094, D-49)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        vm.onForfeitTapped()
        assertEquals(UiText.Res(R.string.battle_running_forfeit_hold), vm.state.value.hint)
        assertFalse(vm.state.value.isConfirmingForfeit)
        vm.onForfeitLongPressed()
        assertTrue(vm.state.value.isConfirmingForfeit)
        vm.onForfeitDismissed()
        assertFalse(w.battles.calls.any { it.startsWith("forfeit") }, "취소하면 보내지 않는다")
        vm.onForfeitLongPressed()
        vm.events.test {
            vm.onForfeitConfirmed()
            runCurrent()
            assertEquals(BattleRunningEvent.Finished(9), awaitItem())
        }
        assertTrue("forfeit:9" in w.battles.calls)
    }

    @Test
    fun `측정을 시작하지 못하고 끝나면 실패 화면을 보인다`() = main.runVmTest { vms ->
        val w = world()
        w.permission.granted = false
        val match = matchFound()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        vm.bind(key)
        w.coordinator.onMatched(match)
        runCurrent()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        assertEquals(BattlePhase.FAILED, vm.state.value.phase)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `채널 연결이 실패하면 조정자 failure를 안내로 보이고 닫을 때 지운다`() = main.runVmTest { vms ->
        val w = world()
        w.channels.channel.connectFailure = AppError.Network
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        vm.bind(key)
        w.coordinator.onMatched(matchFound())
        runCurrent()
        assertEquals(BattleErrorText.of(AppError.Network), vm.state.value.error)
        vm.onFailureClosed()
        runCurrent()
        assertNull(w.coordinator.failure.value)
    }

    @Test
    fun `기록 대결 - 3초 카운트다운을 보인 뒤 측정으로`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        w.coordinator.onRecordBattleStart(
            recordStart(Distance(5_000), com.a207.runkk.app.core.model.CardKind.SELF_RUN, "과거의 나")
        )
        vm.bind(Routes.BattleRunning(42, "과거의 나", 5_000, OpponentKind.SELF.name))
        runCurrent()
        assertEquals(BattlePhase.COUNTDOWN, vm.state.value.phase)
        assertEquals(3L, vm.state.value.countdown)
        w.clock.now = w.clock.now.plusSeconds(3)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
        assertEquals(OpponentKind.SELF, vm.state.value.kind)
    }

    @Test
    fun `종료 카운트다운 중 진행값이 와도 CLOSING을 유지한다(FR-107)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        w.channels.channel.push(CountdownStarted(9, "바람처럼", w.clock.now.plusSeconds(10)))
        runCurrent()
        w.channels.channel.push(progress(gap = -30, theirs = 5_000))
        runCurrent()
        assertEquals(BattlePhase.CLOSING, vm.state.value.phase)
    }

    @Test
    fun `조정자가 이 대결을 모르면 잠시 뒤 실패 화면으로 빠져나갈 수 있다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        vm.bind(key)
        runCurrent()
        assertEquals(BattlePhase.STARTING, vm.state.value.phase)
        advanceTimeBy(5_100)
        assertEquals(BattlePhase.FAILED, vm.state.value.phase)
    }

    @Test
    fun `화면을 다시 열어도 경과는 측정 진행값을 따른다`() = main.runVmTest { vms ->
        val w = world()
        val match = matchFound()
        w.coordinator.onMatched(match)
        runCurrent()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        w.runTo(1, 0.0)
        w.runTo(20, 60.0)
        runCurrent()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock))
        vm.bind(key.copy(startAtEpochMillis = start.toEpochMilli()))
        runCurrent()
        assertEquals(Duration.ofSeconds(20), vm.state.value.elapsed)
    }

    private companion object {
        const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0
    }
}
