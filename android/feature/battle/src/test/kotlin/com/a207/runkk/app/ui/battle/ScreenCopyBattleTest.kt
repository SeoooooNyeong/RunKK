@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test

/**
 * 받아 둔 첫 화면 먼저 보여 주기(S15P31A207-345 2차): 셀럽런은 시작 로딩이 남긴 셀럽 목록 사본을 먼저 그려 네트워크 없이 고르고 시작할 수
 * 있다(대결 생성은 그대로 서버에 묻는다). 뒤에서 새로 받으면 바꾸고, 받지 못해도 사본을 둔다. 친구 대결은 최근 친구 사본을 먼저 그린다.
 */
class ScreenCopyBattleTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `셀럽런은 사본을 먼저 그리고 받는 동안에도 골라 시작할 수 있으며 새로 받으면 바꾼다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.celebCopy = RecordBattleCandidates(listOf(celebCandidates[0]))
        val gate = CompletableDeferred<Unit>()
        w.battles.celebGate = gate
        val vm = vms.track(CelebBattleViewModel(w.battles, w.coordinator))
        runCurrent()

        val shown = vm.state.value
        assertEquals(listOf(celebCandidates[0].displayName), shown.candidates.map { it.name })
        assertTrue(shown.isLoading && shown.isShowingCopy, "받는 중이어도 로딩 원 대신 사본 목록을 그린다")

        vm.events.test {
            vm.onSelect(vm.state.value.candidates[0])
            vm.onStart()
            runCurrent()
            assertIs<RecordBattleEvent.Start>(awaitItem(), "사본으로 고른 셀럽으로 시작한다")
        }
        assertTrue(w.battles.calls.any { it.startsWith("startCeleb:") }, "대결 생성은 그대로 서버에 묻는다")

        gate.complete(Unit)
        runCurrent()
        assertEquals(celebCandidates.size, vm.state.value.candidates.size, "새로 받은 목록으로 바꾼다")
        assertFalse(vm.state.value.isShowingCopy)
        advanceTimeBy(3_100)
    }

    @Test
    fun `셀럽런을 새로 받지 못해도 사본이 있으면 오류 화면으로 덮지 않는다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.celebCopy = RecordBattleCandidates(listOf(celebCandidates[0]))
        w.battles.celebResult = Outcome.Failure(AppError.Network)
        val vm = vms.track(CelebBattleViewModel(w.battles, w.coordinator))
        runCurrent()

        assertEquals(1, vm.state.value.candidates.size)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `셀럽런 사본이 없으면 지금처럼 받는 동안 로딩이고 실패는 오류다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.celebResult = Outcome.Failure(AppError.Network)
        val vm = vms.track(CelebBattleViewModel(w.battles, w.coordinator))
        runCurrent()
        assertTrue(vm.state.value.candidates.isEmpty())
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `친구 대결은 최근 친구 사본을 먼저 그리고 새로 받으면 바꾼다`() = main.runVmTest { vms ->
        val w = world()
        w.battles.historyCopy = historyOf(31L to "사본 친구")
        w.battles.historyResult = Outcome.Failure(AppError.Network)
        val vm = vms.track(
            FriendBattleViewModel(w.battles, w.coordinator, consentOf(FakeConsentSettings(), FakeAccounts()))
        )
        runCurrent()
        assertEquals(listOf(31L), vm.state.value.friends.map { it.memberId }, "받지 못해도 사본을 둔다")

        w.battles.historyResult = Outcome.Success(historyOf(41L to "새 친구"))
        val fresh = vms.track(
            FriendBattleViewModel(w.battles, w.coordinator, consentOf(FakeConsentSettings(), FakeAccounts()))
        )
        runCurrent()
        assertEquals(listOf(41L), fresh.state.value.friends.map { it.memberId }, "새로 받으면 바꾼다")
    }

    private fun historyOf(friend: Pair<Long, String>) = BattleHistory(
        entries = listOf(
            BattleHistory.Entry(
                1,
                BattleHistory.Type.FRIEND,
                friend.second,
                Distance(3_000),
                "WIN",
                Instant.parse("2026-11-18T00:00:00Z"),
                opponentMemberId = friend.first
            )
        ),
        total = 1,
        wins = 1,
        losses = 0,
        selfNewRecords = 0
    )
}
