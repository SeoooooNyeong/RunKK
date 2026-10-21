@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.live.CrewRunCancelled
import com.a207.runkk.app.domain.live.CrewRunEnded
import com.a207.runkk.app.domain.live.CrewRunRelay
import com.a207.runkk.app.domain.live.CrewRunStart
import com.a207.runkk.app.ui.crew.CrewWorld.Companion.crewWorld
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

class CrewRunViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun TestScope.lobbyVm(w: CrewWorld, vms: ViewModelCloser): CrewRunViewModel {
        val vm = vms.track(CrewRunViewModel(w.crews, w.coordinator, w.clock))
        vm.bind(Routes.CrewRunLobby(31))
        runCurrent()
        return vm
    }

    /** "나"는 본인 줄(me)이다. */
    private fun relay(vararg rows: Triple<String, Long, Int?>) = CrewRunRelay(
        31,
        rows.map { (name, meters, rank) ->
            CrewRunRelay.Entry(name, Distance(meters), rank, CrewRunRelay.Connection.CONNECTED, me = name == "나")
        }
    )

    @Test
    fun `대기실에 들어가면 조정자 채널을 열고 출발까지 남은 시간을 센다(FR-147)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        assertEquals(CrewRunPhase.LOBBY, vm.state.value.phase)
        assertEquals(3, vm.state.value.lobby?.participants?.size)
        assertIs<CrewRunState.Lobby>(w.coordinator.state.value)
        assertEquals(Duration.ofMinutes(10), vm.state.value.untilStart)
        w.clock.now = w.clock.now.plusSeconds(60)
        advanceTimeBy(1_100)
        assertEquals(Duration.ofMinutes(9), vm.state.value.untilStart)
    }

    @Test
    fun `채널 연결이 실패하면 조정자 failure를 안내로 보인다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.channels.channel.connectFailure = AppError.Network
        val vm = lobbyVm(w, vms)
        assertEquals(CrewErrorText.of(AppError.Network), vm.state.value.error)
    }

    @Test
    fun `준비 완료를 누르면 켜고 다시 누르면 푼다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        vm.onReadyToggle()
        assertTrue(vm.state.value.ready)
        assertTrue(vm.state.value.lobby!!.participants.first { it.me }.ready)
        vm.onReadyToggle()
        assertFalse(vm.state.value.ready)
    }

    @Test
    fun `크루런 거리는 대기실 응답에서 읽고 준비 여부는 대기실의 내 줄(me)을 따른다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyState = lobby.copy(
            distance = Distance(3_000),
            participants = lobby.participants.map { if (it.me) it.copy(ready = true) else it }
        )
        val vm = lobbyVm(w, vms)
        assertEquals(Distance(3_000), vm.state.value.distance)
        assertTrue(vm.state.value.ready)
        assertIs<CrewRunState.Lobby>(w.coordinator.state.value)
    }

    @Test
    fun `출발 신호 → 카운트다운 → 중계 순위 → 종료면 결과로(FR-148·FR-149·FR-191)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        vm.events.test {
            w.channels.channel.push(CrewRunStart(31, w.clock.now, listOf("바람처럼", "나")))
            runCurrent()
            assertEquals(CrewRunPhase.COUNTDOWN, vm.state.value.phase)
            assertEquals(3L, vm.state.value.countdown)
            w.clock.now = w.clock.now.plusSeconds(3)
            advanceTimeBy(3_100)
            w.channels.channel.push(relay(Triple("나", 820, 2), Triple("바람처럼", 900, 1), Triple("달빛", 300, null)))
            runCurrent()
            assertEquals(CrewRunPhase.RUNNING, vm.state.value.phase)
            assertEquals(listOf("바람처럼", "나", "달빛"), vm.state.value.relay.map { it.name })
            assertEquals(listOf(false, true, false), vm.state.value.relay.map { it.me }, "내 줄을 강조한다")
            w.channels.channel.push(CrewRunEnded(31))
            runCurrent()
            assertEquals(CrewRunEvent.Finished(31), awaitItem())
        }
    }

    @Test
    fun `취소되면 사유를 보인다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        w.channels.channel.push(CrewRunCancelled(31, "준비 완료 2명 미만"))
        runCurrent()
        assertEquals(CrewRunPhase.CANCELLED, vm.state.value.phase)
        assertEquals("준비 완료 2명 미만", vm.state.value.cancelledReason)
    }

    @Test
    fun `그만두기는 길게 누른 뒤 확인해야 quit을 보낸다(FR-192, D-49)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        w.channels.channel.push(CrewRunStart(31, w.clock.now, listOf("나")))
        runCurrent()
        w.clock.now = w.clock.now.plusSeconds(3)
        advanceTimeBy(3_100)
        w.channels.channel.push(relay(Triple("나", 400, 1)))
        runCurrent()
        vm.onQuitTapped()
        assertEquals("길게 눌러 그만두기", vm.state.value.hint)
        vm.onQuitLongPressed()
        assertTrue(vm.state.value.confirmingQuit)
        assertFalse(w.crews.calls.any { it.startsWith("quit") })
        vm.onQuitConfirmed()
        runCurrent()
        assertTrue("quit:31" in w.crews.calls)
        assertEquals(CrewRunPhase.FINISHED, vm.state.value.phase)
    }

    @Test
    fun `대기실 나가기가 실패하면 머물고 사유를 보인다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.fail["leaveLobby"] = com.a207.runkk.app.core.model.AppError.Network
        val vm = lobbyVm(w, vms)
        vm.events.test {
            vm.onLeaveLobby()
            runCurrent()
            expectNoEvents()
        }
        assertFalse(vm.state.value.leaving)
        assertNotNull(vm.state.value.error)
        assertEquals(0, w.channels.channel.closed, "나가지 못했으면 중계 채널을 그대로 둔다")
    }

    @Test
    fun `대기실을 나가면 leaveLobby 뒤 이전 화면으로`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        vm.events.test {
            vm.onLeaveLobby()
            assertEquals(CrewRunEvent.Left, awaitItem())
        }
        assertTrue("leaveLobby:31" in w.crews.calls)
        assertEquals(1, w.channels.channel.closed, "대기실만 나가도 조정자가 연 채널을 닫는다")
        assertEquals(CrewRunState.Idle, w.coordinator.state.value, "결과 화면으로 가지 않는다")
    }

    @Test
    fun `결과 - 완주 인원·순위·첫 크루런·크루 거리 반영(FR-191)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = vms.track(CrewRunResultViewModel(w.crews, w.coordinator))
        vm.load(31)
        runCurrent()
        assertEquals(2, vm.state.value.finishedCount)
        assertTrue(vm.state.value.result!!.firstCrewRun)
        assertEquals("연결 확인 중", connectionLabel("CHECKING"))
        assertEquals("그만둠", connectionLabel("QUIT"))
    }
}
