package com.a207.runkk.watch.ui

import android.app.Application
import android.os.PowerManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 메인 캐러셀 전환(피그마 Q1~Q4 Smart Animate 320ms). 작은 카드를 누르면 바로 바뀌지 않고 곡선을 따라 가운데로 오며, 마지막에서 처음으로도
 * 이어진다. 저전력 표시면 애니메이션 없이 바로 바뀐다. 가운데 카드는 "이름, 설명"으로 읽힌다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchModeCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(mode: WatchMode, isAmbient: Boolean = false) {
        compose.setContent {
            WatchTheme { WatchModeCarousel(WatchFlow.Main(mode), onModeSelected = {}, isAmbient = isAmbient) }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun centerCount(description: String) = compose.onAllNodesWithContentDescription(description)

    @Test
    fun `작은 카드를 누르면 320ms 동안 미끄러진 뒤 가운데 카드가 된다`() {
        show(WatchMode.FREE)
        compose.onNodeWithContentDescription("배틀런").performClick()
        compose.mainClock.advanceTimeBy(32)
        centerCount("배틀런, 1:1 랜덤 매칭 달리기").assertCountEquals(0)
        compose.mainClock.advanceTimeBy(400)
        centerCount("배틀런, 1:1 랜덤 매칭 달리기").assertCountEquals(1)
        centerCount("자유런, 제한 없이 내 페이스로").assertCountEquals(0)
    }

    @Test
    fun `마지막 셀럽런 다음 자유런으로 이어서 넘어간다`() {
        show(WatchMode.CELEB)
        compose.onNodeWithContentDescription("자유런").performClick()
        compose.mainClock.advanceTimeBy(400)
        centerCount("자유런, 제한 없이 내 페이스로").assertCountEquals(1)
    }

    @Test
    fun `저전력 표시면 애니메이션 없이 바로 바뀐다`() {
        show(WatchMode.FREE, isAmbient = true)
        compose.onNodeWithContentDescription("배틀런").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        centerCount("배틀런, 1:1 랜덤 매칭 달리기").assertCountEquals(1)
    }

    @Test
    fun `위로 한 칸쯤 끌었다 놓으면 다음 카드가 가운데가 된다(WR15-6)`() {
        show(WatchMode.FREE)
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -56.dp.toPx()))
            up()
        }
        compose.mainClock.advanceTimeBy(500)
        centerCount("배틀런, 1:1 랜덤 매칭 달리기").assertCountEquals(1)
    }

    @Test
    fun `조금만 끌고 놓으면 제자리로 붙는다(WR15-6)`() {
        show(WatchMode.FREE)
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -18.dp.toPx()))
            up()
        }
        compose.mainClock.advanceTimeBy(500)
        centerCount("자유런, 제한 없이 내 페이스로").assertCountEquals(1)
    }

    @Test
    fun `끌다 취소하면 가까운 칸에 붙는다(WR15-6)`() {
        show(WatchMode.FREE)
        compose.onRoot().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -18.dp.toPx()))
            cancel()
        }
        compose.mainClock.advanceTimeBy(500)
        centerCount("자유런, 제한 없이 내 페이스로").assertCountEquals(1)
    }

    @Test
    fun `절전 모드면 움직임 없이 바로 바뀐다(WR15-6)`() {
        val power = ApplicationProvider.getApplicationContext<Application>().getSystemService(PowerManager::class.java)
        shadowOf(power).setIsPowerSaveMode(true)
        show(WatchMode.FREE)
        compose.onNodeWithContentDescription("배틀런").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        centerCount("배틀런, 1:1 랜덤 매칭 달리기").assertCountEquals(1)
    }
}
