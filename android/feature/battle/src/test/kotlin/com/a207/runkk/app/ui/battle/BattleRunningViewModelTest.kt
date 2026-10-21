@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleGuidance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.CountdownStarted
import com.a207.runkk.app.domain.live.OpponentProgress
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

    private fun progress(gap: Int, theirs: Long) = OpponentProgress(
        battleId = 9,
        opponentName = "바람처럼",
        opponentDistance = Distance(theirs),
        opponentElapsed = Duration.ofSeconds(600),
        gapMeters = gap,
        connection = OpponentProgress.Connection.CONNECTED
    )

    @Test
    fun `상대 진행값으로 격차·상대 거리·내 거리를 보인다(FR-088)`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
        w.channels.channel.push(progress(gap = 42, theirs = 2_800))
        runCurrent()
        val s = vm.state.value
        assertEquals(42, s.gapMeters)
        assertEquals(Distance(2_800), s.opponentDistance)
        assertEquals(Distance(2_842), s.myDistance)
    }

    @Test
    fun `경과 시간은 1초마다 늘어난다`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        w.clock.now = w.clock.now.plusSeconds(15)
        advanceTimeBy(1_100)
        assertEquals(Duration.ofSeconds(15), vm.state.value.elapsed)
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
        assertEquals("길게 눌러 그만두기", vm.state.value.hint)
        assertFalse(vm.state.value.confirmingForfeit)
        vm.onForfeitLongPressed()
        assertTrue(vm.state.value.confirmingForfeit)
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
}
