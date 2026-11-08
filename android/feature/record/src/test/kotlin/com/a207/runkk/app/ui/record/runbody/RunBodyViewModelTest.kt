package com.a207.runkk.app.ui.record.runbody

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.runbody.RunBodyRepository
import com.a207.runkk.app.domain.runbody.RunBodyStatus
import com.a207.runkk.app.ui.record.FakeRecordRepository
import com.a207.runkk.app.ui.record.MainDispatcherRule
import com.a207.runkk.app.ui.record.R
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

/** 런바디 화면 상태(S15P31A207-229~232, FR-066·FR-266·FR-267). 오늘은 2026-11-07(토) 한국 시각이다. */
class RunBodyViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), zone)
    private val records = FakeRecordRepository()
    private val runBody = FakeRunBodyRepository()

    private fun viewModel() = RunBodyViewModel(records, runBody, clock, zone)

    private fun advance(millis: Long) {
        main.dispatcher.scheduler.advanceTimeBy(millis)
        main.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `처음에는 요약 탭이고 리포트와 최근 10회 변화만 읽으며 코멘트가 없으면 준비 중이다(FR-066, G-14, G-33)`() {
        val vm = viewModel()

        assertEquals(RunBodyTab.SUMMARY, vm.state.value.tab)
        assertFalse(vm.state.value.isReportLoading)
        assertEquals(UiText.Res(R.string.record_report_comment_pending), vm.state.value.commentText)
        assertFalse(vm.state.value.isTrendLoading)
        assertEquals(sampleTrend(), vm.state.value.trend)
        assertEquals(RunBodyPeriod.WEEK, vm.state.value.period)
        assertEquals(1, runBody.trendCalls)
        assertEquals(emptyList(), records.runsCalls)
        assertEquals(emptyList(), runBody.sessionCalls)
        assertEquals(0, runBody.scoresCalls)
    }

    @Test
    fun `앱 시계가 UTC여도 오늘과 시각은 기기 시간대로 셈한다(MR !91 e2e 2)`() {
        // 한국 2026-11-08 01:30 = UTC 2026-11-07 16:30. 앱 Clock은 Clock.systemUTC()다(DeviceBindings)
        val utc = Clock.fixed(Instant.parse("2026-11-07T16:30:00Z"), java.time.ZoneOffset.UTC)
        val vm = RunBodyViewModel(records, runBody, utc, zone)

        assertEquals(zone, vm.state.value.zone)
        assertEquals(LocalDate.of(2026, 11, 8), vm.state.value.today)
        assertEquals(YearMonth.of(2026, 11), vm.state.value.month)
        vm.onTab(RunBodyTab.RUN)
        assertEquals(LocalDate.of(2026, 11, 8), records.runsCalls.single().third)
        assertEquals(LocalDate.of(2026, 11, 8), vm.state.value.today)
    }

    @Test
    fun `러닝별 탭을 처음 고르면 최근 90일 본인 기록을 읽고 가장 최근 기록의 분석을 읽는다`() {
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        vm.onTab(RunBodyTab.SUMMARY)
        vm.onTab(RunBodyTab.RUN)

        assertEquals(
            listOf(Triple(RecordRepository.SELF, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 11, 7))),
            records.runsCalls
        )
        assertEquals("r3", vm.state.value.selectedRecordId)
        assertEquals(listOf("r3"), runBody.sessionCalls)
        assertIs<RunBodySessionState.Done>(vm.state.value.session)
    }

    @Test
    fun `대기·분석 중이면 5초마다 다시 읽어 완료되면 같은 칸에 결과를 보인다(G-17 → G-13)`() {
        runBody.reply(
            "r3",
            waiting("r3", RunBodyStatus.PENDING),
            waiting("r3", RunBodyStatus.ANALYZING),
            Outcome.Success(done("r3"))
        )
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        assertEquals(RunBodySessionState.Pending, vm.state.value.session)
        advance(RunBodyViewModel.POLL_MILLIS)
        assertEquals(RunBodySessionState.Analyzing, vm.state.value.session)
        advance(RunBodyViewModel.POLL_MILLIS)
        assertIs<RunBodySessionState.Done>(vm.state.value.session)
        advance(RunBodyViewModel.POLL_MILLIS * 3)
        assertEquals(3, runBody.sessionCalls.size)
    }

    @Test
    fun `60초(12번) 동안 끝나지 않으면 다시 읽기를 멈추고 분석 중을 그대로 보인다(NFR-053)`() {
        runBody.reply("r3", waiting("r3", RunBodyStatus.ANALYZING))
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        advance(RunBodyViewModel.POLL_MILLIS * 20)

        assertEquals(1 + RunBodyViewModel.MAX_POLLS, runBody.sessionCalls.size)
        assertEquals(RunBodySessionState.Analyzing, vm.state.value.session)
    }

    @Test
    fun `실패와 읽기 오류는 분석하지 못했어요, 상태 다시 확인으로 다시 읽는다(G-19)`() {
        runBody.reply(
            "r3",
            waiting("r3", RunBodyStatus.FAILED),
            Outcome.Failure(AppError.Server(503)),
            Outcome.Success(done("r3"))
        )
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        assertEquals(RunBodySessionState.Failed, vm.state.value.session)
        vm.onRetrySession()
        assertEquals(RunBodySessionState.Failed, vm.state.value.session)
        vm.onRetrySession()
        assertIs<RunBodySessionState.Done>(vm.state.value.session)
    }

    @Test
    fun `분석 작업이 없는 기록(404)은 데이터 부족이고 다시 읽지 않는다(G-31)`() {
        runBody.reply("r3", Outcome.Failure(AppError.Rejected(RunBodyRepository.NOT_FOUND)))
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        advance(RunBodyViewModel.POLL_MILLIS * 2)

        assertEquals(RunBodySessionState.NotAnalyzable, vm.state.value.session)
        assertEquals(1, runBody.sessionCalls.size)
        vm.onOpenDetail()
        assertEquals(RunBodyPage.MAIN, vm.state.value.page)
    }

    @Test
    fun `러닝 선택에서 다른 기록을 고르면 탭 화면으로 돌아와 그 기록의 분석을 읽고, 뒤로는 탭 화면으로 온다(G-30)`() {
        val vm = viewModel()
        vm.onTab(RunBodyTab.RUN)

        vm.onOpenPick()
        assertEquals(RunBodyPage.PICK, vm.state.value.page)
        assertTrue(vm.onBack())
        assertEquals(RunBodyPage.MAIN, vm.state.value.page)
        assertFalse(vm.onBack())

        vm.onOpenPick()
        vm.onPick(vm.state.value.runs.first { it.recordId == "r2" })
        assertEquals(RunBodyPage.MAIN, vm.state.value.page)
        assertEquals("r2", vm.state.value.selectedRecordId)
        assertEquals(listOf("r3", "r2"), runBody.sessionCalls)
        vm.onPick(vm.state.value.runs.first { it.recordId == "r2" })
        assertEquals(listOf("r3", "r2"), runBody.sessionCalls)
    }

    @Test
    fun `상세는 결과가 있을 때만 열고 뒤로면 러닝별 탭으로 돌아온다(G-20)`() {
        runBody.reply("r3", waiting("r3", RunBodyStatus.ANALYZING), Outcome.Success(done("r3")))
        val vm = viewModel()
        vm.onTab(RunBodyTab.RUN)

        vm.onOpenDetail()
        assertEquals(RunBodyPage.MAIN, vm.state.value.page)
        advance(RunBodyViewModel.POLL_MILLIS)
        vm.onOpenDetail()
        assertEquals(RunBodyPage.DETAIL, vm.state.value.page)
        assertTrue(vm.onBack())
        assertEquals(RunBodyTab.RUN, vm.state.value.tab)
    }

    @Test
    fun `월간 탭은 이번 달부터 읽고 이전 달로 넘기며 이번 달 뒤로는 가지 않는다(G-22·G-32)`() {
        val vm = viewModel()

        vm.onTab(RunBodyTab.MONTH)
        assertFalse(vm.state.value.canGoNextMonth)
        vm.onNextMonth()
        vm.onPreviousMonth()

        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10)), runBody.monthCalls)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.monthData?.month)
        assertTrue(vm.state.value.canGoNextMonth)
    }

    @Test
    fun `월간을 읽지 못하면 오류와 다시 시도를 보인다`() {
        runBody.monthOf = { Outcome.Failure(AppError.Network) }
        val vm = viewModel()

        vm.onTab(RunBodyTab.MONTH)

        assertEquals(null, vm.state.value.monthData)
        assertTrue(vm.state.value.monthError != null)
        runBody.monthOf = { Outcome.Success(sampleMonth(it)) }
        vm.refreshMonth()
        assertEquals(YearMonth.of(2026, 11), vm.state.value.monthData?.month)
    }

    @Test
    fun `주간·월간 토글은 다시 읽지 않고 기간만 바꾸며 최근 10회 변화를 못 읽으면 다시 시도로 읽는다(G-14)`() {
        runBody.trendReply = Outcome.Failure(AppError.Network)
        val vm = viewModel()

        assertEquals(null, vm.state.value.trend)
        assertTrue(vm.state.value.trendError != null)
        vm.onPeriod(RunBodyPeriod.MONTH)
        assertEquals(RunBodyPeriod.MONTH, vm.state.value.period)
        assertEquals(1, runBody.trendCalls)

        runBody.trendReply = Outcome.Success(sampleTrend())
        vm.refreshTrend()
        assertEquals(sampleTrend(), vm.state.value.trend)
        assertEquals(null, vm.state.value.trendError)
    }

    @Test
    fun `러닝별 탭은 기록과 함께 러닝 점수를 읽고, 못 읽어도 목록은 그대로다(G-30)`() {
        val vm = viewModel()
        vm.onTab(RunBodyTab.RUN)

        assertEquals(1, runBody.scoresCalls)
        assertEquals(78, vm.state.value.scores["r3"]?.score)
        assertEquals(6, vm.state.value.scores["r3"]?.change)

        runBody.scoresReply = Outcome.Failure(AppError.Network)
        vm.refreshRuns()
        assertEquals(2, runBody.scoresCalls)
        assertEquals(78, vm.state.value.scores["r3"]?.score)
        assertTrue(vm.state.value.runs.isNotEmpty())
    }

    @Test
    fun `기다리던 분석이 끝나면 러닝 점수를 다시 읽는다(G-30)`() {
        runBody.reply("r3", waiting("r3", RunBodyStatus.ANALYZING), Outcome.Success(done("r3")))
        val vm = viewModel()

        vm.onTab(RunBodyTab.RUN)
        assertEquals(1, runBody.scoresCalls)
        advance(RunBodyViewModel.POLL_MILLIS)
        assertIs<RunBodySessionState.Done>(vm.state.value.session)
        assertEquals(2, runBody.scoresCalls)
    }
}
