package com.a207.runkk.app.ui.run

import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.designsystem.testing.captureRunkkScreen
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.domain.account.MyAvatar
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
        heartRate = HeartRateSample(start, 152)
    )

    /** 북동쪽으로 휘는 짧은 경로(측정 화면 B2·B4). */
    private val route = RouteLine(
        listOf(
            GeoPoint(37.5000, 127.0000),
            GeoPoint(37.5006, 127.0004),
            GeoPoint(37.5012, 127.0012),
            GeoPoint(37.5016, 127.0024),
            GeoPoint(37.5024, 127.0031)
        )
    )

    @Test
    fun countdown() = compose.captureRunkkScreen("B1_countdown") {
        RunScreen(
            RunUiState(
                phase = RunPhase.COUNTDOWN,
                countdown = 2,
                targetDistance = Distance(5_000),
                targetPace = Pace(330),
                isFromCoachPlan = true
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
                route = route,
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
    fun paused() = compose.captureRunkkScreen("B2_running_paused") {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                route = route,
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
                route = route,
                targetDistance = Distance(5_000),
                isDiscardConfirming = true
            ),
            RunActions()
        )
    }

    @Test
    fun runningLight() = compose.captureRunkkScreen("B2_running_light", isDarkTheme = false) {
        RunScreen(
            RunUiState(phase = RunPhase.RUNNING, progress = progress, route = route, targetDistance = Distance(5_000)),
            RunActions()
        )
    }

    @Test
    fun pausedLight() = compose.captureRunkkScreen("B2_running_paused_light", isDarkTheme = false) {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = progress.copy(currentPace = null, isPaused = true),
                route = route,
                targetDistance = Distance(5_000)
            ),
            RunActions()
        )
    }

    /** 상용안 결과 값(피그마 61326:1878): 5.21km, 구간 5개(5km가 가장 빠름), 심박 5구간, 성과 두 줄. */
    private val result = RunResultUiState(
        isLoading = false,
        recordId = "rec-1",
        cardKind = CardKind.FREE_RUN,
        startedAt = start,
        distance = Distance(5_210),
        elapsed = Duration.ofSeconds(1_668),
        averagePace = Pace(332),
        averageHeartRate = 152,
        splitPaces = listOf(Pace(380), Pace(368), Pace(362), Pace(371), Pace(341)),
        heartRateZones = mapOf(
            "Z1" to Duration.ofMinutes(2),
            "Z2" to Duration.ofMinutes(6),
            "Z3" to Duration.ofMinutes(9),
            "Z4" to Duration.ofMinutes(5),
            "Z5" to Duration.ofMinutes(2)
        ),
        heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
        achievements = listOf(
            AchievementRow(AchievementIcon.STAR, UiText.Plain("최장 거리 신기록"), "직전 4.80km"),
            AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+52")
        ),
        pausedTotal = Duration.ofSeconds(185),
        isUploaded = true
    )

    /** TC-5851 라이트, 심박 있음. 1차 배포(아바타 꾸미기 닫힘): 결과는 뛰어오르는 기본 아바타다(D84). */
    @Test
    fun resultLight() = compose.captureRunkkScreen("B4_run_result_light", isDarkTheme = false) {
        FirstRelease { RunResultScreen(result, onCard = {}, onHome = {}, onRetry = {}) }
    }

    /** B-6 v2(피그마 61370:2110) 운영 빌드: 신기록, 크루 반영 「새벽런 크루 목표에 반영」, 리워드(S15P31A207-251). */
    @Test
    fun resultCrewLight() = compose.captureRunkkScreen("B6_run_result_crew_light", isDarkTheme = false) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
                com.a207.runkk.app.core.designsystem.ReleaseScope.of(isLockingUnreleased = true)
        ) {
            RunResultScreen(
                result.copy(
                    achievements = listOf(
                        result.achievements.first(),
                        AchievementRow(
                            AchievementIcon.CREW,
                            UiText.Res(R.string.run_result_crew, listOf("새벽런")),
                            "+5.21km"
                        ),
                        result.achievements.last()
                    )
                ),
                onCard = {},
                onHome = {},
                onRetry = {}
            )
        }
    }

    /** TC-5852 다크, 심박 있음(가장 빠른 구간 숫자 Lavender). */
    @Test
    fun result() = compose.captureRunkkScreen("B4_run_result") {
        FirstRelease { RunResultScreen(result, onCard = {}, onHome = {}, onRetry = {}) }
    }

    /** TC-5853 심박 없음(61325:1956): 지표 두 칸, 「워치를 연결하면 심박 구간을 볼 수 있어요」. */
    @Test
    fun resultNoHeart() = compose.captureRunkkScreen("B4_run_result_no_heart", isDarkTheme = false) {
        FirstRelease {
            RunResultScreen(
                result.copy(averageHeartRate = null, heartRateZones = emptyMap()),
                onCard = {},
                onHome = {},
                onRetry = {}
            )
        }
    }

    /** TC-5854 출생연도 없음: 「출생연도를 입력하면 심박 구간을 볼 수 있어요」(제안). */
    @Test
    fun resultNoBirthYear() = compose.captureRunkkScreen("B4_run_result_no_birth_year", isDarkTheme = false) {
        FirstRelease {
            RunResultScreen(
                result.copy(heartRateZones = emptyMap(), hasBirthYear = false),
                onCard = {},
                onHome = {},
                onRetry = {}
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun FirstRelease(content: @androidx.compose.runtime.Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.a207.runkk.app.core.designsystem.LocalReleaseScope provides
                com.a207.runkk.app.core.designsystem.ReleaseScope.FIRST_RELEASE,
            content = content
        )
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

    /** 2차 배포(아바타 꾸미기 열림): 히어로 아바타가 홈과 같은 내 아바타(기본 캐릭터 고양이)다(S15P31A207-248). */
    @Test
    fun resultMyAvatar() = compose.captureRunkkScreen("B4_run_result_my_avatar") {
        RunResultScreen(
            result.copy(myAvatar = MyAvatar("default:CAT", emptyList(), null)),
            onCard = {},
            onHome = {},
            onRetry = {}
        )
    }
}
