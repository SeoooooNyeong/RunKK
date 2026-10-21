@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        assertEquals("3W 1L", s.weekly?.let(::weeklyLabel))
        assertEquals(listOf("result:9"), w.battles.calls, "이번 주 전적은 결과 응답에 있어 이력을 따로 부르지 않는다")
    }

    @Test
    fun `판정 문구는 상대 표시명과 도달 시간 차를 쓴다(시안 HiBattleResult)`() {
        val won = battleResult()
        assertEquals("바람처럼님보다 42초 먼저 도착했어요!", verdictMessage(Verdict.WIN, won))
        assertEquals(
            "바람처럼님이 1분 5초 먼저 도착했어요. 다음 대결에서 다시 만나요",
            verdictMessage(
                Verdict.LOSE,
                won.copy(
                    myReach = java.time.Duration.ofSeconds(1_757),
                    opponentReach = java.time.Duration.ofSeconds(1_692)
                )
            )
        )
        assertEquals("바람처럼님과 1초 안에 함께 도착했어요. 무승부!", verdictMessage(Verdict.DRAW, won))
        assertEquals("상대보다 먼저 도착했어요!", verdictMessage(Verdict.WIN, won.copy(opponentName = null)))
        assertEquals("바람처럼님보다 먼저 도착했어요!", verdictMessage(Verdict.WIN, won.copy(myReach = null)))
    }

    @Test
    fun `이력의 기록 차이는 빠름·느림으로, 이번 주 전적은 무승부가 있으면 더한다`() {
        assertEquals("42초 빠름", gapLabel(java.time.Duration.ofSeconds(-42)))
        assertEquals("1분 12초 느림", gapLabel(java.time.Duration.ofSeconds(72)))
        assertEquals("같은 기록", gapLabel(java.time.Duration.ZERO))
        assertEquals("3W 1L 1D", weeklyLabel(com.a207.runkk.app.domain.battle.BattleResult.Weekly(3, 1, 1)))
        assertEquals("이번 주 3승 1패", weeklySpoken(com.a207.runkk.app.domain.battle.BattleResult.Weekly(3, 1, 0)))
    }

    @Test
    fun `보류 결과를 보이고 다시 불러올 수 있다(FR-108)`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Success(battleResult(pending = true, verdict = null))
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        assertTrue(vm.state.value.result?.pending == true)
        assertNull(vm.state.value.verdict)
        w.battles.resultResult = Outcome.Success(battleResult())
        vm.load(9, force = true)
        runCurrent()
        assertEquals(Verdict.WIN, vm.state.value.verdict)
    }

    @Test
    fun `결과를 불러오지 못하면 오류, 같은 대결은 다시 부르면 다시 읽는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = rejected("BATTLE_NOT_FOUND")
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        assertEquals("대결을 찾지 못했어요", vm.state.value.error)
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
        assertEquals("랜덤", typeLabel(BattleHistory.Type.GHOST))
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
