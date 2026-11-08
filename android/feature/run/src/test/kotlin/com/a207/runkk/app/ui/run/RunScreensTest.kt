package com.a207.runkk.app.ui.run

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.DEFAULT_AVATAR_TAG
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.shouldAskNotificationPermission
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.run.RunProgress
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 카운트다운·측정·잠금·결과 화면: 누름 종류별 행동과 접근성 라벨. */
@RunWith(RobolectricTestRunner::class)
class RunScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = RunActions(
        onCancel = { calls += "cancel" },
        onCountdownTapped = { calls += "skip" },
        onFinishTap = { calls += "finishTap" },
        onFinishLongPress = { calls += "finishLong" },
        onPause = { calls += "pause" },
        onResume = { calls += "resume" },
        onRequestPermission = { calls += "permission" },
        onOpenConsents = { calls += "consents" },
        onConsentDismiss = { calls += "dismiss" }
    )

    private fun show(state: RunUiState) = compose.setContent {
        RunkkTheme(reduceMotion = true) { RunScreen(state, actions) }
    }

    @Test
    fun `TC-9727 카운트다운은 머리 알약, 숫자, 설정 요약 한 줄만 있고 숫자를 누르면 바로 시작한다`() {
        show(
            RunUiState(
                phase = RunPhase.COUNTDOWN,
                countdown = 2,
                targetDistance = Distance(5_000),
                isFromCoachPlan = true
            )
        )
        compose.onNodeWithText("자유런 · 5km").assertDoesNotExist()
        compose.onNodeWithText("5km · 음성 · 진동 · 코치보이스").assertExists()
        compose.onNodeWithText("GPS", substring = true).assertDoesNotExist()
        compose.onNodeWithText("워치", substring = true).assertDoesNotExist()
        compose.onNodeWithText("바꾸기").assertDoesNotExist()
        compose.onNodeWithText("취소").assertDoesNotExist()
        compose.onNodeWithContentDescription("2").performClick()
        assertEquals(listOf("skip"), calls)
    }

    @Test
    fun `카운트다운 요약은 꺼진 설정과 목표 자유를 뺀다`() {
        show(RunUiState(phase = RunPhase.COUNTDOWN, isVoiceOn = false))
        compose.onNodeWithText("자유런").assertDoesNotExist()
        compose.onNodeWithText("진동").assertExists()
    }

    @Test
    fun `측정 중 종료는 짧게 안내, 길게 종료이고 잠금 버튼이 없다(D-256)`() {
        show(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = RunProgress.INITIAL.copy(distance = Distance(2_140)),
                targetDistance = Distance(5_000)
            )
        )
        val finish = compose.onNodeWithContentDescription("러닝 종료")
        finish.performClick()
        finish.performTouchInput { longClick() }
        compose.onNodeWithContentDescription("화면 잠금").assertDoesNotExist()
        assertEquals(listOf("finishTap", "finishLong"), calls)
        compose.onNodeWithContentDescription("2.14 킬로미터").assertExists()
    }

    @Test
    fun `위치 동의 안내는 동의하러 가기와 닫기를 보인다(A7)`() {
        show(RunUiState(phase = RunPhase.PREPARING, isConsentRequired = true))
        compose.onNodeWithText("러닝을 기록하려면 위치 정보 수집에 동의해 주세요.").assertExists()
        compose.onNodeWithText("동의하러 가기").performClick()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(listOf("permission", "dismiss"), calls)
    }

    @Test
    fun `재동의 안내는 뒤로가기로 닫히고 동의 관리로 가지 않는다`() {
        show(RunUiState(phase = RunPhase.PREPARING, isConsentRequired = true))
        compose.onNodeWithText("위치 정보 동의가 필요해요").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `S15P31A207-251 운영 빌드 결과 화면은 크루 반영 줄을 보이고 크루가 닫힌 범위는 숨긴다`() {
        var scope by mutableStateOf(ReleaseScope.of(isLockingUnreleased = true))
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides scope) {
                RunkkTheme(reduceMotion = true) {
                    RunResultScreen(
                        RunResultUiState(
                            isLoading = false,
                            recordId = "r",
                            distance = Distance(5_210),
                            elapsed = java.time.Duration.ofSeconds(1_668),
                            achievements = listOf(
                                AchievementRow(
                                    AchievementIcon.CREW,
                                    UiText.Res(R.string.run_result_crew, listOf("새벽런")),
                                    "+5.21km"
                                )
                            ),
                            startedAt = start,
                            isUploaded = true
                        ),
                        onCard = {},
                        onHome = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("새벽런 크루 목표에 반영, +5.21km").assertExists()
        scope = ReleaseScope.FIRST_RELEASE
        compose.onNodeWithContentDescription("새벽런 크루 목표에 반영, +5.21km").assertDoesNotExist()
    }

    @Test
    fun `TC-9729 결과 화면은 시간·휴식·평균 페이스·평균 심박·구간·신기록·리워드이고 열량·날씨·집계 제외가 없다`() {
        compose.setContent {
            CompositionLocalProvider(LocalReleaseScope provides ReleaseScope.FIRST_RELEASE) {
                RunkkTheme(reduceMotion = true) {
                    RunResultScreen(
                        RunResultUiState(
                            isLoading = false,
                            recordId = "r",
                            cardKind = com.a207.runkk.app.core.model.CardKind.FREE_RUN,
                            distance = Distance(5_210),
                            elapsed = java.time.Duration.ofSeconds(1_668),
                            averagePace = com.a207.runkk.app.core.model.Pace(332),
                            averageHeartRate = 152,
                            splitPaces = listOf(com.a207.runkk.app.core.model.Pace(380)),
                            heartRateZones = mapOf("Z2" to java.time.Duration.ofMinutes(8)),
                            achievements = listOf(
                                AchievementRow(AchievementIcon.STAR, UiText.Plain("최장 거리 신기록"), "직전 4.80km"),
                                AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.run_result_reward), "+52")
                            ),
                            heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
                            pausedTotal = java.time.Duration.ofSeconds(185),
                            startedAt = start,
                            isUploaded = true
                        ),
                        onCard = {},
                        onHome = {},
                        onRetry = {}
                    )
                }
            }
        }
        compose.onNodeWithText("러닝 결과").assertExists()
        // 결과 상세는 뛰어오르는 아바타다(D84, 피그마 B-6)
        compose.onNodeWithTag(DEFAULT_AVATAR_TAG + "JUMP", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("휴식 3분 5초", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 페이스", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 심박", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("구간 페이스").assertExists()
        compose.onNodeWithText("심박 구간").assertExists()
        // 상용안(FR-289): 성과는 이름과 값을 나눠 보이고 구간 페이스에 「가장 빠른 구간」 범례가 있다.
        compose.onNodeWithContentDescription("최장 거리 신기록, 직전 4.80km").assertExists()
        compose.onNodeWithContentDescription("리워드, +52").assertExists()
        compose.onNodeWithText("가장 빠른 구간 1km", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("자유런", substring = true).assertExists()
        listOf("kcal", "°C", "집계 제외", "유효 러닝").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `TC-5850 결과 하단 바는 홈으로와 기록 카드 만들기다`() {
        var card = 0
        var home = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunResultScreen(
                    RunResultUiState(
                        isLoading = false,
                        recordId = "r",
                        distance = Distance(5_020),
                        elapsed = java.time.Duration.ofSeconds(1_668),
                        averagePace = com.a207.runkk.app.core.model.Pace(332),
                        averageHeartRate = 152,
                        splitPaces = List(10) { com.a207.runkk.app.core.model.Pace(330 + it) },
                        heartRateZones = mapOf("Z2" to java.time.Duration.ofMinutes(8)),
                        heartRateSource = RunResultUiState.HEART_SOURCE_WATCH,
                        startedAt = start,
                        isUploaded = true
                    ),
                    onCard = { card++ },
                    onHome = { home++ },
                    onRetry = {}
                )
            }
        }
        val barTop = compose.onNodeWithTag(RESULT_BOTTOM_BAR_TAG).getUnclippedBoundsInRoot().top
        compose.onNodeWithText("심박 구간").performScrollTo()
        // 스크롤해도 하단 바는 같은 자리에 보인다.
        assertEquals(barTop, compose.onNodeWithTag(RESULT_BOTTOM_BAR_TAG).getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("홈으로").assertIsDisplayed().performClick()
        compose.onNodeWithText("기록 카드 만들기").assertIsDisplayed().performClick()
        assertEquals(1, home)
        assertEquals(1, card)
        // 결과에는 티어 배지가 없다.
        listOf("티어", "배치 중").forEach {
            compose.onNodeWithContentDescription(it, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `결과 화면은 기록 카드와 홈으로를 보인다`() {
        var card = 0
        var home = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunResultScreen(
                    RunResultUiState(
                        isLoading = false,
                        recordId = "r",
                        distance = Distance(5_020),
                        elapsed = java.time.Duration.ofSeconds(1_668),
                        startedAt = start,
                        isUploaded = true,
                        achievements = listOf(AchievementRow(AchievementIcon.STAR, UiText.Plain("최장 거리 신기록"), ""))
                    ),
                    onCard = { card++ },
                    onHome = { home++ },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithContentDescription("5.02 킬로미터", substring = true).assertExists()
        compose.onNodeWithContentDescription("최장 거리 신기록", substring = true).assertExists()
        compose.onNodeWithText("기록 카드 만들기").performClick()
        compose.onNodeWithText("홈으로").performClick()
        assertEquals(1 to 1, card to home)
    }

    @Test
    fun `측정 중 일시정지 버튼은 48dp 이상이고 누르면 일시정지, 정지 중에는 재개로 바뀐다(FR-194)`() {
        var state by mutableStateOf(RunUiState(phase = RunPhase.RUNNING))
        compose.setContent { RunkkTheme(reduceMotion = true) { RunScreen(state, actions) } }
        compose.onNodeWithContentDescription("일시정지").assertHeightIsAtLeast(48.dp).performClick()
        state = RunUiState(phase = RunPhase.RUNNING, progress = RunProgress.INITIAL.copy(isPaused = true))
        compose.onNodeWithContentDescription("재개").performClick()
        compose.onNodeWithContentDescription("일시정지").assertDoesNotExist()
        compose.onNodeWithText("자유런").assertDoesNotExist()
        compose.onNodeWithContentDescription("러닝 종료").performTouchInput { longClick() }
        assertEquals(listOf("pause", "resume", "finishLong"), calls)
    }

    @Test
    fun `TC-9728 자유런 측정 화면은 거리·시간·평균 페이스·심박과 경로선이고 현재 페이스·잠금이 없다`() {
        show(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = RunProgress.INITIAL.copy(
                    distance = Distance(2_140),
                    elapsed = java.time.Duration.ofSeconds(712),
                    currentPace = com.a207.runkk.app.core.model.Pace(321),
                    averagePace = com.a207.runkk.app.core.model.Pace(332)
                ),
                route = com.a207.runkk.app.core.model.RouteLine(
                    listOf(
                        com.a207.runkk.app.core.model.GeoPoint(37.5, 127.0),
                        com.a207.runkk.app.core.model.GeoPoint(37.501, 127.001)
                    )
                ),
                targetDistance = Distance(5_000)
            )
        )
        compose.onNodeWithText("11:52").assertExists()
        compose.onNodeWithText("5:32").assertExists()
        compose.onNodeWithText("시간").assertExists()
        compose.onNodeWithText("평균 페이스").assertExists()
        compose.onNodeWithText("심박").assertExists()
        compose.onNodeWithText("5:21").assertDoesNotExist()
        compose.onNodeWithText("현재 페이스", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("화면 잠금").assertDoesNotExist()
        compose.onNodeWithContentDescription("러닝 종료").assertExists()
        compose.onNodeWithTag(com.a207.runkk.app.core.designsystem.ROUTE_CANVAS_TAG).assertExists()
        compose.onNodeWithTag(com.a207.runkk.app.core.designsystem.MY_MARKER_TAG).assertExists()
    }

    @Test
    fun `TC-9620 알림 권한은 Android 13 이상에서 없을 때 한 번만 묻는다`() {
        assertEquals(true, shouldAskNotificationPermission(33, isGranted = false, isAsked = false))
        assertEquals(false, shouldAskNotificationPermission(32, isGranted = false, isAsked = false))
        assertEquals(false, shouldAskNotificationPermission(36, isGranted = true, isAsked = false))
        assertEquals(false, shouldAskNotificationPermission(36, isGranted = false, isAsked = true))
    }
}
