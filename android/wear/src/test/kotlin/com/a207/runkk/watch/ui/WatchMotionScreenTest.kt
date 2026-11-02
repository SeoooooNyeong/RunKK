package com.a207.runkk.watch.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.watch.battle.WatchResultHeadline
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 워치 화면 모션(W41, 피그마 프로토타입). 시계를 멈추고([androidx.compose.ui.test.MainTestClock.autoAdvance] 거짓) 상태를 바꾼 뒤
 * 시간을 정해 넘겨, 전환 가운데에 옛 화면·새 화면(옛 값·새 값)이 함께 있는지와 끝나면 옛 것이 없는지 본다. 저전력 표시면 바로 바뀐다.
 * 전환 가운데 프레임은 스크린샷으로 남긴다(기록: recordRoborazziLocalDebug, 검증: verifyRoborazziLocalDebug).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchMotionScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var flow: WatchFlow by mutableStateOf(WatchFlow.Main())
    private var ambient by mutableStateOf(false)

    private fun progress(meters: Long, seconds: Long, paused: Boolean = false) = RunProgress(
        elapsed = Duration.ofSeconds(seconds),
        distance = Distance(meters),
        currentPace = Pace(392),
        averagePace = Pace(390),
        signal = null,
        heartRate = HeartRateSample(Instant.EPOCH, 151),
        isPaused = paused
    )

    private val choices = listOf(null, 3_000L, 5_000L, 10_000L).map { WatchFlow.Choice(it?.let(::Distance)) }
    private val countdown = WatchFlow.Countdown(WatchMode.FREE, Distance(3_000), 3)
    private val selfRun = WatchFlow.Running(WatchMode.SELF, progress(1_500, 585), Distance(3_000), gapMeters = 5)
    private val freeRun = WatchFlow.Running(WatchMode.FREE, progress(1_500, 585), Distance(3_000), isPausable = true)

    /** [initial]을 그리고 끝날 때까지 기다린 뒤 시계를 멈춘다. */
    private fun show(initial: WatchFlow, isAmbient: Boolean = false) {
        flow = initial
        ambient = isAmbient
        compose.setContent { Host() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Composable
    private fun Host() {
        WatchTheme {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(WatchColors.Background)) {
                WatchRoot(
                    flow = flow,
                    companion = WatchUiState(),
                    actions = WatchActions.NONE,
                    isAmbient = ambient,
                    timeText = { TimeText(timeSource = FixedTime) }
                )
            }
        }
    }

    /** 상태를 [next]로 바꾸고 [afterMs]만큼 시간을 넘긴다(첫 프레임이 전환 시작 시각을 잡는다). */
    private fun change(next: WatchFlow, afterMs: Long) {
        compose.runOnUiThread {
            flow = next
            // 시계를 멈춰 두었으므로 바뀐 값을 바로 알린다(다음 프레임에 다시 구성된다).
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(afterMs)
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")

    @Test
    fun `메인에서 거리 선택으로 갈 때 새 화면이 옛 화면 위로 겹쳐 나타난다`() {
        show(WatchFlow.Main(WatchMode.FREE, Gender.FEMALE))
        change(WatchFlow.FreeGoal(choices), afterMs = 100)
        capture("Q1-F1_watch_main_to_free_transition")
        compose.onNodeWithText("달리기 시작").assertExists()
        compose.onNodeWithContentDescription("자유런", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithContentDescription("자유런", substring = true).assertDoesNotExist()
        compose.onNodeWithText("달리기 시작").assertExists()
    }

    @Test
    fun `저전력 표시에서는 겹치지 않고 바로 바뀐다`() {
        show(countdown, isAmbient = true)
        change(freeRun, afterMs = 3 * FRAME_MS)
        compose.onNodeWithText("3").assertDoesNotExist()
        compose.onNodeWithText("1.50").assertExists()
    }

    @Test
    fun `움직임을 줄이면 화면 안 값도 바로 바뀐다`() {
        show(WatchFlow.FreeGoal(choices), isAmbient = true)
        change(WatchFlow.FreeGoal(choices, selected = 1), afterMs = 3 * FRAME_MS)
        compose.onNodeWithText("∞").assertDoesNotExist()
        compose.onNodeWithText("3").assertExists()
    }

    @Test
    fun `쓸어 넘겨 뒤로 가면 겹쳐 나타나지 않고 바로 바뀐다`() {
        flow = WatchFlow.FreeGoal(choices)
        var dismissed = false
        compose.setContent {
            WatchTheme {
                WatchRoot(
                    flow = flow,
                    companion = WatchUiState(),
                    actions = WatchActions(
                        onBack = {
                            dismissed = true
                            flow = WatchFlow.Main(WatchMode.FREE, Gender.FEMALE)
                        }
                    ),
                    timeText = { TimeText(timeSource = FixedTime) }
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { swipeRight() }
        // 쓸기가 끝나 뒤로 갈 때까지 한 프레임씩 넘긴다.
        repeat(MAX_SWIPE_FRAMES) { if (!dismissed) compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(3 * FRAME_MS)
        compose.onNodeWithText("달리기 시작").assertDoesNotExist()
        compose.onNodeWithContentDescription("자유런", substring = true).assertExists()
    }

    @Test
    fun `거리 다이얼을 넘기면 옛 숫자와 새 숫자가 함께 움직이다 새 숫자만 남는다`() {
        show(WatchFlow.FreeGoal(choices))
        change(WatchFlow.FreeGoal(choices, selected = 1), afterMs = 100)
        compose.onNodeWithText("∞").assertExists()
        compose.onNodeWithText("3").assertExists()
        capture("F1-F1-3_watch_dial_transition")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithText("∞").assertDoesNotExist()
        compose.onNodeWithText("3").assertExists()
    }

    @Test
    fun `카운트다운 숫자는 새 숫자가 들어오며 옛 숫자가 사라진다`() {
        show(countdown)
        change(countdown.copy(secondsLeft = 2), afterMs = 140)
        compose.onNodeWithText("3").assertExists()
        compose.onNodeWithText("2").assertExists()
        capture("FC3_watch_countdown_transition")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithText("3").assertDoesNotExist()
        compose.onNodeWithText("2").assertExists()
    }

    @Test
    fun `측정 중 역전 띠는 나타났다가 없어지면 흐려지며 사라진다`() {
        show(selfRun)
        change(selfRun.copy(isLeadChanged = true, leadKind = LeadKind.OVERTOOK), afterMs = 100)
        compose.onNodeWithText("역전!").assertExists()
        capture("SR3_watch_run_lead_banner_transition")
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        change(selfRun, afterMs = 60)
        compose.onNodeWithText("역전!").assertExists()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithText("역전!").assertDoesNotExist()
    }

    @Test
    fun `측정 거리가 바뀌면 옛 값이 빠지고 새 값이 올라온다`() {
        show(freeRun)
        change(freeRun.copy(progress = progress(1_510, 588)), afterMs = 100)
        compose.onNodeWithText("1.50").assertExists()
        compose.onNodeWithText("1.51").assertExists()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.onNodeWithText("1.50").assertDoesNotExist()
    }

    @Test
    fun `측정 거리가 바뀌는 동안 km 단위는 제자리에 있다`() {
        show(freeRun)
        val before = compose.onNodeWithText("km").getUnclippedBoundsInRoot()
        for (step in 1..ROLL_STEPS) {
            change(freeRun.copy(progress = progress(1_500L + step * 10, 588)), afterMs = 60)
            assertEquals(before, compose.onNodeWithText("km").getUnclippedBoundsInRoot())
        }
    }

    @Test
    fun `일시정지로 들어가면 측정 화면 위로 일시정지 화면이 나타난다`() {
        show(freeRun)
        change(WatchFlow.Paused(progress(1_500, 585, paused = true), Distance(3_000)), afterMs = 120)
        compose.onNodeWithText("일시정지").assertExists()
        capture("FR3-FP3_watch_pause_transition")
    }

    @Test
    fun `결과 화면은 머리말과 값 줄이 차례로 나타난다`() {
        show(freeRun)
        val summary = RunSummaryView(Distance(3_000), Duration.ofSeconds(1_170), Pace(390), 154)
        change(WatchFlow.Result(WatchResultHeadline.Goal, summary), afterMs = 180)
        compose.onNodeWithText("완료").assertExists()
        capture("FR3-FG3_watch_result_reveal_transition")
    }

    @Test
    fun `매칭 완료는 매칭 중 위로 나타나며 얼굴과 VS가 들어온다`() {
        show(WatchFlow.Matching("SILVER", Distance(3_000), waitedSeconds = 7))
        change(
            WatchFlow.Matched("SILVER", Distance(3_000), "민준", myGender = Gender.FEMALE, opponentGender = Gender.MALE),
            afterMs = 180
        )
        compose.onNodeWithText("매칭 상대를 찾았어요!").assertExists()
        capture("BMA-BMB_watch_matched_transition")
    }
}

/** 찍을 때마다 바뀌지 않게 고정한 시각. */
private object FixedTime : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}

/** 쓸기가 끝나기를 기다리는 가장 많은 프레임 수. */
private const val MAX_SWIPE_FRAMES = 120

/** 한 프레임(60Hz). */
private const val FRAME_MS = 16L

/** 전환이 다 끝날 만큼(가장 긴 320ms + 차례 간격 180ms + 여유). */
private const val SETTLE_MS = 700L

/** km 제자리 시험에서 거리를 바꾸는 횟수(전환 중간마다 본다). */
private const val ROLL_STEPS = 4
