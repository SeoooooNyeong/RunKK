@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.a207.runkk.app.ui.battle

import app.cash.turbine.test
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleRunState
import com.a207.runkk.app.domain.battle.RecordBattleCandidates
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
    fun `나와의 싸움 - 거리별 최고 기록을 보이고 처음엔 고른 거리가 없으며 기록 없는 거리는 고를 수 없다(FR-096, D-288)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = SelfBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        val s = vm.state.value
        assertFalse(s.isLoading)
        assertNull(s.selected, "카드를 눌러야 기록 깨러 가기가 보인다")
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
            assertEquals(
                Routes.BattleRunning(
                    42,
                    "과거의 나",
                    5_000,
                    OpponentKind.SELF.name,
                    opponentRecordSeconds = 1_650,
                    opponentRecordedOnEpochDay = java.time.LocalDate.of(2026, 10, 12).toEpochDay()
                ),
                event.key,
                "카운트다운이 과거의 나 기록·날짜를 그린다(D-264)"
            )
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
    fun `셀럽런 시작 - 내 최고 기록을 카운트다운 인자로 넘긴다(D-264, 없으면 첫 도전)`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.celebResult = Outcome.Success(
            RecordBattleCandidates(listOf(celebCandidates[0].copy(myBest = java.time.Duration.ofSeconds(3_730))))
        )
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.events.test {
            vm.onSelect(vm.state.value.candidates[0])
            vm.onStart()
            runCurrent()
            val event = assertIs<RecordBattleEvent.Start>(awaitItem())
            assertEquals(3_730L, event.key.myBestSeconds)
            assertEquals(OpponentKind.CELEBRITY.name, event.key.opponentKind)
        }
        advanceTimeBy(3_100)
    }

    @Test
    fun `유명인 - 거리로 거르면 그 거리로 다시 조회하고 그 거리에 없는 선택은 푼다(FR-097)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onSelect(vm.state.value.candidates.first())
        vm.onFilter(3)
        assertNull(vm.state.value.selected, "10km 셀럽은 하프 목록에 없다")
        runCurrent()
        assertEquals(listOf("celeb:null", "celeb:21097"), w.battles.calls)
        assertEquals(3, vm.state.value.filterIndex)
    }

    @Test
    fun `유명인 - 처음엔 고른 셀럽이 없고 카드를 고른 뒤 대결하기로 시작한다(D-288)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        assertNull(vm.state.value.selected, "카드를 눌러야 대결하기가 보인다")
        vm.onSelect(vm.state.value.candidates.first())
        assertFalse(w.battles.calls.any { it.startsWith("startCeleb") }, "고르기만 해서는 시작하지 않는다")
        vm.events.test {
            vm.onStart()
            runCurrent()
            val event = assertIs<RecordBattleEvent.Start>(awaitItem())
            assertEquals(OpponentKind.CELEBRITY.name, event.key.opponentKind)
        }
        assertTrue("startCeleb:7" in w.battles.calls)
    }

    @Test
    fun `유명인 기록이 지워졌으면 안내하고 목록을 다시 불러온다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.startResult = rejected("CELEBRITY_REMOVED")
        val vm = CelebBattleViewModel(w.battles, w.coordinator)
        runCurrent()
        vm.onSelect(vm.state.value.candidates.first())
        vm.onStart()
        runCurrent()
        assertEquals(UiText.Res(R.string.battle_error_celebrity_removed), vm.state.value.message)
        assertNull(vm.state.value.selected)
        assertEquals(2, w.battles.calls.count { it.startsWith("celeb") })
        assertIs<BattleRunState.Idle>(w.coordinator.state.value)
    }

    @Test
    fun `출처 줄은 서버 sourceLabel을 그대로 쓰고, 없으면 줄을 두지 않는다(FR-100, D-288)`() {
        val isEstimated = CandidateUi.of(celebCandidates[1])
        assertEquals(
            UiText.Plain("공개 기록 기준 · 2025 서울마라톤"),
            sourceLine(CandidateUi.of(celebCandidates[0]))
        )
        assertNull(sourceLine(isEstimated.copy(sourceLabel = null, isEstimated = false)))
        assertEquals(UiText.Res(R.string.battle_distance_full), distanceLabel(isEstimated.distance))
    }
}
