package com.a207.runkk.app.ui.run

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkTheme
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
        compose.onNodeWithText("자유런 · 5km").assertExists()
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
        compose.onNodeWithText("자유런").assertExists()
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
    fun `TC-9729 결과 화면은 시간·휴식·평균 페이스·평균 심박·구간·신기록·리워드이고 열량·날씨·집계 제외가 없다`() {
        compose.setContent {
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
                        heartRateZones = listOf("Z2" to java.time.Duration.ofMinutes(8)),
                        newRecords = listOf("최장 거리 신기록 · 직전 4.80km"),
                        rewardPoints = 52,
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
        compose.onNodeWithText("러닝 결과").assertExists()
        compose.onNodeWithText("휴식 3분 5초", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 페이스", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("평균 심박", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("구간 페이스").assertExists()
        compose.onNodeWithText("심박 구간").assertExists()
        compose.onNodeWithText("최장 거리 신기록 · 직전 4.80km").assertExists()
        compose.onNodeWithText("리워드 +52").assertExists()
        compose.onNodeWithText("자유런", substring = true).assertExists()
        listOf("kcal", "°C", "집계 제외", "유효 러닝", "가장 빠른").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
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
                        newRecords = listOf("최장 거리 신기록")
                    ),
                    onCard = { card++ },
                    onHome = { home++ },
                    onRetry = {}
                )
            }
        }
        compose.onNodeWithContentDescription("5.02 킬로미터").assertExists()
        compose.onNodeWithText("최장 거리 신기록").assertExists()
        compose.onNodeWithText("기록 카드").performClick()
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
        compose.onNodeWithText("자유런").assertExists()
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
