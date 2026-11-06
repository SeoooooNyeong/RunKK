@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.account.PlacementStatus
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 배치고사 완료(FR-281): 머리 승패 수, 확정 티어, 랜덤 대결·홈. */
class PlacementCompleteViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val accounts = FakeAccounts()

    @Test
    fun `완료 화면을 읽으면 마지막 판을 본 것으로 기억하고 못 읽으면 기억하지 않는다(PL2-A2)`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Failure(AppError.Network)
        vms.track(PlacementCompleteViewModel(accounts))
        runCurrent()
        assertEquals(null, accounts.placementSeen)

        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD"))
        vms.track(PlacementCompleteViewModel(accounts))
        runCurrent()
        assertEquals(903L, accounts.placementSeen, "센 세 판 가운데 마지막 판(901·902·903)")
    }

    @Test
    fun `TC-5884 완료 화면 머리는 승패 수이고 무승부가 있으면 무를 더한다`() = main.runVmTest { vms ->
        fun headOf(vararg verdicts: String): PlacementCompleteUiState {
            accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "GOLD", verdicts.toList()))
            val vm = vms.track(PlacementCompleteViewModel(accounts))
            runCurrent()
            return vm.state.value
        }
        val mixed = headOf("WIN", "LOSE", "DRAW")
        assertEquals(Triple(1, 1, 1), Triple(mixed.wins, mixed.losses, mixed.draws))
        assertEquals(UiText.Res(R.string.placement_complete_record_draw, listOf(1, 1, 1)), mixed.recordLabel)
        assertEquals("GOLD", mixed.tier)
        assertEquals(
            UiText.Res(R.string.placement_complete_record, listOf(0, 3)),
            headOf("LOSE", "LOSE", "LOSE").recordLabel
        )
        assertEquals(
            UiText.Res(R.string.placement_complete_record, listOf(2, 1)),
            headOf("WIN", "WIN", "LOSE").recordLabel
        )
    }

    @Test
    fun `TC-5885 완료 뒤 랜덤 대결이나 홈으로 간다`() = main.runVmTest { vms ->
        accounts.placementResult = Outcome.Success(settled(PlacementStatus.COMPLETED, "SILVER"))
        val vm = vms.track(PlacementCompleteViewModel(accounts))
        val events = mutableListOf<PlacementCompleteEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.onRandomBattle()
        vm.onHome()
        vm.onBack()
        runCurrent()
        assertEquals(
            listOf(PlacementCompleteEvent.RandomBattle, PlacementCompleteEvent.Home, PlacementCompleteEvent.Home),
            events
        )
    }
}
