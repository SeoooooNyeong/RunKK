@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 배치고사 진행 화면(FR-279, D-479·D-499): 진행 읽기, 확정 티어면 랜덤 대결로, 시작은 같은 매칭 흐름. */
class PlacementViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeConsentSettings()
    private val accounts = FakeAccounts()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(PlacementViewModel(accounts, w.battles, w.coordinator, consentOf(settings, accounts)))

    @Test
    fun `TC-5873 배치고사 진행은 다음 판 번호와 기본 거리로 시작한다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(0))
        val w = world()
        w.battles.status = Outcome.Success(MatchAcceptance(false, null, defaultDistance = Distance(5_000)))
        val vm = vms.vm(w)
        runCurrent()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(0, state.progress?.countedRounds)
        assertEquals(1, state.round, "버튼 「배치고사 1판 시작」")
        assertEquals(Distance(5_000), state.distance)
        assertTrue(state.canStart)
    }

    @Test
    fun `TC-5874 확정 티어면 랜덤 대결 화면으로 바꿔 연다`() = main.runVmTest { vms ->
        // 완료 화면을 이미 본 완료(마지막 판 903)와 면제
        accounts.placementSeen = 903
        listOf(PlacementStatus.COMPLETED, PlacementStatus.EXEMPT).forEach { status ->
            accounts.placementResult = Outcome.Success(settled(status, "GOLD"))
            val vm = vms.vm(world())
            vm.events.test {
                assertEquals(PlacementEvent.Open(Routes.Key(Routes.BATTLE_RANDOM)), awaitItem(), "$status")
            }
        }
    }

    @Test
    fun `배치고사를 마쳤는데 완료 화면을 아직 안 봤으면 완료 화면으로 바꿔 연다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        val unseen = vms.vm(world())
        unseen.events.test {
            assertEquals(PlacementEvent.Open(Routes.Key(Routes.PLACEMENT_COMPLETE)), awaitItem())
        }

        // 다른 판(앞 배치·다른 회원의 대결 ID)을 본 기억은 이번 완료와 상관없다
        accounts.placementSeen = 777
        val stale = vms.vm(world())
        stale.events.test {
            assertEquals(PlacementEvent.Open(Routes.Key(Routes.PLACEMENT_COMPLETE)), awaitItem())
        }
    }

    @Test
    fun `TC-5875 시작을 누르면 고른 거리로 같은 매칭 흐름을 연다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(placing(1, "GOLD"))
        val vm = vms.vm(world())
        runCurrent()
        vm.events.test {
            vm.onDistanceSelected(Distance(10_000))
            vm.onStartTapped()
            assertEquals(PlacementEvent.Open(Routes.BattleQuickMatch(10_000)), awaitItem())
        }

        settings.isConsented = false
        val noConsent = vms.vm(world())
        runCurrent()
        noConsent.events.test {
            noConsent.onStartTapped()
            assertTrue(noConsent.state.value.isConsentSheetVisible, "위치 동의가 없으면 동의 시트")
            expectNoEvents()
        }
    }

    @Test
    fun `TC-5876 진행을 못 읽으면 시작하지 않는다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Failure(AppError.Network)
        val vm = vms.vm(world())
        runCurrent()
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.canStart)
        vm.events.test {
            vm.onStartTapped()
            expectNoEvents()
        }
        accounts.placementResult = Outcome.Success(placing(2, "SILVER"))
        vm.onRetry()
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.round)
    }
}
