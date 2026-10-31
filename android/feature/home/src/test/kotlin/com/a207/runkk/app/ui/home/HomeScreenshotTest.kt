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
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
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
        streakWeeks = 4
    )

    @Test
    fun home() = compose.captureRunkkScreen("A4_home") {
        HomeShell { HomeScreen(loaded, HomeActions(), it) }
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

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun homeLight() = compose.captureRunkkScreen("A4_home_light", isDarkTheme = false) {
        HomeShell { HomeScreen(loaded, HomeActions(), it) }
    }

    /** 가이드 1단계(자유런 카드, 1/6). */
    @Test
    fun guideFirstStep() = compose.captureRunkkScreen("J1_guide_free_run") {
        GuideOn(GuideStep.FREE_RUN, index = 0)
    }

    @Test
    fun guideFirstStepLight() = compose.captureRunkkScreen("J1_guide_free_run_light", isDarkTheme = false) {
        GuideOn(GuideStep.FREE_RUN, index = 0)
    }

    /** 가이드 마지막 단계(마이 탭, 6/6): "시작하기"만. */
    @Test
    fun guideLastStep() = compose.captureRunkkScreen("J7_guide_me") {
        GuideOn(GuideStep.ME, index = 5)
    }

    @Test
    fun guideLastStepLight() = compose.captureRunkkScreen("J7_guide_me_light", isDarkTheme = false) {
        GuideOn(GuideStep.ME, index = 5)
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
                    stepCount = 6,
                    anchor = anchors[step],
                    onNext = {},
                    onSkip = {}
                )
            }
        }
    }
}

/** 앱 탭 화면 모양: 홈 자리 아래에 하단 탭 높이를 비우고(RunkkNavHost EntryBottomSpace) 하단 탭 바를 겹친다. 1차 배포 범위다. */
@Composable
private fun HomeShell(home: @Composable (Modifier) -> Unit) {
    CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
        Box(Modifier.fillMaxSize().background(RunkkColors.Background)) {
            home(Modifier.padding(bottom = RUNKK_BOTTOM_BAR_HEIGHT))
            RunkkBottomBar(
                selected = RunkkTab.HOME,
                onSelect = {},
                lockedTabs = setOf(RunkkTab.CREW),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
