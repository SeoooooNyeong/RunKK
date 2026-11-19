@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotRunnerLook
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.battle.BattleRunState
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
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
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
    fun `S15P31A207-278 진행 화면 키를 앱 범위에 적어 두고 회가 닫히면 돌려주지 않는다`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        assertEquals(key, w.screens.key(), "열린 회의 진행 화면 키")
        vm.onForfeitLongPressed()
        vm.onForfeitConfirmed()
        runCurrent()
        assertNull(w.screens.key(), "닫힌 회의 키는 돌려주지 않는다")
    }

    @Test
    fun `측정을 시작하지 못하고 끝나면 실패 화면을 보인다`() = main.runVmTest { vms ->
        val w = world()
        w.permission.granted = false
        val match = matchFound()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
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
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
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
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
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
    fun `기록 대결 카운트다운은 화면이 늦게 붙어도 3부터 보이고 남은 시간이 정수 초를 지날 때 바로 줄어든다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        w.coordinator.onRecordBattleStart(
            recordStart(Distance(5_000), com.a207.runkk.app.core.model.CardKind.SELF_RUN, "과거의 나")
        )
        runCurrent()
        // 시작 응답 뒤 진행 화면이 0.15초 뒤에 붙었다(남은 2.85초)
        w.clock.now = w.clock.now.plusMillis(150)
        advanceTimeBy(150)
        vm.bind(Routes.BattleRunning(42, "과거의 나", 5_000, OpponentKind.SELF.name))
        runCurrent()
        assertEquals(3L, vm.state.value.countdown)
        // 남은 2.001초: 아직 3
        w.clock.now = w.clock.now.plusMillis(849)
        advanceTimeBy(849)
        runCurrent()
        assertEquals(3L, vm.state.value.countdown)
        // 남은 2초가 되는 순간 2(1초 주기를 기다리지 않는다)
        w.clock.now = w.clock.now.plusMillis(1)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2L, vm.state.value.countdown)
        w.clock.now = w.clock.now.plusMillis(1_000)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1L, vm.state.value.countdown)
        w.clock.now = w.clock.now.plusMillis(1_000)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
    }

    @Test
    fun `배치고사 판이면 진행 중 상대를 물음표로 그리고 아니면 상대 그림을 그린다(FR-276)`() = main.runVmTest { vms ->
        val w = world()
        val placement = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        placement.bind(key.copy(placementRound = 2))
        runCurrent()
        assertEquals(2, placement.state.value.placementRound)
        assertTrue(placement.state.value.isOpponentHidden)
        assertEquals("바람처럼", placement.state.value.opponentName, "표시명은 서버의 가상 닉네임 그대로다")

        val plain = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        plain.bind(key)
        runCurrent()
        assertNull(plain.state.value.placementRound)
        assertFalse(plain.state.value.isOpponentHidden)
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
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
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
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        vm.bind(key.copy(startAtEpochMillis = start.toEpochMilli()))
        runCurrent()
        assertEquals(Duration.ofSeconds(20), vm.state.value.elapsed)
    }

    /** 셀프런을 측정 중까지 진행한 진행 화면 ViewModel(과거의 나 10월 2일 기록 27:30). */
    private fun TestScope.selfRunning(
        w: BattleWorld,
        vms: ViewModelCloser,
        kind: CardKind = CardKind.SELF_RUN
    ): BattleRunningViewModel {
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))
        w.coordinator.onRecordBattleStart(recordStart(Distance(5_000), kind, "10월 2일의 나"))
        val opponent = if (kind == CardKind.SELF_RUN) OpponentKind.SELF else OpponentKind.CELEBRITY
        vm.bind(
            Routes.BattleRunning(
                42,
                "10월 2일의 나",
                5_000,
                opponent.name,
                opponentRecordSeconds = 1_650,
                opponentRecordedOnEpochDay = java.time.LocalDate.of(2026, 10, 2).toEpochDay()
            )
        )
        runCurrent()
        return vm
    }

    @Test
    fun `상대 모습이 상태에 실린다`() = main.runVmTest { vms ->
        val w = world()
        val look = SlotRunnerLook(isFemale = true, topItemId = 12, shoesItemId = 31, bodyVariant = 1, runAmount = 42)
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))

        vm.bind(key.copy(opponentGender = "FEMALE", opponentLook = look))
        runCurrent()

        assertEquals(
            RunnerLook(Gender.FEMALE, 12, null, 31, bodyVariant = 1, runAmount = 42, faceKey = null),
            vm.state.value.opponentLook
        )
        assertEquals(look, vm.state.value.opponentLook?.toSlot(), "칸에 넘길 때 같은 값으로 돌아간다")
    }

    @Test
    fun `모습을 받지 못하면 상태에 모습이 없다(성별 기본 모습으로 그린다)`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))

        vm.bind(key.copy(opponentGender = "MALE"))
        runCurrent()

        assertEquals(null, vm.state.value.opponentLook)
        assertEquals(Gender.MALE, vm.state.value.opponentGender)
    }

    @Test
    fun `배치고사는 물음표를 유지한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))

        vm.bind(key.copy(placementRound = 2, opponentLook = SlotRunnerLook(isFemale = false, topItemId = 12)))
        runCurrent()

        assertEquals(true, vm.state.value.isOpponentHidden)
        assertEquals(null, vm.state.value.opponentLook, "배치고사 상대는 모습이 와도 그리지 않는다")
    }

    @Test
    fun `내 아바타(배경 키와 얼굴)를 홈과 같은 본인 요약에서 읽는다`() = main.runVmTest { vms ->
        val w = world()
        w.accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "GOLD", 3, "default:FOX", "SEOUL_NAMSAN", 12))
        val vm = vms.track(BattleRunningViewModel(w.coordinator, w.clock, w.screens, w.accounts))

        vm.bind(key)
        runCurrent()

        assertEquals("SEOUL_NAMSAN", vm.state.value.myAvatar?.backgroundKey)
        assertEquals("default:FOX", vm.state.value.myAvatar?.faceKey)
    }

    @Test
    fun `D-264 카운트다운에 상대 기록과 날짜를 담는다`() = main.runVmTest { vms ->
        val w = world()
        val vm = selfRunning(w, vms)
        val s = vm.state.value
        assertEquals(BattlePhase.COUNTDOWN, s.phase)
        assertEquals(Duration.ofSeconds(1_650), s.opponentRecord)
        assertEquals(java.time.LocalDate.of(2026, 10, 2), s.opponentRecordedOn)
    }

    @Test
    fun `TC-9638 셀프런 카운트다운 다시 고르기 - cancel만 보내고 기록 없이 기록런 탭으로(D119)`() = main.runVmTest { vms ->
        val w = world()
        val vm = selfRunning(w, vms)
        vm.events.test {
            vm.onReselect()
            runCurrent()
            assertEquals(BattleRunningEvent.Discarded, awaitItem())
        }
        assertTrue("cancel:42" in w.battles.calls)
        assertFalse(w.battles.calls.any { it.startsWith("forfeit") }, "다시 고르기는 그만두기(패배)가 아니다")
        assertTrue(w.pendingRuns.runs.value.isEmpty(), "기록을 저장하지 않는다")
        assertEquals(BattleRunState.Idle, w.coordinator.state.value, "다음 대결을 위해 비운다")
    }

    @Test
    fun `AN10-1 셀럽런 카운트다운 뒤로가기는 취소 확인이고 확인하면 cancel만 보내 기록 없이 기록런 탭으로(D-337)`() = main.runVmTest { vms ->
        val w = world()
        val vm = selfRunning(w, vms, CardKind.CELEB_RUN)
        assertEquals(BattlePhase.COUNTDOWN, vm.state.value.phase)
        vm.events.test {
            vm.onForfeitLongPressed()
            assertTrue(vm.state.value.isConfirmingCancel)
            assertFalse(vm.state.value.isConfirmingForfeit, "출발 전 기록 대결은 그만두기(패) 확인이 아니다")
            vm.onCancelConfirmed()
            runCurrent()
            assertEquals(BattleRunningEvent.Discarded, awaitItem())
        }
        assertEquals(1, w.battles.calls.count { it == "cancel:42" })
        assertFalse(w.battles.calls.any { it.startsWith("forfeit") })
        assertTrue(w.pendingRuns.runs.value.isEmpty(), "기록을 저장하지 않는다")
    }

    @Test
    fun `TC-9636 D119 셀프런 진행 중 그만두기를 확인하면 패배로 판정하고 결과 화면으로(Finished)`() = main.runVmTest { vms ->
        val w = world()
        val vm = selfRunning(w, vms)
        w.clock.now = w.clock.now.plusSeconds(3)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
        vm.events.test {
            vm.onForfeitLongPressed()
            assertTrue(vm.state.value.isConfirmingForfeit)
            vm.onForfeitConfirmed()
            runCurrent()
            assertEquals(BattleRunningEvent.Finished(42), awaitItem())
        }
        assertTrue("forfeit:42" in w.battles.calls)
    }

    @Test
    fun `D119 셀프런 진행 중 취소를 확인하면 결과 화면 없이 기록런 탭으로(Discarded)`() = main.runVmTest { vms ->
        val w = world()
        val vm = selfRunning(w, vms)
        w.clock.now = w.clock.now.plusSeconds(3)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(BattlePhase.RUNNING, vm.state.value.phase)
        vm.events.test {
            vm.onCancelTapped()
            assertTrue(vm.state.value.isConfirmingCancel)
            vm.onCancelConfirmed()
            runCurrent()
            assertEquals(BattleRunningEvent.Discarded, awaitItem())
        }
        assertTrue("cancel:42" in w.battles.calls)
        assertTrue(w.pendingRuns.runs.value.isEmpty(), "셀프런 취소는 기록을 올리지 않는다")
    }

    @Test
    fun `TC-9637 그만두기 문구는 상대 종류마다 다르고 취소는 계속 달리기다`() {
        assertEquals(R.string.battle_running_forfeit_title, OpponentKind.RANDOM.forfeitTitle)
        assertEquals(R.string.battle_running_forfeit_title, OpponentKind.FRIEND.forfeitTitle)
        assertEquals(R.string.battle_running_forfeit_message, OpponentKind.FRIEND.forfeitMessage)
        assertEquals(R.string.battle_running_forfeit_confirm, OpponentKind.RANDOM.forfeitConfirm)
        assertEquals(R.string.battle_forfeit_title_self, OpponentKind.SELF.forfeitTitle)
        assertEquals(R.string.battle_forfeit_message_self, OpponentKind.SELF.forfeitMessage)
        assertEquals(R.string.battle_forfeit_title_celeb, OpponentKind.CELEBRITY.forfeitTitle)
        assertEquals(R.string.battle_forfeit_message_celeb, OpponentKind.CELEBRITY.forfeitMessage)
        assertEquals(R.string.battle_forfeit_confirm_quit, OpponentKind.CELEBRITY.forfeitConfirm)
    }

    @Test
    fun `랜덤 대결은 다시 고르기가 없다`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        vm.onReselect()
        runCurrent()
        assertFalse("forfeit:9" in w.battles.calls)
    }

    @Test
    fun `D-261 진행 상태에 내 경로선을 담는다`() = main.runVmTest { vms ->
        val w = world()
        val vm = running(w, vms)
        w.runTo(1, 0.0)
        w.runTo(15, 45.0)
        runCurrent()
        assertEquals(w.tracker.routeLine.value, vm.state.value.route)
        assertTrue(vm.state.value.route.points.isNotEmpty())
    }

    @Test
    fun `TC-9646 거리 차 표기는 +42 m 앞서는 중, -118 m 뒤처지는 중이다`() {
        assertEquals("+42 m", gapMetersLabel(42))
        assertEquals("-118 m", gapMetersLabel(-118))
        assertEquals("+0 m", gapMetersLabel(0))
    }

    private companion object {
        const val METERS_PER_DEGREE = 6_371_000.0 * Math.PI / 180.0
    }
}
