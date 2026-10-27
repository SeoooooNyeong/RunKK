package com.a207.runkk.watch.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 워치 화면 스크린샷(와이어프레임 K1~K5, 가짜 WatchUiState). 작은 원형 워치(192dp, 384px)에 원으로 잘라 찍는다.
 * 앱 프로세스(Hilt RunkkWatchApp) 대신 빈 Application으로 띄운다. 기록: recordRoborazziLocalDebug, 검증: verifyRoborazziLocalDebug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = WATCH_SMALL_ROUND, application = Application::class)
class WatchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val running = WatchUiState(
        isPhoneConnected = true,
        isRunning = true,
        elapsedSeconds = 1_421,
        distanceMeters = 4_121,
        paceSecondsPerKm = 342,
        heartRateBpm = 152
    )

    /** [isScrolledToEnd]면 크라운으로 끝까지 내린 모습(아래 조작 줄)을 찍는다. */
    private fun capture(name: String, state: WatchUiState, isScrolledToEnd: Boolean = false) {
        compose.setContent {
            WatchTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(WatchColors.Background)
                ) {
                    WatchRoot(
                        state = state,
                        actions = WatchActions.NONE,
                        timeText = { TimeText(timeSource = FixedTimeSource) }
                    )
                }
            }
        }
        if (isScrolledToEnd) {
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    fun home() = capture(
        "K1_watch_home",
        WatchUiState(isPhoneConnected = true, planDistanceMeters = 5_000, planPaceSecondsPerKm = 360)
    )

    @Test
    fun homeStartPending() = capture(
        "K1_watch_home_start_pending",
        WatchUiState(isPhoneConnected = true, isStartPending = true)
    )

    @Test
    fun run() = capture("K2_watch_run", running.copy(isPausable = true))

    @Test
    fun runActions() = capture("K2_watch_run_actions", running.copy(isPausable = true), isScrolledToEnd = true)

    /** 대결·크루런(일시정지 없음)에서 폰 연결이 끊겼을 때. */
    @Test
    fun runLinkLost() = capture("K2_watch_run_link_lost", running.copy(isLinkLost = true, heartRateBpm = null))

    @Test
    fun noLink() = capture("K3_watch_no_link", WatchUiState(isPhoneConnected = false))

    @Test
    fun paused() = capture(
        "K4_watch_paused",
        running.copy(isPaused = true, isPausable = true, paceSecondsPerKm = null, heartRateBpm = 138)
    )

    @Test
    fun pausedActions() = capture(
        "K4_watch_paused_actions",
        running.copy(isPaused = true, isPausable = true, paceSecondsPerKm = null, heartRateBpm = 138),
        isScrolledToEnd = true
    )

    @Test
    fun battleAhead() = capture(
        "K5_watch_battle_ahead",
        running.copy(
            elapsedSeconds = 940,
            distanceMeters = 2_840,
            proximity = WatchProximity(gapMeters = 18, isAhead = true, intervalMillis = 600)
        )
    )

    @Test
    fun battleBehind() = capture(
        "K5_watch_battle_behind",
        running.copy(
            elapsedSeconds = 940,
            distanceMeters = 2_820,
            proximity = WatchProximity(gapMeters = 72, isAhead = false, intervalMillis = 1_510)
        )
    )
}

/** 찍을 때마다 바뀌지 않게 고정한 시각. */
private object FixedTimeSource : TimeSource {
    @Composable
    override fun currentTime(): String = "10:09"
}

/** 작은 원형 워치(Wear OS small round, 192dp = 384px). */
private const val WATCH_SMALL_ROUND = "w192dp-h192dp-round-xhdpi"
