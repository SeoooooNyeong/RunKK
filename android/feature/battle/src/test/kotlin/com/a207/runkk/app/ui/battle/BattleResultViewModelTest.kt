@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BattleResultViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `결과 비교표·판정·리워드와 이번 주 전적을 보인다(FR-093)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        val s = vm.state.value
        assertEquals(Verdict.WIN, s.verdict)
        assertEquals(4, s.result?.rows?.size)
        assertEquals(70, s.result?.rewardPoints)
        assertEquals(uiText(R.string.battle_weekly_spoken, 3, 1), s.weekly?.let(::weeklyLabel))
        assertEquals(listOf("result:9"), w.battles.calls, "이번 주 전적은 결과 응답에 있어 이력을 따로 부르지 않는다")
    }

    @Test
    fun `판정 문구는 상대 표시명과 도달 시간 차를 쓴다(시안 HiBattleResult)`() {
        val won = battleResult()
        assertEquals(
            uiText(R.string.battle_result_win_name_gap, "바람처럼", uiText(R.string.battle_gap_seconds, 42L)),
            verdictMessage(Verdict.WIN, won)
        )
        assertEquals(
            uiText(
                R.string.battle_result_lose_name_gap,
                "바람처럼",
                uiText(R.string.battle_gap_minutes_seconds, 1L, 5L)
            ),
            verdictMessage(
                Verdict.LOSE,
                won.copy(
                    myReach = java.time.Duration.ofSeconds(1_757),
                    opponentReach = java.time.Duration.ofSeconds(1_692)
                )
            )
        )
        assertEquals(uiText(R.string.battle_result_draw_name, "바람처럼"), verdictMessage(Verdict.DRAW, won))
        assertEquals(uiText(R.string.battle_result_win), verdictMessage(Verdict.WIN, won.copy(opponentName = null)))
        assertEquals(
            uiText(R.string.battle_result_win_name, "바람처럼"),
            verdictMessage(Verdict.WIN, won.copy(myReach = null))
        )
    }

    @Test
    fun `이력의 기록 차이는 빠름·느림으로, 이번 주 전적은 무승부가 있으면 더한다`() {
        assertEquals(
            uiText(R.string.battle_gap_faster, uiText(R.string.battle_gap_seconds, 42L)),
            gapLabel(java.time.Duration.ofSeconds(-42))
        )
        assertEquals(
            uiText(R.string.battle_gap_slower, uiText(R.string.battle_gap_minutes_seconds, 1L, 12L)),
            gapLabel(java.time.Duration.ofSeconds(72))
        )
        assertEquals(uiText(R.string.battle_gap_same), gapLabel(java.time.Duration.ZERO))
        assertEquals(
            uiText(R.string.battle_weekly_spoken_draw, 3, 1, 1),
            weeklyLabel(com.a207.runkk.app.domain.battle.BattleResult.Weekly(3, 1, 1))
        )
        assertEquals(
            uiText(R.string.battle_weekly_spoken, 3, 1),
            weeklySpoken(com.a207.runkk.app.domain.battle.BattleResult.Weekly(3, 1, 0))
        )
        assertEquals(
            uiText(R.string.battle_weekly_spoken_draw, 3, 1, 1),
            weeklySpoken(com.a207.runkk.app.domain.battle.BattleResult.Weekly(3, 1, 1))
        )
    }

    @Test
    fun `보류 결과를 보이고 다시 불러올 수 있다(FR-108)`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Success(battleResult(pending = true, verdict = null))
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        assertTrue(vm.state.value.result?.isPending == true)
        assertNull(vm.state.value.verdict)
        w.battles.resultResult = Outcome.Success(battleResult())
        vm.load(9, isForced = true)
        runCurrent()
        assertEquals(Verdict.WIN, vm.state.value.verdict)
    }

    @Test
    fun `보류면 판정이 나올 때까지 몇 초마다 다시 읽는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Success(battleResult(pending = true, verdict = null))
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        assertTrue(vm.state.value.result?.isPending == true)
        assertTrue(vm.state.value.isAwaitingVerdict, "다시 읽는 동안은 결과를 확인하는 화면")
        advanceTimeBy(BattleResultViewModel.PENDING_RETRY_MILLIS + 1)
        assertEquals(2, w.battles.calls.count { it == "result:9" }, "보류면 다시 읽는다")
        w.battles.resultResult = Outcome.Success(battleResult())
        advanceTimeBy(BattleResultViewModel.PENDING_RETRY_MILLIS + 1)
        assertEquals(Verdict.WIN, vm.state.value.verdict)
        assertFalse(vm.state.value.isAwaitingVerdict)
        advanceTimeBy(BattleResultViewModel.PENDING_RETRY_MILLIS * 3)
        assertEquals(3, w.battles.calls.count { it == "result:9" }, "판정이 나오면 더 읽지 않는다")
    }

    @Test
    fun `보류가 길어지면 다시 읽기를 멈추고 보류 화면에 남는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Success(battleResult(pending = true, verdict = null))
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        advanceTimeBy(BattleResultViewModel.PENDING_RETRY_MILLIS * (BattleResultViewModel.PENDING_RETRIES + 5))
        assertEquals(1 + BattleResultViewModel.PENDING_RETRIES, w.battles.calls.count { it == "result:9" })
        assertTrue(vm.state.value.result?.isPending == true)
        assertFalse(vm.state.value.isAwaitingVerdict, "다시 읽기를 멈추면 늦어짐 안내와 다시 보기")
    }

    @Test
    fun `결과를 불러오지 못하면 오류, 같은 대결은 다시 부르면 다시 읽는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = rejected("BATTLE_NOT_FOUND")
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_error_not_found), vm.state.value.error)
        w.battles.resultResult = Outcome.Success(battleResult())
        vm.load(9)
        runCurrent()
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.result)
    }

    @Test
    fun `함께 달린 기록 - 종류로 거르고 고스트는 랜덤 라벨(FR-101)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = BattleHistoryViewModel(w.battles)
        runCurrent()
        assertEquals(12, vm.state.value.history?.total)
        vm.onFilter(2)
        runCurrent()
        assertEquals(listOf("history:null", "history:SELF"), w.battles.calls)
        assertEquals(R.string.battle_type_random, typeLabel(BattleHistory.Type.GHOST))
    }

    @Test
    fun `함께 달린 기록은 끝에 닿으면 다음 쪽을 이어 받고 끝이면 더 부르지 않는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.historyResult = Outcome.Success(history(nextCursor = 7))
        val vm = BattleHistoryViewModel(w.battles)
        runCurrent()
        vm.loadMore()
        runCurrent()
        assertEquals(listOf(9L, 8L, 7L, 4L), vm.state.value.history?.entries?.map { it.battleId })
        assertNull(vm.state.value.history?.nextCursor)
        vm.loadMore()
        runCurrent()
        assertEquals(1, w.battles.calls.count { it == "history:null:7" })
    }

    @Test
    fun `함께 달린 기록을 불러오지 못하면 오류`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.historyResult = Outcome.Failure(AppError.Network)
        val vm = BattleHistoryViewModel(w.battles)
        runCurrent()
        assertNotNull(vm.state.value.error)
    }
}
