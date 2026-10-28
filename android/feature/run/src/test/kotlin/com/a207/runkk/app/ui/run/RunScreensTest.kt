package com.a207.runkk.app.ui.run

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
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
        onLock = { calls += "lock" },
        onUnlockTap = { calls += "unlockTap" },
        onUnlockLongPress = { calls += "unlockLong" },
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
    fun `카운트다운 숫자를 누르면 바로 시작하고 취소가 있다`() {
        show(RunUiState(phase = RunPhase.COUNTDOWN, countdown = 2, targetDistance = Distance(5_000)))
        compose.onNodeWithContentDescription("2").performClick()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("자유런, GPS 찾는 중").assertExists()
        compose.onNodeWithText("지난번 설정 그대로").assertExists()
        assertEquals(listOf("skip", "cancel"), calls)
    }

    @Test
    fun `측정 중 종료는 짧게 안내, 길게 종료, 잠금은 따로`() {
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
        compose.onNodeWithContentDescription("화면 잠금").performClick()
        assertEquals(listOf("finishTap", "finishLong", "lock"), calls)
        compose.onNodeWithContentDescription("2.14 킬로미터").assertExists()
        compose.onNodeWithText("목표까지 2.86km").assertExists()
    }

    @Test
    fun `잠금 화면은 해제만 받는다(짧게 안내, 길게 해제)`() {
        show(RunUiState(phase = RunPhase.LOCKED, progress = RunProgress.INITIAL.copy(isLocked = true)))
        val unlock = compose.onNodeWithContentDescription("화면 잠금 해제")
        unlock.performClick()
        unlock.performSemanticsAction(SemanticsActions.OnLongClick)
        compose.onNodeWithText("화면 잠김").assertExists()
        compose.onNode(
            androidx.compose.ui.test.SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.PaneTitle,
                RuntimeEnvironment.getApplication().getString(R.string.run_locked_pane_title)
            )
        ).assertExists()
        assertEquals(listOf("unlockTap", "unlockLong"), calls)
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
        compose.onNodeWithText("일시정지").assertExists()
        compose.onNodeWithText("자유런").assertExists()
        compose.onNodeWithText("거리 (멈춤)").assertExists()
        compose.onNodeWithContentDescription("러닝 종료").performTouchInput { longClick() }
        assertEquals(listOf("pause", "resume", "finishLong"), calls)
    }

    @Test
    fun `잠금 중에는 일시정지 버튼이 없고, 워치 정지면 해제를 안내한다(D-120)`() {
        show(RunUiState(phase = RunPhase.LOCKED, progress = RunProgress.INITIAL.copy(isLocked = true, isPaused = true)))
        compose.onNodeWithContentDescription("일시정지").assertDoesNotExist()
        compose.onNodeWithContentDescription("재개").assertDoesNotExist()
        compose.onNodeWithText("일시정지").assertExists()
    }

    @Test
    fun `정지 55분이 넘으면 자동 종료 안내에서 지금 종료나 재개를 고른다(B2-3)`() {
        show(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = RunProgress.INITIAL.copy(isPaused = true),
                pausedFor = java.time.Duration.ofMinutes(55).plusSeconds(12)
            )
        )
        compose.onNodeWithText("5분 뒤 러닝을 자동으로 종료해요").assertExists()
        compose.onNodeWithText("55분째 일시정지 중이에요.").assertExists()
        compose.onNodeWithText("자동 종료까지 04:48").assertExists()
        compose.onNodeWithText("지금 종료").performClick()
        compose.onNodeWithText("재개").performClick()
        assertEquals(listOf("finishLong", "resume"), calls)
    }
}
