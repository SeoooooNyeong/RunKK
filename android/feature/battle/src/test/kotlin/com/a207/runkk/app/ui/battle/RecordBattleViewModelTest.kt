@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.ui.battle.BattleWorld.Companion.world
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RecordBattleViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun `나와의 싸움 - 거리별 최고 기록을 보이고 기록 없는 거리는 고를 수 없다(FR-096)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = SelfBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(4, s.candidates.size)
        assertEquals(Distance(5_000), s.candidates[1].distance)
        assertNotNull(s.candidates[1].averagePace)
        vm.onSelect(s.candidates[2])
        assertNull(vm.state.value.selected, "10K 기록이 없으면 고를 수 없다")
        vm.onSelect(s.candidates[1])
        assertEquals(Distance(5_000), vm.state.value.selected?.distance)
    }

    @Test
    fun `나와의 싸움 시작 - 조정자에 시작 정보를 넘기고 진행 화면 키를 보낸다`() = runTest(main.dispatcher) {
        val w = world()
        val vm = SelfBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onSelect(vm.state.value.candidates[1])
        vm.events.test {
            vm.onStart()
            runCurrent()
            val event = assertIs<RecordBattleEvent.Start>(awaitItem())
            assertEquals(Routes.BattleRunning(42, "과거의 나", 5_000, OpponentKind.SELF.name), event.key)
        }
        assertTrue("startSelf:5000" in w.battles.calls)
        assertIs<BattleRunState.Countdown>(w.coordinator.state.value)
        advanceTimeBy(3_100)
        runCurrent()
        assertIs<BattleRunState.Running>(w.coordinator.state.value)
        assertTrue("started:42" in w.battles.calls, "3초 뒤 시작 보고(markStarted)")
    }

    @Test
    fun `후보를 불러오지 못하면 오류를 보이고 다시 시도할 수 있다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.selfResult = Outcome.Failure(AppError.Network)
        val vm = SelfBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        assertNotNull(vm.state.value.error)
        w.battles.selfResult = Outcome.Success(com.a207.runkk.app.domain.battle.RecordBattleCandidates(selfCandidates))
        vm.load()
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(4, vm.state.value.candidates.size)
    }

    @Test
    fun `유명인 - 거리로 거르면 그 거리로 다시 조회한다(FR-097)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onFilter(3)
        runCurrent()
        assertEquals(listOf("celeb:null", "celeb:21097"), w.battles.calls)
        assertEquals(3, vm.state.value.filterIndex)
    }

    @Test
    fun `유명인 - 고르면 확인을 받은 뒤 시작한다`() = runTest(main.dispatcher) {
        val w = world()
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onPick(vm.state.value.candidates.first())
        assertTrue(vm.state.value.confirming)
        assertFalse(w.battles.calls.any { it.startsWith("startCeleb") }, "확인 전에는 시작하지 않는다")
        vm.events.test {
            vm.onStart()
            runCurrent()
            val event = assertIs<RecordBattleEvent.Start>(awaitItem())
            assertEquals(OpponentKind.CELEBRITY.name, event.key.opponentKind)
        }
        assertTrue("startCeleb:7" in w.battles.calls)
        assertFalse(vm.state.value.confirming)
    }

    @Test
    fun `유명인 기록이 지워졌으면 안내하고 목록을 다시 불러온다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.startResult = rejected("CELEBRITY_REMOVED")
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onPick(vm.state.value.candidates.first())
        vm.onStart()
        runCurrent()
        assertEquals("고를 수 없는 기록이에요. 목록을 새로 불러왔어요", vm.state.value.message)
        assertNull(vm.state.value.selected)
        assertEquals(2, w.battles.calls.count { it.startsWith("celeb") })
        assertIs<BattleRunState.Idle>(w.coordinator.state.value)
    }

    @Test
    fun `출처 줄에 공개 기록 기준과 추정 안내를 붙인다(FR-100)`() {
        val estimated = CandidateUi.of(celebCandidates[1])
        assertEquals("공개 기록 기준 · 2024 춘천마라톤 · 구간 기록이 없으면 평균 페이스로 추정", sourceLine(estimated))
        assertEquals("풀", distanceLabel(estimated.distance))
    }
}
