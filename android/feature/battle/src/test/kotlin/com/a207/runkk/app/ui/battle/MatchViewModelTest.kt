@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class MatchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(MatchViewModel(w.battles, w.coordinator, w.channels, w.clock))

    @Test
    fun `거리를 골라 매칭을 요청하고 대기 시간을 센다(FR-080)`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.vm(w)
        vm.onDistanceSelected(Distance(3_000))
        vm.onStartMatching()
        runCurrent()
        assertEquals(listOf("match:3000"), w.battles.calls)
        assertEquals(MatchPhase.WAITING, vm.state.value.phase)
        assertEquals(1, w.channels.channel.connects, "MatchFound를 받으려고 채널에 연결한다")
        advanceTimeBy(7_100)
        assertEquals(7, vm.state.value.waitingSeconds)
    }

    @Test
    fun `언랭이면 거부 사유를 보이고 거리 고르기에 남는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.match = rejected("RANKED_ONLY")
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        assertEquals(MatchPhase.CHOOSING, vm.state.value.phase)
        assertEquals("유효 러닝 3회 뒤 랜덤 대결 가능, 나와의 싸움은 지금 가능", vm.state.value.error)
    }

    @Test
    fun `대기 취소 - 대기열에서 빠지면 채널을 닫고 이전 화면으로(FR-085)`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        vm.events.test {
            vm.onCancel()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertTrue("cancel" in w.battles.calls)
        assertEquals(1, w.channels.channel.closed)
    }

    /** 저장소가 가진 ViewModel: 저장소를 비우면 onCleared가 불린다(시스템 제스처로 화면이 사라진 경우). */
    private fun ownedVm(w: BattleWorld, store: ViewModelStore): MatchViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MatchViewModel(w.battles, w.coordinator, w.channels, w.clock) as T
        }
        return ViewModelProvider.create(store, factory)[MatchViewModel::class]
    }

    @Test
    fun `대기 중 화면이 사라지면 앱 범위에서 대기열 취소를 보내고 채널을 닫는다(FR-085)`() = main.runVmTest {
        val w = world()
        val store = ViewModelStore()
        val vm = ownedVm(w, store)
        vm.onStartMatching()
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(listOf("match:5000", "cancel"), w.battles.calls)
        assertEquals(1, w.channels.channel.closed)
    }

    @Test
    fun `거리 고르기에서 떠나거나 성사 뒤에 떠나면 취소를 보내지 않는다`() = main.runVmTest {
        val w = world()
        val choosing = ViewModelStore()
        ownedVm(w, choosing)
        choosing.clear()
        runCurrent()
        assertTrue(w.battles.calls.isEmpty())
        val matched = ViewModelStore()
        val vm = ownedVm(w, matched)
        vm.onStartMatching()
        runCurrent()
        w.channels.channel.push(matchFound())
        runCurrent()
        matched.clear()
        runCurrent()
        assertTrue("cancel" !in w.battles.calls, "성사 뒤 채널·대결은 조정자가 갖는다")
    }

    @Test
    fun `이미 배정돼 취소가 거부되면 대기를 이어 간다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.cancelResult = rejected("ALREADY_MATCHED")
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        vm.onCancel()
        runCurrent()
        assertEquals(MatchPhase.WAITING, vm.state.value.phase)
        assertEquals("이미 상대를 찾았어요. 곧 출발해요", vm.state.value.error)
    }

    @Test
    fun `성사 - 조정자에 넘기고 시작 예약까지 남은 초를 보인 뒤 측정이 시작되면 진행 화면으로(FR-086·FR-087)`() = main.runVmTest { vms ->
        val w = world()
        w.battles.match = Outcome.Success(MatchAcceptance(waiting = true, distance = Distance(5_000)))
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        vm.events.test {
            val match = matchFound()
            w.channels.channel.push(match)
            runCurrent()
            assertEquals(MatchPhase.MATCHED, vm.state.value.phase)
            assertEquals("바람처럼", vm.state.value.match?.opponentName)
            assertIs<BattleRunState.Matched>(w.coordinator.state.value)

            w.channels.channel.push(BattleStart(match.battleId, w.clock.now.plusSeconds(10), opponentTrack = null))
            runCurrent()
            assertEquals(10L, vm.state.value.startsInSeconds)
            w.clock.now = w.clock.now.plusSeconds(4)
            advanceTimeBy(1_100)
            assertEquals(6L, vm.state.value.startsInSeconds)

            w.clock.now = w.clock.now.plusSeconds(6)
            advanceTimeBy(10_000)
            runCurrent()
            val started = assertIs<MatchEvent.Started>(awaitItem())
            assertEquals(
                Routes.BattleRunning(9, "바람처럼", 5_000, OpponentKind.RANDOM.name, start.plusSeconds(10).toEpochMilli()),
                started.key
            )
        }
    }

    @Test
    fun `시작이 거부돼 측정 없이 끝나면 안내하고 거리 고르기로 돌아간다`() = main.runVmTest { vms ->
        val w = world()
        w.permission.granted = false
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        val match = matchFound()
        w.channels.channel.push(match)
        runCurrent()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        assertEquals(MatchPhase.CHOOSING, vm.state.value.phase)
        // 조정자 failure가 실제로 값을 내므로 일반 문구 대신 거부 사유를 안내한다.
        assertEquals(BattleErrorText.of(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT)), vm.state.value.error)
    }

    @Test
    fun `대기 중 채널 연결이 실패하면 앱을 죽이지 않고 대기열에서 빠져 거리 고르기로`() = main.runVmTest { vms ->
        val w = world()
        w.channels.channel.connectError = IllegalStateException("handshake 404")
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        assertEquals(MatchPhase.CHOOSING, vm.state.value.phase)
        assertEquals("상대를 찾는 연결이 끊겼어요. 다시 시도해 주세요", vm.state.value.error)
        assertTrue("cancel" in w.battles.calls)
    }

    @Test
    fun `대기 중 채널 연결이 Failure로 돌아와도 대기열에서 빠져 거리 고르기로`() = main.runVmTest { vms ->
        val w = world()
        w.channels.channel.connectFailure = AppError.Network
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        assertEquals(MatchPhase.CHOOSING, vm.state.value.phase)
        assertEquals("상대를 찾는 연결이 끊겼어요. 다시 시도해 주세요", vm.state.value.error)
        assertTrue("cancel" in w.battles.calls)
    }

    @Test
    fun `취소를 기다리는 사이 성사되면 취소 응답을 무시한다`() = main.runVmTest { vms ->
        val w = world()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.battles.cancelGate = gate
        val vm = vms.vm(w)
        vm.onStartMatching()
        runCurrent()
        vm.events.test {
            vm.onCancel()
            w.channels.channel.push(matchFound())
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            assertEquals(MatchPhase.MATCHED, vm.state.value.phase)
            expectNoEvents()
        }
    }
}
