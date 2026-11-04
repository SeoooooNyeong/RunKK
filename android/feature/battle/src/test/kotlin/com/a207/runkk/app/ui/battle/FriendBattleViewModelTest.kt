@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.MatchAcceptance
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/** 친구 대결 화면(FR-286): 최근 친구 고르기는 초대와 묶지 않고, 거리 상자는 펼쳐 고른다. */
class FriendBattleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeConsentSettings()
    private val accounts = FakeAccounts()

    private fun ViewModelCloser.vm(w: BattleWorld) =
        track(FriendBattleViewModel(w.battles, w.coordinator, consentOf(settings, accounts)))

    private fun friendHistory() = BattleHistory(
        entries = listOf(
            BattleHistory.Entry(
                5,
                BattleHistory.Type.FRIEND,
                "바람처럼",
                Distance(3_000),
                "WIN",
                start,
                opponentMemberId = 21
            ),
            BattleHistory.Entry(
                4,
                BattleHistory.Type.FRIEND,
                "달빛",
                Distance(5_000),
                "LOSE",
                start.minusSeconds(86_400),
                opponentMemberId = 22
            )
        ),
        total = 2,
        wins = 1,
        losses = 1,
        selfNewRecords = 0
    )

    @Test
    fun `TC-5890 친구 고르기는 초대에 묶이지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.historyResult = Outcome.Success(friendHistory())
        val vm = vms.vm(w)
        runCurrent()
        assertEquals(listOf(21L, 22L), vm.state.value.friends.map { it.memberId })
        assertTrue("history:FRIEND" in w.battles.calls, "친구 대결 이력 첫 쪽을 읽는다")

        vm.onFriendSelected(22)
        vm.onDistanceSelected(Distance(3_000))
        assertEquals(22L, vm.state.value.selectedFriendId)
        vm.events.test {
            vm.onInviteTapped()
            assertEquals(FriendBattleEvent.Open(Routes.FriendInviteWait(3_000)), awaitItem())
        }
    }

    @Test
    fun `TC-5891 거리 상자는 펼치고 고른다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.status = Outcome.Success(MatchAcceptance(false, null, defaultDistance = Distance(1_000)))
        val vm = vms.vm(w)
        runCurrent()
        assertEquals(Distance(1_000), vm.state.value.distance, "처음 값은 기본 대결 거리")
        vm.onDistanceMenuToggle()
        assertTrue(vm.state.value.isDistanceMenuOpen)
        vm.onDistanceMenuToggle()
        assertFalse(vm.state.value.isDistanceMenuOpen)
        vm.onDistanceMenuToggle()
        vm.onDistanceSelected(Distance(10_000))
        assertEquals(Distance(10_000), vm.state.value.distance)
        assertFalse(vm.state.value.isDistanceMenuOpen, "고르면 닫힌다")
    }
}
