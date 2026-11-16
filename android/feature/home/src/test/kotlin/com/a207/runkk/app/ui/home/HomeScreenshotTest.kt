package com.a207.runkk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.GuideAnchors
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.LocalGuideAnchors
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkBottomBar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkTab
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.account.PlacementProgress
import com.a207.runkk.app.domain.account.PlacementStatus
import com.a207.runkk.app.domain.record.RunSummary
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 홈(A-4)과 첫 사용 가이드(J-1, J-7) 스크린샷(가짜 UiState, 390×844dp). 1차 배포(아바타 꾸미기 닫힘) 모습이다.
 * 앱처럼 홈 자리는 하단 탭 높이만큼 위에서 끝나고 그 위에 하단 탭 바를 겹친다. 가이드 강조 자리는 화면 요소가 적은 실제 앵커를 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val loaded = HomeUiState(
        isLoading = false,
        header = HomeHeader("달리는곰", "골드", 3, 1_250),
        isHeaderLoading = false,
        isAvatarReady = true,
        weeklyDistance = Distance(12_000),
        weeklyGoal = Distance(20_000),
        streakWeeks = 4,
        tierDisplay = TierDisplay.Placing
    )

    /** 펼친 패널 값(피그마 61342:2228): 오늘 3.20km 18분 40초 1회, 이번 달 41.2km 9회, 최근 러닝, 5주 잔디. */
    private val today = LocalDate.of(2026, 11, 4)
    private val summary = HomeSummary(
        today = TodaySummary(Distance(3_200), Duration.ofSeconds(1_120), 1),
        month = MonthSummary(Distance(41_200), 9),
        latestRun = RunSummary(
            recordId = "run-9",
            startedAt = LocalDateTime.of(2026, 11, 4, 7, 10).atZone(ZoneId.systemDefault()).toInstant(),
            distance = Distance(3_200),
            elapsed = Duration.ofSeconds(1_120),
            averagePace = Pace(350),
            isValid = true,
            mode = RunMode.FREE,
            cardKind = CardKind.FREE_RUN
        ),
        grass = GrassWeeks.of(
            listOf(7, 9, 12, 14, 15, 20, 22, 26, 28, 30, 31).associate {
                LocalDate.of(2026, 10, it) to Distance(3_000)
            } + listOf(2, 4).associate { LocalDate.of(2026, 11, it) to Distance(3_200) },
            today
        )
    )
    private val expanded = loaded.copy(
        tierDisplay = TierDisplay.Confirmed("GOLD"),
        isSummaryExpanded = true,
        summary = summary
    )

    /**
     * TC-5843 내 기록 요약 접힘(FR-287·FR-288): 헤더 아주 작은 배지 「배치 중」 + Lv, 오른쪽 「내 기록 요약 ⌄」, 펼친 패널 없음.
     * 라이트 짝은 [homeLight](A4_home_light)다.
     */
    @Test
    fun home() = compose.captureRunkkScreen("A4_home") {
        HomeShell { HomeScreen(loaded, HomeActions(), it) }
    }

    /** 크루런 결과 「확인」 뒤 이번 주 막대: 라임 테두리와 「+0.8km 크루런」(2초, 설계 05-32 흐름 4). */
    @Test
    fun homeCrewRunAdded() = compose.captureRunkkScreen("A4_home_crew_run_added") {
        HomeShell { HomeScreen(loaded.copy(weeklyHighlight = Distance(800)), HomeActions(), it) }
    }

    @Test
    fun homeCrewRunAddedLight() = compose.captureRunkkScreen("A4_home_crew_run_added_light", isDarkTheme = false) {
        HomeShell { HomeScreen(loaded.copy(weeklyHighlight = Distance(800)), HomeActions(), it) }
    }

    @Test
    fun homeLoading() = compose.captureRunkkScreen("A4_home_loading") {
        HomeShell { HomeScreen(HomeUiState(), HomeActions(), it) }
    }

    @Test
    fun homeOffline() = compose.captureRunkkScreen("A4_home_error") {
        HomeShell {
            HomeScreen(
                HomeUiState(isLoading = false, isHeaderLoading = false, error = RunkkErrorText.of(AppError.Network)),
                HomeActions(),
                it
            )
        }
    }

    @Test
    fun homeLocationConsent() = compose.captureRunkkScreen("A6_location_consent") {
        HomeShell { HomeScreen(loaded.copy(isConsentSheetVisible = true), HomeActions(), it) }
    }

    /** TC-5844 펼침 라이트(61342:2228). */
    @Test
    fun summaryExpandedLight() = compose.captureRunkkScreen("A4_summary_expanded_light", isDarkTheme = false) {
        HomeShell { HomeScreen(expanded, HomeActions(), it) }
    }

    /** 작은 화면(360×640dp)에서 펼친 패널은 남은 높이에 맞춰 줄고 안에서 스크롤한다(잘림 수정, 2026-11-06 사용자 지적). */
    @Test
    @Config(qualifiers = "w360dp-h640dp")
    fun summaryExpandedSmall() = compose.captureRunkkScreen("A4_summary_expanded_small", isDarkTheme = false) {
        HomeShell { HomeScreen(expanded, HomeActions(), it) }
    }

    /** TC-5845 오늘 기록 없음(61342:2496). */
    @Test
    fun summaryNoToday() = compose.captureRunkkScreen("A4_summary_no_today", isDarkTheme = false) {
        HomeShell {
            HomeScreen(
                expanded.copy(summary = summary.copy(today = null, month = MonthSummary(Distance(38_000), 8))),
                HomeActions(),
                it
            )
        }
    }

    /** TC-5846 펼침 다크(61343:2758). */
    @Test
    fun summaryExpanded() = compose.captureRunkkScreen("A4_summary_expanded") {
        HomeShell { HomeScreen(expanded, HomeActions(), it) }
    }

    /** TC-5847 배틀런 진입 시트, 배치 중(61355:3402). 홈 위에 연 시트 창까지 화면째 찍는다. */
    @Test
    fun battleEntryPlacing() = captureEntrySheet(
        "A4_battle_entry_placing",
        PlacementProgress(PlacementStatus.IN_PROGRESS, 0, 1, null, "UNRANKED", emptyList())
    )

    /** TC-5848 배틀런 진입 시트, 확정 티어(제안 문구). */
    @Test
    fun battleEntryRanked() = captureEntrySheet(
        "A4_battle_entry_ranked",
        PlacementProgress(PlacementStatus.COMPLETED, 3, null, null, "GOLD", emptyList())
    )

    /** 진입 시트는 따로 뜬 창(디자인 시스템 시트)이라 루트 하나가 아니라 화면 전체를 찍는다. */
    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureEntrySheet(name: String, placement: PlacementProgress) {
        compose.setContent {
            RunkkTheme(darkTheme = false, reduceMotion = true) {
                HomeShell { HomeScreen(loaded, HomeActions(), it) }
                CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                    BattleEntrySheet(BattleEntrySheetState(placement), onRandom = {}, onFriend = {}, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/$name.png")
    }

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun homeLight() = compose.captureRunkkScreen("A4_home_light", isDarkTheme = false) {
        HomeShell { HomeScreen(loaded, HomeActions(), it) }
    }

    /** 폴드 펼친 화면(673×841dp): 배경 트랙이 아래에 남아 아바타가 트랙 위에 선다(가운데 자르기면 공중에 뜬다). */
    @Test
    @Config(qualifiers = "w673dp-h841dp")
    fun homeFoldLight() = compose.captureRunkkScreen("A4_home_fold_light", isDarkTheme = false) {
        HomeShell { HomeScreen(loaded, HomeActions(), it) }
    }

    /** 가이드 시작 카드(FR-성-35). */
    @Test
    fun guideStartCard() = compose.captureRunkkScreen("J0_guide_start_card") {
        GuideStartOn()
    }

    @Test
    fun guideStartCardLight() = compose.captureRunkkScreen("J0_guide_start_card_light", isDarkTheme = false) {
        GuideStartOn()
    }

    /** 가이드 1단계(자유런 카드, 1 / 4). */
    @Test
    fun guideFirstStep() = compose.captureRunkkScreen("J1_guide_free_run") {
        GuideOn(GuideStep.FREE_RUN, index = 0)
    }

    @Test
    fun guideFirstStepLight() = compose.captureRunkkScreen("J1_guide_free_run_light", isDarkTheme = false) {
        GuideOn(GuideStep.FREE_RUN, index = 0)
    }

    /** 가이드 2단계(배틀런 카드, 2 / 4): 「건너뛰기」, 「이전」, 「다음」. */
    @Test
    fun guideBattleStepLight() = compose.captureRunkkScreen("J2_guide_battle_run_light", isDarkTheme = false) {
        GuideOn(GuideStep.BATTLE_RUN, index = 1)
    }

    /** 가이드 3단계(기록런 탭, 3 / 4). */
    @Test
    fun guideRecordRunStepLight() = compose.captureRunkkScreen("J3_guide_record_run_light", isDarkTheme = false) {
        GuideOn(GuideStep.RECORD_RUN, index = 2)
    }

    /** 가이드 마지막 단계(내 러너, 4 / 4): 「이전」, 「시작하기」. */
    @Test
    fun guideMyRunnerStep() = compose.captureRunkkScreen("J4_guide_my_runner") {
        GuideOn(GuideStep.MY_RUNNER, index = 3)
    }

    @Test
    fun guideMyRunnerStepLight() = compose.captureRunkkScreen("J4_guide_my_runner_light", isDarkTheme = false) {
        GuideOn(GuideStep.MY_RUNNER, index = 3)
    }

    /** 홈과 하단 탭 위에 시작 카드를 덮는다. */
    @Composable
    private fun GuideStartOn() {
        Box(Modifier.fillMaxSize()) {
            HomeShell { HomeScreen(loaded, HomeActions(), it) }
            FirstRunGuideStartCard(stepCount = GuideStep.visibleIn(RELEASE_SCOPE).size, onStart = {}, onSkip = {})
        }
    }

    /** 홈과 하단 탭을 그리고, 화면 요소가 적은 [step]의 앵커로 가이드 막을 덮는다. */
    @Composable
    private fun GuideOn(step: GuideStep, index: Int) {
        val anchors = remember { GuideAnchors() }
        CompositionLocalProvider(LocalGuideAnchors provides anchors) {
            Box(Modifier.fillMaxSize()) {
                HomeShell { HomeScreen(loaded, HomeActions(), it) }
                FirstRunGuideOverlay(
                    step = step,
                    index = index,
                    stepCount = GuideStep.visibleIn(RELEASE_SCOPE).size,
                    anchor = anchors[step],
                    onNext = {},
                    onPrevious = {},
                    onBack = {}
                )
            }
        }
    }
}

/** 운영 빌드 배포 범위(2차 배포: 크루·아바타 꾸미기 열림, S15P31A207-248). */
private val RELEASE_SCOPE = ReleaseScope.of(isLockingUnreleased = true)

/** 앱 탭 화면 모양: 홈 자리 아래에 하단 탭 높이를 비우고(RunkkNavHost EntryBottomSpace) 하단 탭 바를 겹친다. 운영 빌드 범위다. */
@Composable
private fun HomeShell(home: @Composable (Modifier) -> Unit) {
    CompositionLocalProvider(LocalReleaseScope provides RELEASE_SCOPE) {
        Box(Modifier.fillMaxSize().background(RunkkColors.Background)) {
            home(Modifier.padding(bottom = RUNKK_BOTTOM_BAR_HEIGHT))
            RunkkBottomBar(
                selected = RunkkTab.HOME,
                onSelect = {},
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
