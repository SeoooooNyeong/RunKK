@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.ActiveBattle
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.live.BattleEnded
import com.a207.runkk.app.domain.live.BattleStart
import com.a207.runkk.app.domain.live.FriendInviteDeclined
import com.a207.runkk.app.domain.live.FriendInviteExpired
import com.a207.runkk.app.domain.live.MatchFailed
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 매칭 대기·초대 대기·참가 확인·성사(D-269, TC-9661~TC-9683). 조정자와 RunTracker는 진짜를 쓴다(BattleWorld). */
class MatchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeConsentSettings()
    private val accounts = FakeAccounts()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(MatchViewModel(w.battles, w.coordinator, w.channels, w.clock, consentOf(settings, accounts), accounts))

    private fun TestScope.quick(w: BattleWorld, vms: ViewModelCloser, distance: Long = 5_000): MatchViewModel =
        vms.vm(w).also {
            it.startQuick(Distance(distance))
            runCurrent()
        }

    // ---------- 랜덤 매칭 ----------

    @Test
    fun `랜덤 - 채널을 연결한 뒤 요청하고 대기 시간을 센다(FR-080)`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms, 3_000)
        assertEquals(listOf("match:3000"), w.battles.calls)
        assertEquals(MatchPhase.WAITING, vm.state.value.phase)
        assertEquals(1, w.channels.channel.connects, "MatchFound를 받으려고 채널에 연결한다")
        advanceTimeBy(7_100)
        assertEquals(7, vm.state.value.waitingSeconds)
    }

    @Test
    fun `랜덤 - 언랭 거부는 사유 안내 뒤 확인하면 배틀런 화면으로`() = main.runVmTest { vms ->
        val w = world()
        w.battles.match = rejected("RANKED_ONLY")
        val vm = vms.vm(w)
        vm.events.test {
            vm.startQuick(Distance(5_000))
            runCurrent()
            assertEquals(UiText.Res(R.string.battle_error_ranked_only), vm.state.value.notice)
            vm.onNoticeConfirmed()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
    }

    @Test
    fun `랜덤 - 열린 친구 초대가 있으면 409 ALREADY_IN_BATTLE로 진행 중인 대결 안내`() = main.runVmTest { vms ->
        val w = world()
        w.battles.match = rejected("ALREADY_IN_BATTLE")
        val vm = quick(w, vms)
        assertEquals(UiText.Res(R.string.battle_invite_busy), vm.state.value.notice)
    }

    @Test
    fun `랜덤 - 이미 대기열에 있으면 다시 요청하지 않고 대기 화면을 잇는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.status = Outcome.Success(MatchAcceptance(isWaiting = true, distance = Distance(3_000)))
        val vm = quick(w, vms)
        assertEquals(MatchPhase.WAITING, vm.state.value.phase)
        assertEquals(Distance(3_000), vm.state.value.distance)
        assertTrue(w.battles.calls.none { it.startsWith("match:") })
    }

    @Test
    fun `랜덤 - 랜덤 대결 거리가 아니면 요청하지 않고 안내한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms, 7_000)
        assertTrue(w.battles.calls.isEmpty())
        assertEquals(UiText.Res(R.string.battle_error_distance_invalid), vm.state.value.notice)
    }

    @Test
    fun `대기 취소 - 대기열에서 빠지면 채널을 닫고 이전 화면으로(FR-085)`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        vm.events.test {
            vm.onCancel()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertTrue("cancel" in w.battles.calls)
        assertEquals(1, w.channels.channel.closed)
    }

    @Test
    fun `대기 중 화면이 사라지면 앱 범위에서 대기열 취소를 보내고 채널을 닫는다(FR-085)`() = main.runVmTest {
        val w = world()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, factory(w))[MatchViewModel::class]
        vm.startQuick(Distance(5_000))
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue("cancel" in w.battles.calls)
        assertEquals(1, w.channels.channel.closed)
    }

    @Test
    fun `이미 배정돼 취소가 거부되면 대기를 이어 간다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.cancelResult = rejected("ALREADY_MATCHED")
        val vm = quick(w, vms)
        vm.onCancel()
        runCurrent()
        assertEquals(MatchPhase.WAITING, vm.state.value.phase)
        assertEquals(UiText.Res(R.string.battle_error_already_matched), vm.state.value.error)
    }

    @Test
    fun `취소를 기다리는 사이 성사되면 취소 응답을 무시한다`() = main.runVmTest { vms ->
        val w = world()
        val gate = CompletableDeferred<Unit>()
        w.battles.cancelGate = gate
        val vm = quick(w, vms)
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

    @Test
    fun `성사 - 조정자에 넘기고 시작 예약까지 남은 초를 보인 뒤 측정이 시작되면 진행 화면으로(FR-086·FR-087)`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
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
    fun `TC-9663 성사 화면의 내 티어는 내 프로필 티어이고 못 읽으면 비운다(R2-10)`() = main.runVmTest { vms ->
        val vm = quick(world(), vms)
        assertEquals("GOLD", vm.state.value.myTier)
        accounts.summaryResult = Outcome.Failure(AppError.Network)
        val failed = quick(world(), vms)
        assertNull(failed.state.value.myTier)
    }

    @Test
    fun `성사와 출발 예약이 잇따라 와도 출발 예약을 놓치지 않는다`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        val match = matchFound()
        w.channels.channel.push(match)
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now.plusSeconds(10), opponentTrack = null))
        runCurrent()
        assertIs<BattleRunState.Countdown>(w.coordinator.state.value)
        assertEquals(10L, vm.state.value.startsInSeconds)
    }

    @Test
    fun `측정이 시작되면 대기용 임대를 반납하고 조정자 임대는 남긴다(7차 AN7-1)`() = main.runVmTest { vms ->
        val w = world()
        quick(w, vms)
        val match = matchFound()
        w.channels.channel.push(match)
        runCurrent()
        assertEquals(0, w.channels.channel.closed, "출발 전에는 대기 임대를 쥐고 있어야 출발 예약을 받는다")
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        assertIs<BattleRunState.Running>(w.coordinator.state.value)
        assertEquals(1, w.channels.channel.closed, "대기 임대만 반납한다")
    }

    @Test
    fun `QA-2 성사 뒤 30초가 지나도 측정이 시작되지 않으면 그만두기를 보이고 그만두면 이전 화면으로`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        w.channels.channel.push(matchFound())
        runCurrent()
        advanceTimeBy(29_000)
        assertFalse(vm.state.value.canForfeit)
        advanceTimeBy(1_100)
        assertTrue(vm.state.value.canForfeit)
        vm.events.test {
            vm.onForfeit()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertTrue("forfeit:9" in w.battles.calls)
        assertNull(vm.state.value.notice)
        assertEquals(BattleRunState.Idle, w.coordinator.state.value, "새 대결을 시작할 수 있다")
    }

    @Test
    fun `TC-9772 출발 시각 15초 뒤까지 측정이 시작되지 않으면 그만두기를 보인다(14·9초 없음, 15초 보임)`() = main.runVmTest { vms ->
        // 조정자를 따로 멈춘 스케줄러에 두어 출발 시각이 지나도 측정이 시작되지 않는 기기를 흉내 낸다.
        val stalled = TestCoroutineScheduler()
        val w = BattleWorld(CoroutineScope(StandardTestDispatcher(stalled) + Job(backgroundScope.coroutineContext.job)))
        fun settle() {
            repeat(3) {
                runCurrent()
                stalled.runCurrent()
            }
        }
        val vm = vms.vm(w)
        vm.startQuick(Distance(5_000))
        settle()
        val match = matchFound()
        w.channels.channel.push(match)
        settle()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now.plusSeconds(10), opponentTrack = null))
        settle()
        assertIs<BattleRunState.Countdown>(w.coordinator.state.value)
        advanceTimeBy(24_900)
        runCurrent()
        assertFalse(vm.state.value.canForfeit, "출발 시각 + 14.9초")
        advanceTimeBy(100)
        runCurrent()
        assertTrue(vm.state.value.canForfeit, "출발 시각 + 15초")
    }

    @Test
    fun `출발 전에 상대가 그만두면 그 사실을 안내한다(대결 취소)`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        val match = matchFound()
        w.channels.channel.push(match)
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now.plusSeconds(10), opponentTrack = null))
        runCurrent()
        w.channels.channel.push(BattleEnded(match.battleId, BattleEnded.Status.OPPONENT_FORFEITED))
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_match_opponent_left), vm.state.value.notice)
    }

    @Test
    fun `시작이 거부돼 측정 없이 끝나면 그 사유를 안내한다`() = main.runVmTest { vms ->
        val w = world()
        w.permission.granted = false
        val vm = quick(w, vms)
        val match = matchFound()
        w.channels.channel.push(match)
        runCurrent()
        w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
        runCurrent()
        assertEquals(BattleErrorText.of(AppError.Rejected(AppError.Rejected.LOCATION_CONSENT)), vm.state.value.notice)
    }

    @Test
    fun `M-4 대기 채널이 결과 없이 끝나면 앱을 죽이지 않고 대기열에서 빠진다`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        w.channels.channel.endMessages()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_match_connection_lost), vm.state.value.notice)
        assertTrue("cancel" in w.battles.calls)
    }

    @Test
    fun `채널 연결이 실패하면 요청을 보내지 않고 안내한다, 인증 실패면 로그인 안내`() = main.runVmTest { vms ->
        val w = world()
        w.channels.channel.connectFailure = AppError.Unauthorized
        val vm = quick(w, vms)
        assertTrue(w.battles.calls.none { it.startsWith("match:") }, "연결 전에 요청하지 않는다")
        assertEquals(BattleErrorText.of(AppError.Unauthorized), vm.state.value.notice)
    }

    @Test
    fun `매칭 실패(MatchFailed)를 받으면 취소를 보내지 않고 채널을 닫은 뒤 안내한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        w.channels.channel.push(MatchFailed("MATCH_FAILED", "대결을 만들지 못했어요. 다시 시도해 주세요"))
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_error_match_failed), vm.state.value.notice)
        assertEquals(1, w.channels.channel.closed)
        assertTrue("cancel" !in w.battles.calls, "서버가 이미 대기열에서 뺐다")
        val waited = vm.state.value.waitingSeconds
        advanceTimeBy(5_000)
        assertEquals(waited, vm.state.value.waitingSeconds, "대기 시간을 더 세지 않는다")
    }

    @Test
    fun `모르는 사유의 매칭 실패는 서버 문구를 보인다`() = main.runVmTest { vms ->
        val w = world()
        val vm = quick(w, vms)
        w.channels.channel.push(MatchFailed("SOMETHING_NEW", "잠시 뒤 다시 시도해 주세요"))
        runCurrent()
        assertEquals(UiText.Plain("잠시 뒤 다시 시도해 주세요"), vm.state.value.notice)
    }

    // ---------- 친구 초대 ----------

    private fun TestScope.invite(w: BattleWorld, vms: ViewModelCloser): MatchViewModel = vms.vm(w).also {
        it.startInvite(Distance(5_000))
        runCurrent()
    }

    @Test
    fun `TC-9664 초대 - 채널 연결 뒤 초대를 만들고 INVITING과 공유 이벤트(내 닉네임)를 낸다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.vm(w)
        vm.events.test {
            vm.startInvite(Distance(5_000))
            runCurrent()
            val share = assertIs<MatchEvent.ShareInvite>(awaitItem())
            assertEquals(friendInvite(), share.invite)
            assertEquals("달리는곰", share.inviterName)
        }
        assertEquals(MatchPhase.INVITING, vm.state.value.phase)
        assertEquals(OpponentKind.FRIEND, vm.state.value.opponentKind)
        assertEquals(listOf("invite:5000"), w.battles.calls)
        assertEquals(1, w.channels.channel.connects)
    }

    @Test
    fun `TC-9673 AN8-2 초대 응답이 프로필보다 먼저 와도 공유 메시지는 내 닉네임을 기다려 싣는다`() = main.runVmTest { vms ->
        val w = world()
        val gate = CompletableDeferred<Unit>()
        accounts.summaryGate = gate
        val vm = vms.vm(w)
        vm.events.test {
            vm.startInvite(Distance(5_000))
            runCurrent()
            assertEquals(MatchPhase.INVITING, vm.state.value.phase, "초대 대기는 바로 보인다")
            expectNoEvents()
            gate.complete(Unit)
            runCurrent()
            assertEquals("달리는곰", assertIs<MatchEvent.ShareInvite>(awaitItem()).inviterName)
            vm.onResend()
            runCurrent()
            assertEquals("달리는곰", assertIs<MatchEvent.ShareInvite>(awaitItem()).inviterName, "다시 보내기도 같은 이름")
        }
    }

    @Test
    fun `TC-9673 AN8-2 닉네임을 못 읽으면(실패·응답 없음) 이름 없이 공유한다`() = main.runVmTest { vms ->
        val failed = world()
        val named = accounts.summaryResult
        accounts.summaryResult = Outcome.Failure(AppError.Network)
        val vm = vms.vm(failed)
        vm.events.test {
            vm.startInvite(Distance(5_000))
            runCurrent()
            assertNull(assertIs<MatchEvent.ShareInvite>(awaitItem()).inviterName, "읽기 실패")
        }
        val slow = world()
        accounts.summaryResult = named
        accounts.summaryGate = CompletableDeferred()
        val waiting = vms.vm(slow)
        waiting.events.test {
            waiting.startInvite(Distance(5_000))
            runCurrent()
            expectNoEvents()
            advanceTimeBy(MatchViewModel.INVITER_NAME_WAIT.toMillis() + 1)
            assertNull(assertIs<MatchEvent.ShareInvite>(awaitItem()).inviterName, "응답 없음")
        }
    }

    @Test
    fun `TC-9664 초대 거부 - 이미 보낸 초대·진행 중인 대결·일반 오류 문구, 위치 동의 없음은 시트`() = main.runVmTest { vms ->
        val cases = mapOf(
            "INVITE_ALREADY_PENDING" to UiText.Res(R.string.battle_invite_already_pending),
            "ALREADY_IN_BATTLE" to UiText.Res(R.string.battle_invite_busy),
            "BATTLE_DISTANCE_INVALID" to UiText.Res(R.string.battle_error_distance_invalid)
        )
        cases.forEach { (code, text) ->
            val w = world()
            w.battles.createInviteResult = rejected(code)
            assertEquals(text, invite(w, vms).state.value.notice, code)
        }
        val w = world()
        w.battles.createInviteResult = rejected("LOCATION_CONSENT_REQUIRED")
        val vm = invite(w, vms)
        assertTrue(vm.state.value.isConsentSheetVisible)
        w.battles.createInviteResult = Outcome.Success(friendInvite())
        vm.onConsentAgreed()
        runCurrent()
        assertEquals(listOf("LOCATION"), accounts.consents)
        assertEquals(MatchPhase.INVITING, vm.state.value.phase, "동의하면 초대를 다시 만든다")
    }

    @Test
    fun `TC-9665 초대 대기 경과를 센다`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        assertEquals(0L, vm.state.value.waitingSeconds)
        advanceTimeBy(299_100)
        assertEquals(299L, vm.state.value.waitingSeconds)
        assertEquals("4:59", waitLabel(vm.state.value.waitingSeconds))
    }

    @Test
    fun `TC-9666 서버 만료 알림이면 바로, 로컬 타이머는 만료 + 2초에 INVITE_ENDED`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        w.channels.channel.push(FriendInviteExpired(41))
        runCurrent()
        assertEquals(MatchPhase.INVITE_ENDED, vm.state.value.phase)
        assertEquals(MatchUiState.InviteEnd.EXPIRED, vm.state.value.inviteEnd)

        val w2 = world()
        val vm2 = invite(w2, vms)
        advanceTimeBy(301_999)
        assertEquals(MatchPhase.INVITING, vm2.state.value.phase, "만료 + 1.999초까지 기다린다")
        advanceTimeBy(2)
        assertEquals(MatchPhase.INVITE_ENDED, vm2.state.value.phase)
        assertTrue(w2.battles.calls.none { it.startsWith("cancelInvite") }, "서버가 이미 끝냈다")
    }

    @Test
    fun `다른 초대의 끝 알림은 무시한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        w.channels.channel.push(FriendInviteDeclined(99))
        runCurrent()
        assertEquals(MatchPhase.INVITING, vm.state.value.phase)
    }

    @Test
    fun `TC-9667 거절 알림이면 거절 안내, 확인하면 배틀런 화면 친구 분절로`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        w.channels.channel.push(FriendInviteDeclined(41))
        runCurrent()
        assertEquals(MatchUiState.InviteEnd.DECLINED, vm.state.value.inviteEnd)
        vm.events.test {
            assertIs<MatchEvent.ShareInvite>(awaitItem())
            vm.onInviteEndedConfirmed()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
    }

    @Test
    fun `TC-9668 다시 보내기는 같은 초대로 공유만 다시 하고 초대를 새로 만들지 않는다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.vm(w)
        vm.events.test {
            vm.startInvite(Distance(5_000))
            runCurrent()
            awaitItem()
            vm.onResend()
            assertEquals(friendInvite(), assertIs<MatchEvent.ShareInvite>(awaitItem()).invite)
        }
        assertEquals(1, w.battles.calls.count { it.startsWith("invite:") })
    }

    @Test
    fun `TC-9669 취소 - 204면 초대를 취소하고 이전 화면, INVITE_ALREADY_JOINED면 취소를 접고 성사를 기다린다`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        vm.events.test {
            assertIs<MatchEvent.ShareInvite>(awaitItem())
            vm.onCancel()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:41" })

        val w2 = world()
        w2.battles.cancelInviteResults += rejected("INVITE_ALREADY_JOINED")
        val vm2 = invite(w2, vms)
        vm2.onCancel()
        runCurrent()
        assertEquals(MatchPhase.INVITING, vm2.state.value.phase)
        w2.channels.channel.push(matchFound())
        runCurrent()
        assertEquals(MatchPhase.MATCHED, vm2.state.value.phase)
    }

    @Test
    fun `TC-9670 초대 대기 화면이 사라지면 앱 범위에서 초대를 취소한다`() = main.runVmTest {
        val w = world()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, factory(w))[MatchViewModel::class]
        vm.startInvite(Distance(5_000))
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:41" })
        assertEquals(1, w.channels.channel.closed)
    }

    @Test
    fun `TC-9672 카카오톡도 웹 공유도 못 열면 초대를 취소하고 안내한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        vm.onShareFailed()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_invite_share_failed), vm.state.value.notice)
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:41" })
    }

    @Test
    fun `TC-9682 초대한 쪽도 성사되면 친구 대결로 출발한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = invite(w, vms)
        vm.events.test {
            assertIs<MatchEvent.ShareInvite>(awaitItem())
            val match = matchFound()
            w.channels.channel.push(match)
            w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
            runCurrent()
            val started = assertIs<MatchEvent.Started>(awaitItem())
            assertEquals(OpponentKind.FRIEND.name, started.key.opponentKind)
        }
    }

    @Test
    fun `초대 만들기가 위치 동의로 거부된 뒤 시트를 닫으면 머물지 않고 이전 화면으로`() = main.runVmTest { vms ->
        val w = world()
        w.battles.createInviteResult = rejected("LOCATION_CONSENT_REQUIRED")
        val vm = invite(w, vms)
        assertTrue(vm.state.value.isConsentSheetVisible)
        vm.events.test {
            vm.onConsentClosed()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
    }

    @Test
    fun `초대 만들기가 거부된 뒤 동의 주기도 실패하면 사유 안내(확인하면 이전 화면)`() = main.runVmTest { vms ->
        val w = world()
        w.battles.createInviteResult = rejected("LOCATION_CONSENT_REQUIRED")
        accounts.consentResult = rejected("QUEUE_UNAVAILABLE")
        val vm = invite(w, vms)
        vm.onConsentAgreed()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_error_queue_unavailable), vm.state.value.notice)
    }

    @Test
    fun `요청 중에 뒤로 가면 서버 대기를 정리하고 이전 화면으로`() = main.runVmTest { vms ->
        val w = world()
        w.channels.channel.connectGate = CompletableDeferred()
        val vm = vms.vm(w)
        vm.startQuick(Distance(5_000))
        runCurrent()
        assertEquals(MatchPhase.REQUESTING, vm.state.value.phase)
        vm.events.test {
            vm.onCancel()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertTrue("cancel" in w.battles.calls)
    }

    @Test
    fun `연결이 끊겨 정리한 대기는 화면이 지워질 때 다시 정리하지 않는다`() = main.runVmTest {
        val w = world()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, factory(w))[MatchViewModel::class]
        vm.startQuick(Distance(5_000))
        runCurrent()
        w.channels.channel.endMessages()
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "cancel" })
    }

    @Test
    fun `초대를 만드는 중에 화면이 사라져도 만들어진 초대를 취소한다(D-271)`() = main.runVmTest {
        val w = world()
        w.battles.createGate = CompletableDeferred()
        val store = ViewModelStore()
        val vm = ViewModelProvider.create(store, factory(w))[MatchViewModel::class]
        vm.startInvite(Distance(5_000))
        runCurrent()
        store.clear()
        runCurrent()
        w.battles.createGate?.complete(Unit)
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:41" })
    }

    @Test
    fun `기기 시계가 서버보다 크게 앞서도 초대를 바로 끝내지 않는다(만료 5분 + 2초를 만든 때부터 잰다)`() = main.runVmTest { vms ->
        val w = world()
        w.clock.now = start.plusSeconds(600)
        val vm = invite(w, vms)
        advanceTimeBy(60_000)
        assertEquals(MatchPhase.INVITING, vm.state.value.phase)
        advanceTimeBy(242_100)
        assertEquals(MatchPhase.INVITE_ENDED, vm.state.value.phase)
    }

    // ---------- 받은 초대 참가 ----------

    private fun TestScope.join(w: BattleWorld, vms: ViewModelCloser): MatchViewModel = vms.vm(w).also {
        it.startJoin("tok")
        runCurrent()
    }

    @Test
    fun `TC-9677 막힘 사유가 없으면 참가 확인, 끝난·참가된·내 초대·바쁨은 사유 안내`() = main.runVmTest { vms ->
        val w = world()
        val vm = join(w, vms)
        assertEquals(MatchPhase.CONFIRMING, vm.state.value.phase)
        assertEquals("바람처럼", vm.state.value.invite?.inviter?.name)
        assertNull(vm.state.value.notice)

        val cases = mapOf(
            "INVITE_EXPIRED" to R.string.battle_join_ended,
            "INVITE_CANCELLED" to R.string.battle_join_ended,
            "INVITE_ALREADY_JOINED" to R.string.battle_join_already_joined,
            "INVITE_OWN" to R.string.battle_join_own,
            "ALREADY_IN_BATTLE" to R.string.battle_join_busy
        )
        cases.forEach { (reason, text) ->
            val each = world()
            each.battles.inviteViewResult = Outcome.Success(inviteView(blockedReason = reason))
            assertEquals(UiText.Res(text), join(each, vms).state.value.notice, reason)
        }
        val missing = world()
        missing.battles.inviteViewResult = rejected("INVITE_NOT_FOUND")
        assertEquals(UiText.Res(R.string.battle_join_ended), join(missing, vms).state.value.notice)
    }

    @Test
    fun `TC-9663 참가하는 쪽의 내 티어는 참가 확인 정보의 내 티어다(R2-10)`() = main.runVmTest { vms ->
        val vm = join(world(), vms)
        assertEquals("UNRANKED", vm.state.value.myTier)
    }

    @Test
    fun `TC-9678 서버가 막지 않아도 이 기기가 측정 중이면 참가할 수 없다`() = main.runVmTest { vms ->
        val w = world()
        w.tracker.startRun(
            com.a207.runkk.app.domain.run.RunStartSpec(
                mode = com.a207.runkk.app.core.model.RunMode.FREE,
                cardKind = com.a207.runkk.app.core.model.CardKind.FREE_RUN
            )
        )
        val vm = join(w, vms)
        assertEquals(UiText.Res(R.string.battle_join_busy), vm.state.value.notice)
    }

    @Test
    fun `TC-9680 거절은 거절을 보내고 홈으로, 거부되면 끝난 초대 안내`() = main.runVmTest { vms ->
        val w = world()
        val vm = join(w, vms)
        vm.events.test {
            vm.onDecline()
            runCurrent()
            assertEquals(MatchEvent.Exit, awaitItem())
        }
        assertTrue("decline:tok" in w.battles.calls)

        val w2 = world()
        w2.battles.declineResult = rejected("INVITE_EXPIRED")
        val vm2 = join(w2, vms)
        vm2.onDecline()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_join_ended), vm2.state.value.notice)
    }

    @Test
    fun `TC-9773 참가 확인 화면은 스스로 만료하지 않고 4분 59초면 참가, 5분에 서버가 만료로 거부하면 끝난 초대 안내`() = main.runVmTest { vms ->
        val early = world()
        val joined = join(early, vms)
        early.clock.now = early.clock.now.plusSeconds(299)
        advanceTimeBy(299_000)
        joined.onAccept()
        runCurrent()
        assertEquals(MatchPhase.JOINING, joined.state.value.phase, "4:59 참가")

        val late = world()
        val vm = join(late, vms)
        late.clock.now = late.clock.now.plusSeconds(300)
        advanceTimeBy(300_000)
        runCurrent()
        assertEquals(MatchPhase.CONFIRMING, vm.state.value.phase, "앱이 자체 만료하지 않는다")
        assertNull(vm.state.value.notice)
        late.battles.acceptResult = rejected("INVITE_EXPIRED")
        vm.onAccept()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_join_ended), vm.state.value.notice, "5:00 서버 409 INVITE_EXPIRED")
    }

    @Test
    fun `TC-9681 위치 동의가 없으면 시트를 띄우고 참가를 보내지 않는다`() = main.runVmTest { vms ->
        val w = world()
        settings.isConsented = false
        val vm = join(w, vms)
        vm.onAccept()
        runCurrent()
        assertTrue(vm.state.value.isConsentSheetVisible)
        assertTrue(w.battles.calls.none { it.startsWith("accept") })
        vm.onConsentAgreed()
        runCurrent()
        assertTrue("accept:tok" in w.battles.calls, "동의하면 이어서 참가한다")
    }

    @Test
    fun `TC-9681 참가는 채널 연결을 끝낸 다음에 보낸다`() = main.runVmTest { vms ->
        val w = world()
        val vm = join(w, vms)
        assertEquals(0, w.channels.channel.connects)
        vm.onAccept()
        runCurrent()
        assertEquals(1, w.channels.channel.connects)
        assertEquals(listOf("inviteView:tok", "accept:tok"), w.battles.calls)
        assertEquals(MatchPhase.JOINING, vm.state.value.phase)
    }

    @Test
    fun `TC-9682 참가 뒤 MatchFound가 오면 성사 흐름과 같이 자동 출발한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = join(w, vms)
        vm.onAccept()
        runCurrent()
        vm.events.test {
            val match = matchFound()
            w.channels.channel.push(match)
            runCurrent()
            assertEquals(MatchPhase.MATCHED, vm.state.value.phase)
            assertIs<BattleRunState.Matched>(w.coordinator.state.value)
            w.channels.channel.push(BattleStart(match.battleId, w.clock.now, opponentTrack = null))
            runCurrent()
            assertEquals(OpponentKind.FRIEND.name, assertIs<MatchEvent.Started>(awaitItem()).key.opponentKind)
        }
    }

    @Test
    fun `TC-9683 참가 뒤 10초 안에 MatchFound가 없으면 진행 중인 대결을 확인한다`() = main.runVmTest { vms ->
        val w = world()
        val vm = join(w, vms)
        vm.onAccept()
        runCurrent()
        advanceTimeBy(9_000)
        assertTrue("active" !in w.battles.calls)
        advanceTimeBy(1_100)
        assertTrue("active" in w.battles.calls)
        assertEquals(UiText.Res(R.string.battle_match_start_failed), vm.state.value.notice, "대결이 없으면 실패 안내")

        val w2 = world()
        w2.battles.activeResult = Outcome.Success(
            ActiveBattle(
                9,
                BattleHistory.Type.FRIEND,
                "WAITING_START",
                Distance(5_000),
                "바람처럼",
                w2.clock.now.plusSeconds(10)
            )
        )
        val vm2 = join(w2, vms)
        vm2.onAccept()
        runCurrent()
        advanceTimeBy(10_100)
        assertEquals(MatchPhase.MATCHED, vm2.state.value.phase, "대결이 있으면 그 대결로 합류한다")
        assertIs<BattleRunState.Countdown>(w2.coordinator.state.value)
    }

    @Test
    fun `참가 거부는 같은 사유 문구를 쓴다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.acceptResult = rejected("INVITE_ALREADY_JOINED")
        val vm = join(w, vms)
        vm.onAccept()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_join_already_joined), vm.state.value.notice)
    }

    private fun factory(w: BattleWorld) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MatchViewModel(w.battles, w.coordinator, w.channels, w.clock, consentOf(settings, accounts), accounts) as T
    }
}
