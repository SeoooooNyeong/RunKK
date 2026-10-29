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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class BattleResultViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val detailCalls = mutableListOf<String>()
    private val details = RunDetailSource { recordId ->
        detailCalls += recordId
        Outcome.Success(recordDetail(recordId))
    }

    @Test
    fun `결과 판정·적립 합계와 2쪽 기록 상세를 보인다(FR-093, D-283)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
        vm.load(9)
        runCurrent()
        val s = vm.state.value
        assertEquals(Verdict.WIN, s.verdict)
        assertEquals(OpponentKind.RANDOM, s.kind)
        assertEquals(52, s.result?.totalRewardPoints)
        assertEquals(listOf("rec-9"), detailCalls, "본인 기록 상세를 한 번 읽는다")
        assertEquals("rec-9", s.detail?.summary?.recordId)
        assertEquals(listOf("result:9"), w.battles.calls, "이번 주 전적·이력을 따로 부르지 않는다")
    }

    @Test
    fun `TC-9688 결과 종류는 서버 type으로 고르고 친구 대결도 대결 결과로 그린다`() {
        fun kindOf(type: BattleHistory.Type?) = BattleResultUiState(result = battleResult().copy(type = type)).kind
        assertEquals(OpponentKind.FRIEND, kindOf(BattleHistory.Type.FRIEND))
        assertEquals(OpponentKind.RANDOM, kindOf(BattleHistory.Type.GHOST))
        assertEquals(OpponentKind.SELF, kindOf(BattleHistory.Type.SELF))
        assertEquals(OpponentKind.CELEBRITY, kindOf(BattleHistory.Type.CELEBRITY))
        assertEquals(OpponentKind.RANDOM, kindOf(null), "옛 서버")
        assertEquals(R.string.battle_result_title, resultTitleRes(OpponentKind.FRIEND))
        assertEquals(R.string.battle_result_title_self, resultTitleRes(OpponentKind.SELF))
        assertEquals(R.string.battle_result_title_celeb, resultTitleRes(OpponentKind.CELEBRITY))
    }

    @Test
    fun `TC-9651 큰 제목은 승리!·패배·무승부, 셀프런 승리는 신기록!`() {
        assertEquals(R.string.battle_result_headline_win, headlineRes(OpponentKind.RANDOM, Verdict.WIN))
        assertEquals(R.string.battle_result_headline_win, headlineRes(OpponentKind.FRIEND, Verdict.WIN))
        assertEquals(R.string.battle_result_headline_new_record, headlineRes(OpponentKind.SELF, Verdict.WIN))
        assertEquals(R.string.battle_result_headline_lose, headlineRes(OpponentKind.CELEBRITY, Verdict.LOSE))
        assertEquals(R.string.battle_result_headline_draw, headlineRes(OpponentKind.RANDOM, Verdict.DRAW))
    }

    @Test
    fun `D-284 평가가 실린 결과는 평가 화면 이동을 한 번만 낸다`() = runTest(main.dispatcher) {
        val w = world()
        val evaluation = com.a207.runkk.app.domain.battle.BattleResult.Evaluation("FIRST_PLACEMENT", "SILVER", 3, null)
        w.battles.resultResult = Outcome.Success(battleResult().copy(evaluation = evaluation))
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
        val events = mutableListOf<BattleResultEvent>()
        backgroundScope.launch { vm.events.collect { events += it } }
        vm.load(9)
        runCurrent()
        vm.load(9, isForced = true)
        runCurrent()
        assertEquals(listOf<BattleResultEvent>(BattleResultEvent.NavigateEvaluation(evaluation)), events)
    }

    @Test
    fun `QA-5 그만두기 뒤 비교표 없이 온 결과는 내 기록이 올라가면 다시 읽는다`() = runTest(main.dispatcher) {
        val w = world()
        w.pendingRuns.runs.value = listOf(pendingBattleRun(9))
        w.battles.resultResult =
            Outcome.Success(battleResult(verdict = "LOSE", recordId = null).copy(rows = emptyList()))
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
        vm.load(9)
        runCurrent()
        assertEquals(emptyList(), vm.state.value.result?.rows)
        w.battles.resultResult = Outcome.Success(battleResult(verdict = "LOSE"))
        w.pendingRuns.remove("rec-9")
        runCurrent()
        assertEquals(4, vm.state.value.result?.rows?.size)
        assertEquals(2, w.battles.calls.count { it == "result:9" })
        w.pendingRuns.runs.value = listOf(pendingBattleRun(10))
        w.pendingRuns.runs.value = emptyList()
        runCurrent()
        assertEquals(2, w.battles.calls.count { it == "result:9" }, "기록이 있는 결과는 다시 읽지 않는다")
    }

    @Test
    fun `QA-5 오프라인이라 읽지 못한 결과는 기록이 올라가면 다시 읽는다`() = runTest(main.dispatcher) {
        val w = world()
        w.pendingRuns.runs.value = listOf(pendingBattleRun(9))
        w.battles.resultResult = Outcome.Failure(AppError.Network)
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
        vm.load(9)
        runCurrent()
        assertNotNull(vm.state.value.error)
        w.battles.resultResult = Outcome.Success(battleResult(verdict = "LOSE"))
        w.pendingRuns.remove("rec-9")
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(Verdict.LOSE, vm.state.value.verdict)
    }

    @Test
    fun `QA-5 네트워크 오류는 몇 초마다 다시 읽고 거부는 다시 읽지 않는다`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Failure(AppError.Network)
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
        vm.load(9)
        runCurrent()
        advanceTimeBy(BattleResultViewModel.OFFLINE_RETRY_MILLIS + 1)
        assertEquals(2, w.battles.calls.count { it == "result:9" })
        w.battles.resultResult = Outcome.Success(battleResult())
        advanceTimeBy(BattleResultViewModel.OFFLINE_RETRY_MILLIS + 1)
        assertEquals(Verdict.WIN, vm.state.value.verdict)
        assertNull(vm.state.value.error)
        advanceTimeBy(BattleResultViewModel.OFFLINE_RETRY_MILLIS * 5)
        assertEquals(3, w.battles.calls.count { it == "result:9" })

        val rejectedWorld = world()
        rejectedWorld.battles.resultResult = rejected("BATTLE_NOT_FOUND")
        val gone =
            BattleResultViewModel(rejectedWorld.battles, rejectedWorld.coordinator, rejectedWorld.uploads, details)
        gone.load(9)
        advanceTimeBy(BattleResultViewModel.OFFLINE_RETRY_MILLIS * 5)
        assertEquals(1, rejectedWorld.battles.calls.count { it == "result:9" })
    }

    @Test
    fun `QA-6 패배는 상대가 실제로 먼저 도착했을 때만 상대와 시간 차를 쓰고 나머지는 대결에서 졌어요`() {
        val lost = battleResult(verdict = "LOSE")
        val generic = uiText(R.string.battle_result_lose)
        // 내 기록 무효·포기·미도착: 내 도달 시간이 없다.
        assertEquals(generic, verdictMessage(OpponentKind.RANDOM, Verdict.LOSE, lost.copy(myReach = null)))
        // 상대가 도착하지 못했다(내가 빨랐는데 무효 등).
        assertEquals(generic, verdictMessage(OpponentKind.RANDOM, Verdict.LOSE, lost.copy(opponentReach = null)))
        // 둘 다 도착했지만 내가 더 빨랐다(판정 사유를 앱이 모른다).
        assertEquals(generic, verdictMessage(OpponentKind.RANDOM, Verdict.LOSE, lost))
        assertEquals(generic, verdictMessage(OpponentKind.RANDOM, Verdict.LOSE, null))
        assertEquals(
            generic,
            verdictMessage(
                OpponentKind.RANDOM,
                Verdict.LOSE,
                lost.copy(
                    opponentName = null,
                    myReach = java.time.Duration.ofSeconds(1_757),
                    opponentReach = java.time.Duration.ofSeconds(1_692)
                )
            )
        )
    }

    @Test
    fun `판정 문구는 상대 표시명과 도달 시간 차를 쓴다(시안 HiBattleResult)`() {
        val won = battleResult()
        assertEquals(
            uiText(R.string.battle_result_win_name_gap, "바람처럼", uiText(R.string.battle_gap_seconds, 42L)),
            verdictMessage(OpponentKind.RANDOM, Verdict.WIN, won)
        )
        assertEquals(
            uiText(
                R.string.battle_result_lose_name_gap,
                "바람처럼",
                uiText(R.string.battle_gap_minutes_seconds, 1L, 5L)
            ),
            verdictMessage(
                OpponentKind.RANDOM,
                Verdict.LOSE,
                won.copy(
                    myReach = java.time.Duration.ofSeconds(1_757),
                    opponentReach = java.time.Duration.ofSeconds(1_692)
                )
            )
        )
        assertEquals(
            uiText(R.string.battle_result_draw_name, "바람처럼"),
            verdictMessage(OpponentKind.RANDOM, Verdict.DRAW, won)
        )
        assertEquals(
            uiText(R.string.battle_result_win),
            verdictMessage(OpponentKind.RANDOM, Verdict.WIN, won.copy(opponentName = null))
        )
        assertEquals(
            uiText(R.string.battle_result_win_name, "바람처럼"),
            verdictMessage(OpponentKind.RANDOM, Verdict.WIN, won.copy(myReach = null))
        )
    }

    @Test
    fun `TC-9647 TC-9648 셀프런·셀럽런 문구는 이름에 님을 붙이지 않고 종류마다 다르다(D-283)`() {
        val self = battleResult().copy(opponentName = "10월 2일의 나", myReach = java.time.Duration.ofSeconds(1_798))
            .copy(opponentReach = java.time.Duration.ofSeconds(1_812))
        val fourteen = uiText(R.string.battle_gap_seconds, 14L)
        assertEquals(
            uiText(R.string.battle_result_win_self, "10월 2일의 나", fourteen),
            verdictMessage(OpponentKind.SELF, Verdict.WIN, self)
        )
        val slower = self.copy(myReach = java.time.Duration.ofSeconds(1_826))
        assertEquals(
            uiText(R.string.battle_result_lose_self, "10월 2일의 나", fourteen),
            verdictMessage(OpponentKind.SELF, Verdict.LOSE, slower)
        )
        val celeb = battleResult().copy(
            opponentName = "김선교 대공",
            myReach = java.time.Duration.ofSeconds(3_640),
            opponentReach = java.time.Duration.ofSeconds(3_500)
        )
        assertEquals(
            uiText(R.string.battle_result_lose_celeb, "김선교 대공", uiText(R.string.battle_gap_minutes_seconds, 2L, 20L)),
            verdictMessage(OpponentKind.CELEBRITY, Verdict.LOSE, celeb)
        )
        assertEquals(
            uiText(R.string.battle_result_win_name_gap, "바람처럼", uiText(R.string.battle_gap_seconds, 42L)),
            verdictMessage(OpponentKind.FRIEND, Verdict.WIN, battleResult())
        )
    }

    @Test
    fun `TC-9650 시간 차 표기는 분까지만 올린다`() {
        assertEquals(uiText(R.string.battle_gap_seconds, 59L), gapText(java.time.Duration.ofSeconds(59)))
        assertEquals(uiText(R.string.battle_gap_minutes, 1L), gapText(java.time.Duration.ofSeconds(60)))
        assertEquals(uiText(R.string.battle_gap_minutes_seconds, 1L, 1L), gapText(java.time.Duration.ofSeconds(61)))
        assertEquals(uiText(R.string.battle_gap_minutes, 60L), gapText(java.time.Duration.ofSeconds(3_600)))
    }

    @Test
    fun `이력의 기록 차이는 빠름·느림으로 보인다`() {
        assertEquals(
            uiText(R.string.battle_gap_faster, uiText(R.string.battle_gap_seconds, 42L)),
            gapLabel(java.time.Duration.ofSeconds(-42))
        )
        assertEquals(
            uiText(R.string.battle_gap_slower, uiText(R.string.battle_gap_minutes_seconds, 1L, 12L)),
            gapLabel(java.time.Duration.ofSeconds(72))
        )
        assertEquals(uiText(R.string.battle_gap_same), gapLabel(java.time.Duration.ZERO))
    }

    @Test
    fun `보류 결과를 보이고 다시 불러올 수 있다(FR-108)`() = runTest(main.dispatcher) {
        val w = world()
        w.battles.resultResult = Outcome.Success(battleResult(pending = true, verdict = null))
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
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
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
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
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
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
        val vm = BattleResultViewModel(w.battles, w.coordinator, w.uploads, details)
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
    fun `TC-9739 대결 탭 - 배틀런 칩은 RANDOM으로 묻고 종류는 배틀런·친구 대결·셀프런·셀럽런으로 적는다(FR-101)`() = runTest(main.dispatcher) {
        val w = world()
        val vm = BattleHistoryViewModel(w.battles)
        runCurrent()
        assertEquals(12, vm.state.value.history?.total)
        vm.onFilter(1)
        runCurrent()
        vm.onFilter(2)
        runCurrent()
        vm.onFilter(3)
        runCurrent()
        assertEquals(
            listOf("history:null", "history:RANDOM", "history:SELF", "history:CELEBRITY"),
            w.battles.calls
        )
        assertEquals(
            listOf(
                R.string.battle_filter_all,
                R.string.battle_history_type_battle,
                R.string.battle_history_type_self,
                R.string.battle_history_type_celeb
            ),
            HISTORY_FILTERS.map { it.first }
        )
        assertEquals(R.string.battle_history_type_battle, typeLabel(BattleHistory.Type.RANDOM))
        assertEquals(R.string.battle_history_type_battle, typeLabel(BattleHistory.Type.GHOST))
        assertEquals(R.string.battle_history_type_friend, typeLabel(BattleHistory.Type.FRIEND))
        assertEquals(R.string.battle_history_type_self, typeLabel(BattleHistory.Type.SELF))
        assertEquals(R.string.battle_history_type_celeb, typeLabel(BattleHistory.Type.CELEBRITY))
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
