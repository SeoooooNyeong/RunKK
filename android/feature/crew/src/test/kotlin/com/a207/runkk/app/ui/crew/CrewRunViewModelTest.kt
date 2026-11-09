@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.crew

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.ServerClock
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MyAvatar
import com.a207.runkk.app.domain.crew.CrewRunState
import com.a207.runkk.app.domain.crew.CrewRunStatus
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

    /** 결과 화면 ViewModel. 다시 읽기 상한은 시험 시계(가상 시간)로 센다. */
    private fun TestScope.resultVm(w: CrewWorld): CrewRunResultViewModel =
        CrewRunResultViewModel(w.crews, w.coordinator, timeSource = testScheduler.timeSource) {
            Outcome.Failure(AppError.Network)
        }

    private fun TestScope.lobbyVm(
        w: CrewWorld,
        vms: ViewModelCloser,
        serverClock: ServerClock = ServerClock(w.clock)
    ): CrewRunViewModel {
        val vm = vms.track(CrewRunViewModel(w.crews, w.coordinator, w.clock, serverClock))
        vm.bind(Routes.CrewRunLobby(31))
        runCurrent()
        return vm
    }

    /** "나"는 본인 줄(me)이다. 줄 순서는 서버가 준 대기실 입장 순이다. */
    private fun relay(vararg rows: Triple<String, Long, CrewRunRelay.State>) = CrewRunRelay(
        31,
        Distance(rows.sumOf { it.second }),
        rows.map { (name, meters, state) -> CrewRunRelay.Entry(name, Distance(meters), state, isMe = name == "나") }
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
    fun `출발까지 남은 시간은 기기 시계가 아니라 서버 시각으로 센다`() = main.runVmTest { vms ->
        val w = crewWorld()
        // 기기 시계가 서버보다 2분 빠르다: 모집 일시(기기 기준 10분 뒤)까지 서버로는 12분 남았다.
        val serverClock = ServerClock(w.clock).apply { onServerDate(w.clock.now.minusSeconds(120)) }
        val vm = lobbyVm(w, vms, serverClock)
        assertEquals(Duration.ofMinutes(12), vm.state.value.untilStart)
        w.clock.now = w.clock.now.plusSeconds(60)
        advanceTimeBy(1_100)
        assertEquals(Duration.ofMinutes(11), vm.state.value.untilStart)
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
        assertTrue(vm.state.value.isReady)
        assertTrue(vm.state.value.lobby!!.participants.first { it.isMe }.isReady)
        vm.onReadyToggle()
        assertFalse(vm.state.value.isReady)
    }

    @Test
    fun `크루런 거리는 대기실 응답에서 읽고 준비 여부는 대기실의 내 줄(me)을 따른다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.lobbyState = lobby.copy(
            distance = Distance(3_000),
            participants = lobby.participants.map { if (it.isMe) it.copy(isReady = true) else it }
        )
        val vm = lobbyVm(w, vms)
        assertEquals(Distance(3_000), vm.state.value.distance)
        assertTrue(vm.state.value.isReady)
        assertIs<CrewRunState.Lobby>(w.coordinator.state.value)
    }

    @Test
    fun `출발 신호 → 카운트다운 → 합산 중계(입장 순·함께 달리는 크루원 수) → 종료면 결과로`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        vm.events.test {
            w.channels.channel.push(CrewRunStart(31, w.clock.now, listOf("바람처럼", "나")))
            runCurrent()
            assertEquals(CrewRunPhase.COUNTDOWN, vm.state.value.phase)
            assertEquals(3L, vm.state.value.countdown)
            w.clock.now = w.clock.now.plusSeconds(3)
            advanceTimeBy(3_100)
            w.channels.channel.push(
                relay(
                    Triple("나", 820, CrewRunRelay.State.RUNNING),
                    Triple("바람처럼", 900, CrewRunRelay.State.PAUSED),
                    Triple("달빛", 300, CrewRunRelay.State.ENDED)
                )
            )
            runCurrent()
            assertEquals(CrewRunPhase.RUNNING, vm.state.value.phase)
            assertEquals(listOf("나", "바람처럼", "달빛"), vm.state.value.relay.map { it.name }, "서버 순서 그대로")
            assertEquals(listOf(true, false, false), vm.state.value.relay.map { it.isMe })
            assertEquals(Distance(2_020), vm.state.value.totalDistance)
            assertEquals(1, vm.state.value.runningMates, "끝낸 달빛은 빼고 멈춘 바람처럼은 센다")
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
        // 취소 화면(F-14c)도 대기실 틀이다: 마지막 대기실(참가자·준비 상태)을 그대로 두고 남은 시간은 0:00이다.
        assertEquals(3, vm.state.value.lobby?.participants?.size)
        assertEquals(Duration.ZERO, vm.state.value.untilStart)
        w.clock.now = w.clock.now.plusSeconds(5)
        advanceTimeBy(5_000)
        assertEquals(Duration.ZERO, vm.state.value.untilStart, "취소된 뒤에는 다시 세지 않는다")
    }

    @Test
    fun `그만두기는 길게 누른 뒤 확인해야 quit을 보낸다(FR-192, D-49)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        w.channels.channel.push(CrewRunStart(31, w.clock.now, listOf("나")))
        runCurrent()
        w.clock.now = w.clock.now.plusSeconds(3)
        advanceTimeBy(3_100)
        w.channels.channel.push(relay(Triple("나", 400, CrewRunRelay.State.RUNNING)))
        runCurrent()
        vm.onQuitTapped()
        assertEquals(UiText.Res(R.string.crew_run_quit_hint), vm.state.value.hint)
        vm.onQuitLongPressed()
        assertTrue(vm.state.value.isConfirmingQuit)
        assertFalse(w.crews.calls.any { it.startsWith("quit") })
        vm.onQuitConfirmed()
        runCurrent()
        assertTrue(w.crews.calls.any { it.startsWith("quit:31") })
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
        assertFalse(vm.state.value.isLeaving)
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
    fun `대기실이 아직 안 열렸으면 다시 시도 대신 돌아가기를 주고, 뒤로 가면 나가기 요청 없이 닫는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.fail["enter"] = AppError.Rejected("LOBBY_NOT_OPEN")
        val vm = lobbyVm(w, vms)
        assertEquals(CrewRunPhase.ENTERING, vm.state.value.phase)
        assertEquals(UiText.Res(R.string.crew_error_lobby_not_open), vm.state.value.error)
        assertFalse(vm.state.value.canRetry, "다시 해도 같은 거부라 다시 시도를 주지 않는다")
        vm.events.test {
            vm.onBack()
            assertEquals(CrewRunEvent.Left, awaitItem())
        }
        assertFalse(w.crews.calls.any { it.startsWith("leaveLobby") }, "들어가지 못한 대기실은 나가기를 보내지 않는다")
    }

    @Test
    fun `네트워크 오류로 못 들어갔으면 다시 시도를 주고 뒤로 가기도 된다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.fail["enter"] = AppError.Network
        val vm = lobbyVm(w, vms)
        assertTrue(vm.state.value.canRetry)
        vm.events.test {
            vm.onBack()
            assertEquals(CrewRunEvent.Left, awaitItem())
        }
    }

    @Test
    fun `뒤로 가기 - 대기실에서는 나가기를 보낸 뒤 닫는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        vm.events.test {
            vm.onBack()
            assertEquals(CrewRunEvent.Left, awaitItem())
        }
        assertTrue("leaveLobby:31" in w.crews.calls)
    }

    @Test
    fun `뒤로 가기 - 출발 카운트다운부터는 나가지 않고 그만두기 안내만 한다`() = main.runVmTest { vms ->
        val w = crewWorld()
        val vm = lobbyVm(w, vms)
        w.channels.channel.push(CrewRunStart(31, w.clock.now, listOf("나")))
        runCurrent()
        vm.events.test {
            vm.onBack()
            expectNoEvents()
        }
        assertEquals(UiText.Res(R.string.crew_run_quit_hint), vm.state.value.hint)
        assertFalse(w.crews.calls.any { it.startsWith("leaveLobby") })
    }

    @Test
    fun `결과 - 크루 합산 거리·참가자·첫 크루런을 보이고 내 기록 상세를 한 번 읽는다(F-16·F-16b)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val read = mutableListOf<String>()
        val vm = vms.track(
            CrewRunResultViewModel(w.crews, w.coordinator, timeSource = testScheduler.timeSource) { id ->
                read += id
                Outcome.Failure(AppError.Network)
            }
        )
        vm.load(31)
        runCurrent()
        val result = vm.state.value.result!!
        assertEquals(Distance(23_440), result.totalDistance)
        assertEquals(5, result.participants.size)
        assertTrue(result.isFirstCrewRun)
        assertEquals(listOf("rec-31"), read)
        assertEquals(listOf("result:31"), w.crews.calls.filter { it.startsWith("result") }, "끝났고 내 기록이 있으면 다시 읽지 않는다")
    }

    @Test
    fun `결과 - 가운데 내 아바타는 홈과 같은 출처(본인 요약의 얼굴·배경)로 한 번 읽는다(F-16)`() = main.runVmTest { vms ->
        val w = crewWorld()
        val look = MyAvatar("face-7", emptyList(), "bg-park")
        var reads = 0
        val vm = vms.track(
            CrewRunResultViewModel(
                w.crews,
                w.coordinator,
                timeSource = testScheduler.timeSource,
                myAvatar = {
                    reads++
                    look
                }
            ) { Outcome.Failure(AppError.Network) }
        )
        vm.load(31)
        runCurrent()
        assertEquals(look, vm.state.value.myAvatar)
        vm.load(31, isForced = true)
        runCurrent()
        assertEquals(1, reads, "다시 읽어도 내 아바타는 한 번만 읽는다")
    }

    @Test
    fun `결과 - 아직 달리는 크루원이 있으면 3초 뒤 다시 읽는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING, myRecordId = null)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        runCurrent()
        w.crews.resultState = crewRunResult
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(2, w.crews.calls.count { it.startsWith("result") })
        assertEquals(CrewRunStatus.FINISHED, vm.state.value.result?.status)
    }

    @Test
    fun `결과 - 크루원이 30초 넘게 달려도 간격을 늘려 가며 크루런이 끝날 때까지 다시 읽는다(MR !91 AN-3)`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        fun reads() = w.crews.calls.count { it.startsWith("result") }
        vm.load(31)
        runCurrent()
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS * CrewRunResultViewModel.REREADS + 1)
        runCurrent()
        assertEquals(1 + CrewRunResultViewModel.REREADS, reads(), "처음 30초는 3초 간격")

        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(1 + CrewRunResultViewModel.REREADS, reads(), "30초 뒤에는 간격이 3초보다 길다")
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        val afterTenMinutes = reads()
        assertTrue(afterTenMinutes > 1 + CrewRunResultViewModel.REREADS, "30초 뒤에도 계속 읽는다")
        assertTrue(afterTenMinutes < 1 + CrewRunResultViewModel.REREADS + 10 * 60 / 3, "간격을 늘려 덜 읽는다")

        w.crews.resultState = crewRunResult
        advanceTimeBy(CrewRunResultViewModel.MAX_REREAD_MILLIS + 1)
        runCurrent()
        val ended = reads()
        assertEquals(CrewRunStatus.FINISHED, vm.state.value.result?.status)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(ended, reads(), "모두 끝나 크루런이 닫히면 멈춘다")
    }

    @Test
    fun `결과 - 화면이 가려지면 다시 읽기를 멈추고 돌아오면 바로 읽은 뒤 이어 간다(MR !91 CR2-A1)`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        fun reads() = w.crews.calls.count { it.startsWith("result") }
        vm.load(31)
        runCurrent()
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(2, reads())

        vm.onStop()
        advanceTimeBy(30 * 60_000L)
        runCurrent()
        assertEquals(2, reads(), "가려진 동안은 읽지 않는다")

        vm.onStart()
        runCurrent()
        assertEquals(3, reads(), "돌아오면 바로 읽는다")
        advanceTimeBy(CrewRunResultViewModel.REREAD_MILLIS + 1)
        runCurrent()
        assertEquals(4, reads(), "간격 규칙을 잇는다")
        vm.onStart()
        runCurrent()
        assertEquals(4, reads(), "이미 읽는 중이면 시작을 거듭하지 않는다")
    }

    @Test
    fun `결과 - 3시간 상한은 가려진 시간까지 실제 경과로 센다(MR !91 CR2-A1)`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        fun reads() = w.crews.calls.count { it.startsWith("result") }
        vm.load(31)
        runCurrent()
        vm.onStop()
        advanceTimeBy(CrewRunResultViewModel.RUNNING_WATCH_MILLIS + 1)
        vm.onStart()
        runCurrent()
        val afterReturn = reads()
        assertEquals(2, afterReturn, "돌아와 한 번 읽는다")
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(afterReturn, reads(), "상한을 넘었으니 더 읽지 않는다")
    }

    @Test
    fun `결과 - 크루런이 끝났고 내 기록만 아직이면 3초 간격 10번까지만 다시 읽는다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(myRecordId = null)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(1 + CrewRunResultViewModel.REREADS, w.crews.calls.count { it.startsWith("result") })
    }

    @Test
    fun `결과 - 크루런이 아무리 길어도 다시 읽기는 상한 시간에서 멈춘다`() = main.runVmTest { vms ->
        val w = crewWorld()
        w.crews.resultState = crewRunResult.copy(status = CrewRunStatus.RUNNING)
        val vm = vms.track(resultVm(w))
        vm.load(31)
        advanceTimeBy(CrewRunResultViewModel.RUNNING_WATCH_MILLIS + CrewRunResultViewModel.MAX_REREAD_MILLIS)
        runCurrent()
        val stopped = w.crews.calls.count { it.startsWith("result") }
        advanceTimeBy(60 * 60_000L)
        runCurrent()
        assertEquals(stopped, w.crews.calls.count { it.startsWith("result") })
    }
}
