package com.a207.runkk.app.ui.record

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Report
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 성장·리포트·랭킹·코치 화면: 탭·버튼 동작과 스크린샷. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class AnalysisScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val week = LocalDate.of(2026, 10, 19)
    private val coach = CoachUiState(
        weekStart = week,
        today = week.plusDays(2),
        isLoading = false,
        progress = PlanProgress(
            listOf(
                PlanProgress.Day(week, Distance(5_000), Distance(5_200), null),
                PlanProgress.Day(week.plusDays(1), Distance(5_000), Distance(3_000), Distance(2_000)),
                PlanProgress.Day(week.plusDays(2), Distance(7_000), Distance.ZERO, null)
            )
        )
    )

    @Test
    fun `TC-9742 TC-9698 랭킹은 크루가 닫혀 있으면 개인만, 주간·월간 칩과 누적 유효 거리 기준을 보인다`() {
        val calls = mutableListOf<Any>()
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RankingScreen(
                        RankingUiState(isLoading = false, ranking = sampleRanking),
                        onScope = { calls += it },
                        onPeriod = { calls += it },
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("크루").assertDoesNotExist()
        compose.onNodeWithText("개인").assertDoesNotExist()
        compose.onNodeWithText("누적 유효 거리").assertExists()
        compose.onNodeWithText("월간").performClick()
        assertEquals(listOf<Any>(AggregationPeriod.MONTHLY), calls)
    }

    @Test
    fun `TC-9698 랭킹은 크루가 열려 있으면 개인·크루 분절을 보인다(2차 배포)`() {
        val calls = mutableListOf<Any>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RankingScreen(
                    RankingUiState(isLoading = false, ranking = sampleRanking),
                    onScope = { calls += it },
                    onPeriod = {},
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithText("크루").performClick()
        assertEquals(listOf<Any>(RankingScope.CREW), calls)
    }

    @Test
    fun `코치에서 오늘 계획 거리를 고른다`() {
        val plans = mutableListOf<Distance>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CoachScreen(coach, CoachActions(onTodayPlan = { plans += it }))
            }
        }
        compose.onNodeWithText("보충 거리 2.00km 추천").assertExists()
        compose.onNodeWithText("5km").performClick()
        assertEquals(listOf(Distance(5_000)), plans)
    }

    @Test
    fun `주간 목표는 횟수·거리를 고른 뒤에만 저장할 수 있다`() {
        val calls = mutableListOf<Any>()
        var state by mutableStateOf(coach)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                CoachScreen(
                    state,
                    CoachActions(
                        onGoalRuns = { calls += "runs:$it" },
                        onGoalDistance = { calls += it },
                        onSaveGoal = { calls += "save" }
                    )
                )
            }
        }
        compose.onNodeWithText("목표 저장").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("4회").performScrollTo().performClick()
        compose.onNodeWithText("20km").performScrollTo().performClick()
        state = coach.copy(goalRuns = 4, goalDistance = Distance(20_000))
        compose.onNodeWithText("목표 저장").performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf<Any>("runs:4", Distance(20_000), "save"), calls)
    }

    @Test
    fun `TC-9742 랭킹 행은 순위·아바타·닉네임·거리만, 남은 거리·집계 시각·티어는 그리지 않는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RankingScreen(RankingUiState(isLoading = false, ranking = sampleRanking), {}, {}, {})
            }
        }
        compose.onNodeWithText("한 단계 위까지", substring = true).assertDoesNotExist()
        compose.onNodeWithText("집계", substring = true).assertDoesNotExist()
        compose.onNodeWithText("골드").assertDoesNotExist()
    }

    @Test
    fun `순위가 없으면 아직 순위가 없다고 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RankingScreen(
                    RankingUiState(isLoading = false, ranking = sampleRanking.copy(entries = emptyList(), mine = null)),
                    {},
                    {},
                    {}
                )
            }
        }
        compose.onNodeWithText("아직 순위가 없어요").assertExists()
    }

    @Test
    fun `랭킹 줄은 순위·이름·거리를 한 문장으로 읽는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RankingScreen(RankingUiState(isLoading = false, ranking = sampleRanking), {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("1위 바람, 48.00킬로미터").assertExists()
        compose.onNodeWithContentDescription("2위 나, 41.20킬로미터, 나").assertExists()
    }

    @Test
    fun `랭킹 범위·기간을 바꾸면 내 순위 카드가 바뀐 결과를 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RankingScreen(RankingUiState(isLoading = false, ranking = sampleRanking), {}, {}, {})
            }
        }
        compose.onNode(
            androidx.compose.ui.test.hasContentDescription("2위 나", substring = true) and
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.LiveRegion,
                    androidx.compose.ui.semantics.LiveRegionMode.Polite
                )
        ).assertExists()
    }

    @Test
    fun growthShot() = compose.captureRunkkScreen("record_growth") {
        GrowthScreen(GrowthUiState(isLoading = false, growth = sampleGrowth), {}, {}, {})
    }

    @Test
    fun reportShot() = compose.captureRunkkScreen("record_report") {
        ReportScreen(
            ReportUiState(
                isLoading = false,
                report = Report("이번 주 3회, 15.2km · 평균 5:38", "지난주보다 페이스가 10초 빨라졌어요. 이 흐름이면 10K도 충분해요", "AI")
            ),
            {
            },
            {}
        )
    }

    @Test
    fun rankingShot() = compose.captureRunkkScreen("record_ranking") {
        RankingScreen(RankingUiState(isLoading = false, ranking = sampleRanking), {}, {}, {})
    }

    @Test
    fun coachShot() = compose.captureRunkkScreen("record_coach") {
        CoachScreen(coach.copy(goalRuns = 4, goalDistance = Distance(20_000)), CoachActions())
    }
}
