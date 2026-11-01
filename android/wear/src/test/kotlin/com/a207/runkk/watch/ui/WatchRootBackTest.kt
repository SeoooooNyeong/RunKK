package com.a207.runkk.watch.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.run.RunProgress
import com.a207.runkk.watch.battle.WatchResultHeadline
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 하드웨어 뒤로 키(상태도 2.4.25, WR12-2): 뒤로를 받지 않는 상태(카운트다운·측정·일시정지·매칭 완료·결과)에서는 키를 소비해 앱이 닫히지
 * 않는다. 거리 선택·셀럽 목록·매칭 중은 [WatchActions.onBack]으로 넘기고, 메인·미로그인·동행 표시는 시스템 기본 동작(앱 닫기)이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchRootBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var backs = 0

    private val progress = RunProgress(
        elapsed = Duration.ofSeconds(300),
        distance = Distance(1_000),
        currentPace = Pace(300),
        averagePace = Pace(300),
        signal = null,
        heartRate = null,
        isPaused = false
    )

    private var shown: WatchFlow? by mutableStateOf(null)

    /** [flow]를 그리고 하드웨어 뒤로 키를 누른다. 화면 내용은 한 번만 붙이고 상태만 바꾼다. 앱이 닫히면 참이다. */
    private fun pressBack(flow: WatchFlow): Boolean {
        backs = 0
        if (shown == null) {
            compose.setContent {
                WatchTheme {
                    shown?.let {
                        WatchRoot(flow = it, companion = WatchUiState(), actions = WatchActions(onBack = { backs++ }))
                    }
                }
            }
        }
        compose.runOnUiThread { shown = flow }
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        return compose.activity.isFinishing
    }

    @Test
    fun `측정 카운트다운 매칭 완료 결과에서는 뒤로 키를 소비하고 앱을 닫지 않는다`() {
        val summary = RunSummaryView(Distance(1_000), Duration.ofSeconds(300), Pace(300), null)
        val held = listOf(
            WatchFlow.Countdown(WatchMode.BATTLE, Distance(3_000), 2),
            WatchFlow.Running(WatchMode.FREE, progress, goal = null, isPausable = true),
            WatchFlow.Paused(progress, goal = null),
            WatchFlow.Matched("SILVER", Distance(3_000), "민준", null, null),
            WatchFlow.Result(WatchResultHeadline.Finish, summary)
        )

        held.forEach { flow ->
            assertFalse(pressBack(flow), "$flow 에서 앱이 닫혔다")
            assertEquals(0, backs, "$flow 에서 뒤로가 넘어갔다")
        }
    }

    @Test
    fun `매칭 중 뒤로 키는 뒤로 동작으로 넘긴다`() {
        assertFalse(pressBack(WatchFlow.Matching("SILVER", Distance(3_000))))
        assertEquals(1, backs)
    }

    @Test
    fun `메인에서 뒤로 키는 앱을 닫는다`() {
        assertTrue(pressBack(WatchFlow.Main()))
    }
}
