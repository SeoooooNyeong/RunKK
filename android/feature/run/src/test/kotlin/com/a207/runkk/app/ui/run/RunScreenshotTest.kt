package com.a207.runkk.app.ui.run

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunProgress
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 카운트다운(B1)·러닝(B2)·잠금(B3)·결과(B4) 스크린샷(가짜 UiState, 390×844dp 다크). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class RunScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val progress = RunProgress(
        elapsed = Duration.ofSeconds(712),
        distance = Distance(2_140),
        currentPace = Pace(321),
        averagePace = Pace(332),
        signal = SignalState.NORMAL,
        isLocked = false,
        heartRate = HeartRateSample(start, 152)
    )

    @Test
    fun countdown() = compose.captureRunkkScreen("B1_countdown") {
        RunScreen(
            RunUiState(
                phase = RunPhase.COUNTDOWN,
                countdown = 3,
                targetDistance = Distance(5_000),
                targetPace = Pace(330)
            ),
            RunActions()
        )
    }

    @Test
    fun preparing() = compose.captureRunkkScreen("B1_countdown_needs_consent_dismissed") {
        RunScreen(
            RunUiState(phase = RunPhase.PREPARING, consentRequired = true, consentDialogDismissed = true),
            RunActions()
        )
    }

    @Test
    fun running() = compose.captureRunkkScreen("B2_running") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress,
                targetDistance = Distance(5_000),
                splitBanner = UiText.Plain("2km 통과 · 11:04 · 평균 5:32")
            ),
            RunActions()
        )
    }

    @Test
    fun runningHint() = compose.captureRunkkScreen("B2_running_finish_hint") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(heartRate = null),
                hint = UiText.Res(R.string.run_hold_to_finish)
            ),
            RunActions()
        )
    }

    @Test
    fun locked() = compose.captureRunkkScreen("B3_running_locked") {
        RunScreen(
            RunUiState(
                phase = RunPhase.LOCKED,
                progress = progress.copy(isLocked = true),
                targetDistance = Distance(5_000)
            ),
            RunActions()
        )
    }

    @Test
    fun result() = compose.captureRunkkScreen("B4_run_result") {
        RunResultScreen(
            RunResultUiState(
                loading = false,
                recordId = "rec-1",
                cardKind = CardKind.FREE_RUN,
                startedAt = start,
                distance = Distance(5_020),
                elapsed = Duration.ofSeconds(1_668),
                averagePace = Pace(332),
                averageHeartRate = 152,
                route = listOf(
                    GeoPoint(37.50, 127.00),
                    GeoPoint(37.505, 127.01),
                    GeoPoint(37.502, 127.02),
                    GeoPoint(37.51, 127.03)
                ),
                weather = "12°C 맑음",
                calories = 312,
                newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
                heartRateZones = listOf(
                    "Z1" to Duration.ofMinutes(2),
                    "Z2" to Duration.ofMinutes(6),
                    "Z3" to Duration.ofMinutes(9),
                    "Z4" to Duration.ofMinutes(5),
                    "Z5" to Duration.ofMinutes(2)
                ),
                uploaded = true
            ),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }

    @Test
    fun resultPendingUpload() = compose.captureRunkkScreen("B4_run_result_not_uploaded") {
        RunResultScreen(
            RunResultUiState(
                loading = false,
                recordId = "rec-1",
                startedAt = start,
                distance = Distance(3_000),
                elapsed = Duration.ofMinutes(20),
                averagePace = Pace(400)
            ),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }
}
