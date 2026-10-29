package com.a207.runkk.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Distance
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 홈(A-4)과 첫 사용 가이드(J-1) 스크린샷(가짜 UiState, 390×844dp). 1차 배포(아바타 꾸미기 닫힘) 모습이다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class HomeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val loaded = HomeUiState(
        isLoading = false,
        header = HomeHeader("달리는곰", "골드", 3, 1_250),
        isHeaderLoading = false,
        weeklyDistance = Distance(12_000),
        weeklyGoal = Distance(20_000),
        streakDays = 4
    )

    @Test
    fun home() = compose.captureRunkkScreen("A4_home") {
        FirstRelease { HomeScreen(loaded, HomeActions()) }
    }

    @Test
    fun homeLoading() = compose.captureRunkkScreen("A4_home_loading") {
        FirstRelease { HomeScreen(HomeUiState(), HomeActions()) }
    }

    @Test
    fun homeOffline() = compose.captureRunkkScreen("A4_home_error") {
        FirstRelease {
            HomeScreen(
                HomeUiState(isLoading = false, isHeaderLoading = false, error = RunkkErrorText.of(AppError.Network)),
                HomeActions()
            )
        }
    }

    @Test
    fun homeLocationConsent() = compose.captureRunkkScreen("A6_location_consent") {
        FirstRelease { HomeScreen(loaded.copy(isConsentSheetVisible = true), HomeActions()) }
    }

    // 라이트 벌(FR-203, NFR-041 화면 10개 × 테마 2).
    @Test
    fun homeLight() = compose.captureRunkkScreen("A4_home_light", isDarkTheme = false) {
        FirstRelease { HomeScreen(loaded, HomeActions()) }
    }

    /** 가이드 1단계(자유런 카드 앵커 자리 가정, 1/6). */
    @Test
    fun guideFirstStep() = compose.captureRunkkScreen("J1_guide_free_run") {
        FirstRelease {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(loaded, HomeActions())
                FirstRunGuideOverlay(
                    step = GuideStep.FREE_RUN,
                    index = 0,
                    stepCount = 6,
                    anchor = Rect(42f, 470f, 520f, 700f),
                    onNext = {},
                    onSkip = {}
                )
            }
        }
    }

    /** 가이드 마지막 단계(마이 탭, 6/6): "시작하기"만. */
    @Test
    fun guideLastStep() = compose.captureRunkkScreen("J7_guide_me") {
        FirstRelease {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(loaded, HomeActions())
                FirstRunGuideOverlay(
                    step = GuideStep.ME,
                    index = 5,
                    stepCount = 6,
                    anchor = Rect(860f, 2380f, 1060f, 2580f),
                    onNext = {},
                    onSkip = {}
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun FirstRelease(content: @androidx.compose.runtime.Composable () -> Unit) {
    CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE, content = content)
}
