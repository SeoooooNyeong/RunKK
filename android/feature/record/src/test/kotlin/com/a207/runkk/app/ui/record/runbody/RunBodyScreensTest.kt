package com.a207.runkk.app.ui.record.runbody

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.Report
import com.a207.runkk.app.domain.runbody.RunBodyAxis
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.domain.runbody.RunBodyTrend
import com.a207.runkk.app.ui.record.summary
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 런바디 화면(피그마 G · 런바디 디자인 · Light): 버튼 동작과 스크린샷(라이트 벌). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunBodyScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 7)

    /** 11월 2일 오전 7:10, 5.2km(G-13 견본)와 지난 기록 하나. */
    private val runs = listOf(
        summary("r3", 20, 5_200).copy(
            startedAt = Instant.parse("2026-11-01T22:10:00Z"),
            elapsed = Duration.ofSeconds(30 * 60 + 10)
        ),
        summary("r2", 28, 10_000).copy(elapsed = Duration.ofSeconds(58 * 60))
    )

    private fun runState(session: RunBodySessionState) = RunBodyUiState(
        today = today,
        zone = zone,
        tab = RunBodyTab.RUN,
        isReportLoading = false,
        runs = runs,
        selectedRecordId = "r3",
        session = session
    )

    private fun monthState(month: RunBodyMonth, at: YearMonth = YearMonth.of(2026, 10)) = RunBodyUiState(
        today = today,
        zone = zone,
        tab = RunBodyTab.MONTH,
        isReportLoading = false,
        month = at,
        monthData = month
    )

    private val coach = RunBodyActions(onCoach = {})

    @Test
    fun `탭을 누르면 고른 탭을 알리고 AI 코치는 연결이 없으면 그리지 않는다`() {
        val tabs = mutableListOf<RunBodyTab>()
        compose.setContent {
            RunkkTheme { RunBodyScreen(runState(RunBodySessionState.Loading), RunBodyActions(onTab = { tabs += it })) }
        }
        compose.onNodeWithText("월간").performClick()
        compose.onNodeWithText("요약").performClick()
        assertEquals(listOf(RunBodyTab.MONTH, RunBodyTab.SUMMARY), tabs)
        compose.onNodeWithText("AI 코치").assertDoesNotExist()
    }

    @Test
    fun `AI 코치는 오른쪽 아래 떠 있는 버튼으로 코치 대화를 연다(G-13)`() {
        var coached = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(runState(RunBodySessionState.Loading), RunBodyActions(onCoach = { coached++ }))
            }
        }
        compose.onNodeWithText("AI 코치").assertHasClickAction().performClick()
        assertEquals(1, coached)
    }

    @Test
    fun `결과가 있으면 가장 높은 점수와 체크할 항목을 보이고 자세히 보기로 상세를 연다(G-13)`() {
        var opened = 0
        var picked = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.Done(sampleResult())),
                    RunBodyActions(onOpenDetail = { opened++ }, onOpenPick = { picked++ })
                )
            }
        }
        compose.onNodeWithText("4개 항목 분석 완료, 러닝 폼 데이터 없음").assertExists()
        compose.onNodeWithText("지구력 84점").assertExists()
        compose.onNodeWithText("페이스 안정성 72점").assertExists()
        compose.onNodeWithText("기록 선택").performClick()
        compose.onNodeWithText("구간과 피드백 자세히 보기").performScrollTo().performClick()
        assertEquals(1, opened)
        assertEquals(1, picked)
    }

    @Test
    fun `분석 중에는 점수 대신 분석 중을 보이고 상세보기를 꺼 둔다(G-13 분석 중)`() {
        compose.setContent { RunkkTheme { RunBodyScreen(runState(RunBodySessionState.Analyzing), RunBodyActions()) } }
        compose.onNodeWithText("분석 중이에요").assertExists()
        compose.onNodeWithText("분석 완료 후 상세보기").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `실패면 상태 다시 확인으로 다시 읽는다(G-19)`() {
        var retried = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(runState(RunBodySessionState.Failed), RunBodyActions(onRetrySession = { retried++ }))
            }
        }
        compose.onNodeWithText("분석하지 못했어요").assertExists()
        compose.onNodeWithText("상태 다시 확인").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `출생연도가 없으면 입력 안내와 프로필 편집 단추를 보인다(MR !91 e2e 1)`() {
        var edits = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.NeedsBirthYear),
                    RunBodyActions(onEditProfile = { edits++ })
                )
            }
        }
        compose.onNodeWithText("출생연도를 입력하면 분석할 수 있어요").assertExists()
        compose.onNodeWithText("분석하지 못했어요").assertDoesNotExist()
        compose.onNodeWithText("프로필 편집").performScrollTo().performClick()
        assertEquals(1, edits)
    }

    @Test
    fun `대기·분석 중에도 다시 확인으로 상태를 읽는다(MR !91 e2e 4)`() {
        var retried = 0
        val session = mutableStateOf<RunBodySessionState>(RunBodySessionState.Pending)
        compose.setContent {
            RunkkTheme { RunBodyScreen(runState(session.value), RunBodyActions(onRetrySession = { retried++ })) }
        }
        compose.onNodeWithText("다시 확인").performScrollTo().performClick()
        session.value = RunBodySessionState.Analyzing
        compose.onNodeWithText("다시 확인").performScrollTo().performClick()
        assertEquals(2, retried)
    }

    @Test
    fun `ai 일시 장애면 실패 대신 잠시 후 다시 시도와 다시 시도 단추를 보인다(MR !91 e2e 5)`() {
        var retried = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.Unavailable),
                    RunBodyActions(onRetrySession = { retried++ })
                )
            }
        }
        compose.onNodeWithText("분석하지 못했어요").assertDoesNotExist()
        compose.onNodeWithText("잠시 후 다시 시도해 주세요").assertExists()
        compose.onNodeWithText("다시 시도").performScrollTo().performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `대기 중이면 날짜와 때, 거리로 러닝을 알린다(G-17)`() {
        compose.setContent { RunkkTheme { RunBodyScreen(runState(RunBodySessionState.Pending), RunBodyActions()) } }
        compose.onNodeWithText("분석을 준비하고 있어요").assertExists()
        compose.onNodeWithText("11월 2일 아침 러닝 5.2km").assertExists()
    }

    @Test
    fun `점수가 하나면 분석 가능한 프로필, 하나도 없으면 데이터 부족이다(G-21·G-31)`() {
        val partial = sampleResult(mapOf(RunBodyAxis.STABILITY to 72))
        compose.setContent {
            RunkkTheme { RunBodyScreen(runState(RunBodySessionState.Done(partial)), RunBodyActions()) }
        }
        compose.onNodeWithText("분석 가능한 러닝 프로필").assertExists()
        compose.onNodeWithText("페이스 안정성 1개 항목 분석 완료").assertExists()
        compose.onNodeWithText("가장 높은 점수").assertDoesNotExist()
    }

    @Test
    fun `러닝 선택은 고른 기록에 선택됨을 붙이고 누른 기록을 알린다(G-30)`() {
        val picked = mutableListOf<String>()
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.Loading).copy(page = RunBodyPage.PICK),
                    RunBodyActions(onPick = { picked += it.recordId })
                )
            }
        }
        // 피그마 "5.2km · 선택됨"은 가운뎃점 대신 메타 줄로 그리고 한 번에 읽는다([임시:D-성-10])
        compose.onNodeWithContentDescription("5.2km, 선택됨").assertIsSelected()
        compose.onNodeWithContentDescription("10.0km, 58:00").assertIsNotSelected()
        compose.onNodeWithContentDescription("10.0km, 58:00").performClick()
        assertEquals(listOf("r2"), picked)
    }

    @Test
    fun `지난달은 이 달로 부르고 주간 거리와 부하 거리를 피그마 표기로 보인다(G-22)`() {
        compose.setContent { RunkkTheme { RunBodyScreen(monthState(sampleMonth()), RunBodyActions()) } }
        compose.onNodeWithText("18.4 km").performScrollTo().assertExists()
        compose.onNodeWithText("1.14 적정").assertExists()
        compose.onNodeWithText("이 달 개인 최고").performScrollTo().assertExists()
        compose.onNodeWithText("이번 달 개인 최고").assertDoesNotExist()
        compose.onNodeWithText("20.2km", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun `첫 러닝 달이면 이전 달로 가지 않고 이전 달 보기도 없다(G-32)`() {
        val empty = sampleMonth(YearMonth.of(2026, 11)).copy(sessionCount = 0, averages = emptyMap())
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    monthState(empty, YearMonth.of(2026, 11)).copy(
                        trend = sampleTrend().copy(firstRunAt = Instant.parse("2026-11-02T22:00:00Z"))
                    ),
                    RunBodyActions()
                )
            }
        }
        compose.onNodeWithText("이번 달의 러닝을\n아직 분석하지 않았어요").assertExists()
        compose.onNodeWithContentDescription("이전 달").assertIsNotEnabled()
        compose.onNodeWithText("이전 달 보기").assertDoesNotExist()
    }

    @Test
    fun `상세 구간 기록은 한 줄에 페이스와 심박을, 피드백은 라벨과 본문을 한 줄에 보인다(G-20)`() {
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.Done(sampleResult())).copy(page = RunBodyPage.DETAIL),
                    RunBodyActions()
                )
            }
        }
        compose.onNodeWithContentDescription("11월 2일 오전 7:10, 5.2km").assertExists()
        compose.onNodeWithText("00:30:10", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("구간, 페이스, 평균 심박").performScrollTo().assertExists()
        compose.onNodeWithText("5′42″/km, 148 bpm", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("마지막 0.2 km", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("좋았던 점 전반과 후반 페이스 차이가 작아요.").performScrollTo().assertExists()
        compose.onNodeWithText("확인 근거 전반 5′44″/km → 후반 5′52″/km").performScrollTo().assertExists()
        compose.onNodeWithText("5.2 km / 5.0 km", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun `분석된 러닝이 없는 달은 이전 달 보기를 주고 개인 최고·주간 거리는 그리지 않는다(G-32)`() {
        var previous = 0
        val empty = sampleMonth(YearMonth.of(2026, 11)).copy(
            sessionCount = 0,
            averages = emptyMap(),
            bests = RunBodyMonth.Bests(null, null, null, null),
            weeks = emptyList()
        )
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    monthState(empty, YearMonth.of(2026, 11)),
                    RunBodyActions(onPreviousMonth = {
                        previous++
                    })
                )
            }
        }
        compose.onNodeWithText("이번 달 개인 최고").assertDoesNotExist()
        compose.onNodeWithText("이전 달 보기").performClick()
        assertEquals(1, previous)
    }

    /** 피그마 G-14 주간 러닝 기록: 11/2(월) 4.2km, 11/4(수) 5km, 11/6(금) 4km, 11/7(토) 5.2km = 18.4km, 4회, 1:47:20. */
    private val weekRuns = listOf(
        Triple("w1", 2, 4_200L) to 24 * 60 + 20L,
        Triple("w2", 4, 5_000L) to 29 * 60L,
        Triple("w3", 6, 4_000L) to 23 * 60 + 10L,
        Triple("w4", 7, 5_200L) to 30 * 60 + 50L
    ).map { (run, seconds) ->
        summary(run.first, 1, run.third).copy(
            startedAt = LocalDate.of(2026, 11, run.second).atTime(7, 10).atZone(zone).toInstant(),
            elapsed = Duration.ofSeconds(seconds)
        )
    }

    private fun summaryState(trend: RunBodyTrend?, comment: String? = null) = RunBodyUiState(
        today = today,
        zone = zone,
        isReportLoading = false,
        report = Report("1~2회 평균 점수 33.0 · 1회 거리 +0 m", comment, null),
        isTrendLoading = false,
        trend = trend,
        isWeekLoading = false,
        weekRuns = weekRuns
    )

    @Test
    fun `요약은 주간 러닝 기록·최근 10회 평균·러닝 코멘트를 보이고 개발용 요약 줄은 없다(G-14)`() {
        var previous = 0
        var next = 0
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    summaryState(sampleTrend(), comment = "평균 페이스가 14초 빨라졌어요."),
                    RunBodyActions(onPreviousWeek = { previous++ }, onNextWeek = { next++ })
                )
            }
        }
        compose.onNodeWithText("주간 러닝 기록").performScrollTo().assertExists()
        compose.onNodeWithText("11월 1주").assertExists()
        compose.onNodeWithText("18.4", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4회 달림").assertExists()
        compose.onNodeWithText("1:47:20").assertExists()
        compose.onNodeWithContentDescription("월요일 4.2km").assertExists()
        compose.onNodeWithContentDescription("수요일 5km").assertExists()
        compose.onNodeWithContentDescription("화요일 러닝 없음").assertExists()
        compose.onNodeWithContentDescription("다음 주").assertIsNotEnabled()
        compose.onNodeWithContentDescription("이전 주").performClick()
        assertEquals(1, previous)
        assertEquals(0, next)
        compose.onNodeWithText("평균 거리", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithText("4.6", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("149", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("러닝 코멘트", useUnmergedTree = true).performScrollTo().assertExists()
        compose.onNodeWithText("평균 페이스가 14초 빨라졌어요.", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("이번 주 요약").assertDoesNotExist()
        compose.onNodeWithText("평균 점수", substring = true).assertDoesNotExist()
    }

    @Test
    fun `10회 전이면 남은 횟수만 보이고 최근 10회 평균은 그리지 않는다(G-33)`() {
        compose.setContent {
            RunkkTheme { RunBodyScreen(summaryState(RunBodyTrend(2, 0, 8, null, null, null, null)), RunBodyActions()) }
        }
        compose.onNodeWithText("러닝을 8번 더 하면 최근 10회 변화를 보여 드려요").assertExists()
        compose.onNodeWithText("주간").assertDoesNotExist()
        compose.onNodeWithText("평균 거리", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("코멘트 준비 중", useUnmergedTree = true).performScrollTo().assertExists()
    }

    @Test
    fun `요약은 최근 10회 변화의 회차와 페이스·같은 심박·주간 빈도·러닝 유형을 보이고 주간·월간 토글은 없다(G-14)`() {
        compose.setContent { RunkkTheme { RunBodyScreen(summaryState(sampleTrend()), RunBodyActions()) } }
        compose.onNodeWithText("2회차").assertExists()
        compose.onNodeWithText("11~20번째 러닝").assertExists()
        compose.onNodeWithText("14초 단축").assertExists()
        compose.onNodeWithText("12초/km").assertExists()
        compose.onNodeWithText("주간 러닝 빈도").assertExists()
        compose.onNodeWithText("2.1 → 2.8회").assertExists()
        compose.onNodeWithText("0.7회 늘었어요").assertExists()
        compose.onNodeWithText("출근도장 거북이").performScrollTo().assertExists()
        // 피그마 G-14에는 토글이 없다([임시:D-성-17]). "월간"은 맨 위 탭 하나뿐이다
        compose.onNodeWithText("주간").assertDoesNotExist()
        assertEquals(1, compose.onAllNodesWithText("월간").fetchSemanticsNodes().size)
        compose.onNodeWithText("월간 러닝 빈도").assertDoesNotExist()
    }

    @Test
    fun `러닝 선택은 점수가 있는 기록에 러닝 점수와 변화를 보인다(G-30)`() {
        compose.setContent {
            RunkkTheme {
                RunBodyScreen(
                    runState(RunBodySessionState.Loading).copy(
                        page = RunBodyPage.PICK,
                        scores = mapOf("r3" to SAMPLE_RUN_SCORES.first())
                    ),
                    RunBodyActions()
                )
            }
        }
        compose.onNodeWithContentDescription("러닝 점수 78점, 6점 올랐어요").assertExists()
        compose.onNodeWithText("러닝 점수", useUnmergedTree = true).assertExists()
    }

    // ---------- 스크린샷(라이트 벌, 피그마 대응) ----------

    @Test
    fun summaryShot() = compose.captureRunkkScreen("G14_runbody_summary", isDarkTheme = false) {
        RunBodyScreen(summaryState(sampleTrend(), comment = FIGMA_COMMENT), coach)
    }

    /** [content]를 라이트 벌로 그리고 맨 아래까지 올려 찍는다(화면 아래쪽 카드 확인용). */
    private fun captureBottom(name: String, content: @Composable () -> Unit) {
        compose.setContent { RunkkTheme(darkTheme = false, reduceMotion = true, content = content) }
        repeat(BOTTOM_SWIPES) { compose.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    /** G-14 아래쪽: 주간 러닝 기록, 최근 10회 평균, 러닝 코멘트. */
    @Test
    fun summaryLowerShot() = captureBottom("G14_runbody_summary_lower") {
        RunBodyScreen(summaryState(sampleTrend(), comment = FIGMA_COMMENT), coach)
    }

    /** G-20 아래쪽: 구간 기록 한 줄, 피드백·코칭 라벨, 이전 러닝과 비교. */
    @Test
    fun detailLowerShot() = captureBottom("G20_runbody_detail_lower") {
        RunBodyScreen(runState(RunBodySessionState.Done(sampleResult())).copy(page = RunBodyPage.DETAIL), coach)
    }

    /** G-22 아래쪽(지난달): 이 달 개인 최고, 주간 거리 막대. */
    @Test
    fun monthLowerShot() = captureBottom("G22_runbody_month_lower") {
        RunBodyScreen(monthState(sampleMonth()), coach)
    }

    /** G-33: 10회 전(토글·평균 없음), 코멘트 준비 중. */
    @Test
    fun summaryPendingShot() = compose.captureRunkkScreen("G33_runbody_summary_pending", isDarkTheme = false) {
        RunBodyScreen(summaryState(RunBodyTrend(2, 0, 8, null, null, null, null)), coach)
    }

    @Test
    fun runResultShot() = compose.captureRunkkScreen("G13_runbody_run", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Done(sampleResult())), coach)
    }

    @Test
    fun runPendingShot() = compose.captureRunkkScreen("G17_runbody_pending", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Pending), coach)
    }

    @Test
    fun runAnalyzingShot() = compose.captureRunkkScreen("G13_runbody_analyzing", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Analyzing), coach)
    }

    @Test
    fun runFailedShot() = compose.captureRunkkScreen("G19_runbody_failed", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Failed), coach)
    }

    @Test
    fun runPartialShot() = compose.captureRunkkScreen("G21_runbody_partial", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Done(sampleResult(mapOf(RunBodyAxis.STABILITY to 72)))), coach)
    }

    @Test
    fun runEmptyShot() = compose.captureRunkkScreen("G31_runbody_no_data", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Done(sampleResult(emptyMap()))), coach)
    }

    @Test
    fun pickShot() = compose.captureRunkkScreen("G30_runbody_pick", isDarkTheme = false) {
        RunBodyScreen(
            runState(RunBodySessionState.Loading).copy(
                page = RunBodyPage.PICK,
                scores = SAMPLE_RUN_SCORES.associateBy { it.recordId }
            ),
            coach
        )
    }

    @Test
    fun detailShot() = compose.captureRunkkScreen("G20_runbody_detail", isDarkTheme = false) {
        RunBodyScreen(runState(RunBodySessionState.Done(sampleResult())).copy(page = RunBodyPage.DETAIL), coach)
    }

    @Test
    fun detailFullAxesDarkShot() = compose.captureRunkkScreen("G20_runbody_detail_dark") {
        val full = sampleResult(SAMPLE_SCORES + (RunBodyAxis.FORM to 66))
        RunBodyScreen(runState(RunBodySessionState.Done(full)).copy(page = RunBodyPage.DETAIL), coach)
    }

    @Test
    fun monthShot() = compose.captureRunkkScreen("G22_runbody_month", isDarkTheme = false) {
        RunBodyScreen(monthState(sampleMonth()), coach)
    }

    @Test
    fun monthPreparingShot() = compose.captureRunkkScreen("G23_runbody_month_preparing", isDarkTheme = false) {
        val month = sampleMonth().copy(
            baseline = RunBodyMonth.Baseline(
                isAvailable = false,
                sessionCount = 2,
                needs = 1,
                pace = null,
                averageHeartRate = null
            ),
            load = RunBodyMonth.Load(
                isAvailable = false,
                ratio = null,
                level = null,
                acute = Distance(5_200),
                chronicWeekly = Distance(1_300),
                availableFrom = today.plusDays(14)
            )
        )
        RunBodyScreen(monthState(month), coach)
    }

    private companion object {
        const val BOTTOM_SWIPES = 5
        const val FIGMA_COMMENT = "평균 페이스가 14초 빨라졌어요.\n주간 러닝 빈도도 0.7회 늘었어요."
    }

    @Test
    fun monthEmptyShot() = compose.captureRunkkScreen("G32_runbody_month_empty", isDarkTheme = false) {
        val empty = sampleMonth(YearMonth.of(2026, 11)).copy(
            sessionCount = 0,
            averages = emptyMap(),
            bests = RunBodyMonth.Bests(null, null, null, null),
            weeks = emptyList(),
            baseline = RunBodyMonth.Baseline(
                isAvailable = true,
                sessionCount = 20,
                needs = 0,
                pace = Pace(348),
                averageHeartRate = 149
            )
        )
        RunBodyScreen(monthState(empty, YearMonth.of(2026, 11)), coach)
    }
}
