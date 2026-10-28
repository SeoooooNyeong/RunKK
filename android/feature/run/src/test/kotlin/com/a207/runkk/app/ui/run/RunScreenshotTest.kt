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

/** 카운트다운(B1)·러닝(B2, 일시정지 포함)·잠금(B3)·결과(B4) 스크린샷(가짜 UiState, 390×844dp 다크, _light는 라이트 벌). */
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

    /** A7 위치 동의 필요 안내(대화상자). */
    @Test
    fun consent() = compose.captureRunkkScreen("A7_location_consent") {
        RunScreen(RunUiState(phase = RunPhase.PREPARING, isConsentRequired = true), RunActions())
    }

    @Test
    fun preparing() = compose.captureRunkkScreen("B1_countdown_needs_consent_dismissed") {
        RunScreen(
            RunUiState(phase = RunPhase.PREPARING, isConsentRequired = true, isConsentDialogDismissed = true),
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
                splitBanner = UiText.Plain("2km, 11:04, 평균 페이스 5:32")
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
    fun paused() = compose.captureRunkkScreen("B2_running_paused") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                targetDistance = Distance(5_000),
                pausedFor = Duration.ofSeconds(192)
            ),
            RunActions()
        )
    }

    /** B2-3 장시간 정지 자동 종료 안내(정지 55분 12초). */
    @Test
    fun pauseAutoEnd() = compose.captureRunkkScreen("B2_running_pause_auto_end") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                targetDistance = Distance(5_000),
                pausedFor = Duration.ofMinutes(55).plusSeconds(12)
            ),
            RunActions()
        )
    }

    @Test
    fun pausedLocked() = compose.captureRunkkScreen("B3_running_locked_paused") {
        RunScreen(
            RunUiState(
                phase = RunPhase.LOCKED,
                progress = progress.copy(currentPace = null, isLocked = true, isPaused = true),
                targetDistance = Distance(5_000)
            ),
            RunActions()
        )
    }

    /** 자유런 취소 확인(측정 화면에서 취소를 누른 뒤). */
    @Test
    fun discardDialog() = compose.captureRunkkScreen("B2_running_discard_confirm") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress,
                targetDistance = Distance(5_000),
                isDiscardConfirming = true
            ),
            RunActions()
        )
    }

    @Test
    fun discardDialogLight() = compose.captureRunkkScreen("B2_running_discard_confirm_light", isDarkTheme = false) {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                targetDistance = Distance(5_000),
                pausedFor = Duration.ofSeconds(192),
                isDiscardConfirming = true
            ),
            RunActions()
        )
    }

    @Test
    fun runningLight() = compose.captureRunkkScreen("B2_running_light", isDarkTheme = false) {
        RunScreen(
            RunUiState(phase = RunPhase.RUNNING, progress = progress, targetDistance = Distance(5_000)),
            RunActions()
        )
    }

    @Test
    fun pausedLight() = compose.captureRunkkScreen("B2_running_paused_light", isDarkTheme = false) {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                targetDistance = Distance(5_000),
                pausedFor = Duration.ofSeconds(192)
            ),
            RunActions()
        )
    }

    @Test
    fun resultLight() = compose.captureRunkkScreen("B4_run_result_light", isDarkTheme = false) {
        RunResultScreen(
            RunResultUiState(
                isLoading = false,
                recordId = "rec-1",
                cardKind = CardKind.FREE_RUN,
                startedAt = start,
                distance = Distance(5_020),
                elapsed = Duration.ofSeconds(1_668),
                averagePace = Pace(332),
                averageHeartRate = 152,
                route = listOf(GeoPoint(37.50, 127.00), GeoPoint(37.505, 127.01), GeoPoint(37.51, 127.03)),
                heartRateZones = listOf("Z1" to Duration.ofMinutes(2), "Z3" to Duration.ofMinutes(9)),
                isUploaded = true
            ),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }

    @Test
    fun result() = compose.captureRunkkScreen("B4_run_result") {
        RunResultScreen(
            RunResultUiState(
                isLoading = false,
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
                newRecords = listOf("최장 거리 신기록, 직전 4.80km"),
                pausedTotal = Duration.ofMinutes(3),
                heartRateZones = listOf(
                    "Z1" to Duration.ofMinutes(2),
                    "Z2" to Duration.ofMinutes(6),
                    "Z3" to Duration.ofMinutes(9),
                    "Z4" to Duration.ofMinutes(5),
                    "Z5" to Duration.ofMinutes(2)
                ),
                isUploaded = true
            ),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }

    /** 서버가 무효로 판정한 기록(순간이동만 있는 기록): "집계 제외" 표시, 칼로리 없음. */
    private fun excludedResult() = RunResultUiState(
        isLoading = false,
        recordId = "rec-2",
        cardKind = CardKind.FREE_RUN,
        startedAt = start,
        distance = Distance(360),
        elapsed = Duration.ofSeconds(120),
        averagePace = Pace(333),
        route = listOf(GeoPoint(37.50, 127.00), GeoPoint(37.502, 127.004)),
        weather = "12°C 맑음",
        isUploaded = true,
        isExcluded = true
    )

    @Test
    fun resultExcluded() = compose.captureRunkkScreen("B4_run_result_excluded") {
        RunResultScreen(excludedResult(), onCard = {}, onHome = {}, onRetry = {})
    }

    @Test
    fun resultExcludedLight() = compose.captureRunkkScreen("B4_run_result_excluded_light", isDarkTheme = false) {
        RunResultScreen(excludedResult(), onCard = {}, onHome = {}, onRetry = {})
    }

    @Test
    fun resultPendingUpload() = compose.captureRunkkScreen("B4_run_result_not_uploaded") {
        RunResultScreen(
            RunResultUiState(
                isLoading = false,
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
