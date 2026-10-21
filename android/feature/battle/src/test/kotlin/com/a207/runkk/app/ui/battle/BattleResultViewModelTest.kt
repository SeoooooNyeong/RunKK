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
    fun `결과 비교표·판정·리워드와 전적을 보인다(FR-093)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = BattleResultViewModel(w.battles, w.coordinator)
        vm.load(9)
        runCurrent()
        val s = vm.state.value
        assertEquals(Verdict.WIN, s.verdict)
        assertEquals(4, s.result?.rows?.size)
        assertEquals(70, s.result?.rewardPoints)
        assertEquals(7, s.wins)
        assertEquals(5, s.losses)
        assertTrue("result:9" in w.battles.calls && "history:null" in w.battles.calls)
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
    fun `함께 달린 기록을 불러오지 못하면 오류`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.historyResult = Outcome.Failure(AppError.Network)
        val vm = BattleHistoryViewModel(w.battles)
        runCurrent()
        assertNotNull(vm.state.value.error)
    }
}
