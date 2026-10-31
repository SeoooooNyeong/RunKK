@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MySummary
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import java.time.Duration
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 배틀런 화면(D-268): 분절, 기본 거리, 거리 카드 최고 기록, 위치 동의, 남은 초대 복구(D-271). */
class BattleModeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeConsentSettings()
    private val accounts = FakeAccounts()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(BattleModeViewModel(w.battles, w.coordinator, consentOf(settings, accounts), accounts))

    @Test
    fun `언랭이면 랜덤 시작 대신 남은 유효 러닝 수를 알리고 친구 초대는 막지 않는다`() = main.runVmTest { vms ->
        accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "UNRANKED", 1, null, null, 1))
        val vm = vms.vm(world())
        runCurrent()
        assertEquals(2, vm.state.value.randomRunsLeft)
        assertEquals(0, accounts.profileCalls, "프로필 전체를 읽지 않는다(R53)")
        vm.onRandomStartTapped()
        assertTrue(vm.state.value.isRandomLockNoticeVisible)
        vm.onRandomLockNoticeClosed()
        vm.events.test {
            vm.onInviteTapped()
            assertTrue(awaitItem() is BattleModeEvent.Open)
        }
    }

    @Test
    fun `유효 러닝 수를 모르면(옛 서버) 남은 수 없이 잠그고, 티어가 있으면 잠그지 않는다(D-331)`() = main.runVmTest { vms ->
        accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "UNRANKED", 1, null, null, null))
        val unknown = vms.vm(world())
        runCurrent()
        assertEquals(0, unknown.state.value.randomRunsLeft)
        accounts.summaryResult = Outcome.Success(MySummary("달리는곰", "GOLD", 3, null, null, 12))
        val ranked = vms.vm(world())
        runCurrent()
        assertNull(ranked.state.value.randomRunsLeft)
    }

    @Test
    fun `기본 거리는 서버 기본 대결 거리이고 카드마다 셀프런 후보의 최고 기록을 보인다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.status = Outcome.Success(MatchAcceptance(false, null, defaultDistance = Distance(3_000)))
        w.battles.selfResult = Outcome.Success(
            RecordBattleCandidates(
                listOf(
                    RecordBattleCandidates.Candidate(
                        null,
                        "나",
                        Distance(1_000),
                        Duration.ofSeconds(292),
                        LocalDate.of(2026, 9, 14),
                        null,
                        false
                    ),
                    RecordBattleCandidates.Candidate(null, "나", Distance(3_000), null, null, null, false),
                    RecordBattleCandidates.Candidate(
                        null,
                        "나",
                        Distance(21_097),
                        Duration.ofSeconds(7_000),
                        null,
                        null,
                        false
                    )
                )
            )
        )
        val vm = vms.vm(w)
        runCurrent()
        val s = vm.state.value
        assertEquals(Distance(3_000), s.distance)
        assertEquals(MatchUiState.DISTANCES, s.cards.map { it.distance }, "1·3·5·10km만 보인다")
        assertEquals(Duration.ofSeconds(292), s.cards[0].best)
        assertEquals(LocalDate.of(2026, 9, 14), s.cards[0].recordedOn)
        assertNull(s.cards[1].best, "기록 없음")
    }

    @Test
    fun `기본 거리를 읽지 못하면 5km`() = main.runVmTest { vms ->
        val w = world()
        w.battles.status = rejected("QUEUE_UNAVAILABLE")
        val vm = vms.vm(w)
        runCurrent()
        assertEquals(Distance(5_000), vm.state.value.distance)
    }

    @Test
    fun `기록 없는 거리도 고를 수 있고 고른 거리로 매칭·초대 화면을 연다`() = main.runVmTest { vms ->
        val w = world()
        val vm = vms.vm(w)
        runCurrent()
        vm.onDistanceSelected(Distance(10_000))
        vm.events.test {
            vm.onRandomStartTapped()
            assertEquals(BattleModeEvent.Open(Routes.BattleQuickMatch(10_000)), awaitItem())
            vm.onSegmentSelected(BattleModeUiState.Segment.FRIEND)
            vm.onInviteTapped()
            assertEquals(BattleModeEvent.Open(Routes.FriendInviteWait(10_000)), awaitItem())
        }
    }

    @Test
    fun `TC-9685 위치 동의가 없으면 시트, 닫으면 시작하지 않고 동의하면 누른 동작을 잇는다`() = main.runVmTest { vms ->
        val w = world()
        settings.isConsented = false
        val vm = vms.vm(w)
        runCurrent()
        vm.events.test {
            vm.onRandomStartTapped()
            assertTrue(vm.state.value.isConsentSheetVisible)
            vm.onConsentClosed()
            expectNoEvents()
            vm.onInviteTapped()
            vm.onConsentAgreed()
            runCurrent()
            assertEquals(BattleModeEvent.Open(Routes.FriendInviteWait(5_000)), awaitItem())
        }
        assertEquals(listOf("LOCATION"), accounts.consents)
    }

    @Test
    fun `TC-9671 열릴 때 남은 초대가 있으면(200) 그 초대를 취소하고 없으면(204) 부르지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.myInviteResult = Outcome.Success(friendInvite(inviteId = 77))
        val vm = vms.vm(w)
        vm.onResumed()
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:77" })

        val none = world()
        val other = vms.vm(none)
        other.onResumed()
        runCurrent()
        assertTrue(none.battles.calls.none { it.startsWith("cancelInvite") })
    }

    @Test
    fun `AN8-22 초대 대기로 넘어가면 늦게 끝난 남은 초대 조회가 방금 만든 초대를 지우지 않는다`() = main.runVmTest { vms ->
        val w = world()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.battles.myInviteGate = gate
        val vm = vms.vm(w)
        vm.onResumed()
        runCurrent()
        vm.onInviteTapped()
        w.battles.myInviteResult = Outcome.Success(friendInvite(inviteId = 88))
        gate.complete(Unit)
        runCurrent()
        assertTrue(w.battles.calls.none { it.startsWith("cancelInvite") })
    }

    @Test
    fun `AN8-22 랜덤 매칭으로 넘어갈 때는 남은 초대 조회를 끝까지 두어 정리한다`() = main.runVmTest { vms ->
        val w = world()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        w.battles.myInviteGate = gate
        w.battles.myInviteResult = Outcome.Success(friendInvite(inviteId = 77))
        val vm = vms.vm(w)
        vm.onResumed()
        runCurrent()
        vm.onRandomStartTapped()
        gate.complete(Unit)
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "cancelInvite:77" })
    }
}
