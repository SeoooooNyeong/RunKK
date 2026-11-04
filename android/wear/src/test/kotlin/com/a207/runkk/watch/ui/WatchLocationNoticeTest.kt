package com.a207.runkk.watch.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.watch.battle.RecordOpponent
import com.a207.runkk.watch.cache.CachedTrack
import com.a207.runkk.watch.testing.celebrityTrack
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 워치 위치 꺼짐 안내(W52)를 누르면 위치 설정을 연다. 거리 선택(다이얼·목록)과 셀럽 목록에서 같다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w192dp-h192dp-round-xhdpi", application = Application::class)
class WatchLocationNoticeTest {
    @get:Rule
    val compose = createComposeRule()

    private var opens = 0

    private fun show(flow: WatchFlow) {
        compose.setContent {
            WatchTheme {
                WatchRoot(
                    flow = flow,
                    companion = WatchUiState(),
                    actions = WatchActions(onOpenLocationSettings = { opens++ })
                )
            }
        }
        compose.waitForIdle()
    }

    private fun clickNotice() {
        compose.onNodeWithText(NOTICE)
            .assert(SemanticsMatcher("위치 설정 열기 라벨") { it.config[SemanticsActions.OnClick].label == LABEL })
            .performClick()
        compose.waitForIdle()
    }

    @Test
    fun `자유런 거리 선택의 위치 안내를 누르면 위치 설정을 연다`() {
        val choices = WatchFlowViewModel.FREE_GOALS.map { WatchFlow.Choice(it) }
        show(WatchFlow.FreeGoal(choices, notice = WatchFlow.Notice.LOCATION_OFF))

        clickNotice()

        assertEquals(1, opens)
    }

    @Test
    fun `셀프런 거리 목록의 위치 안내를 누르면 위치 설정을 연다`() {
        val choices = WatchFlowViewModel.SELF_DISTANCES.map {
            WatchFlow.Choice(it, record = Duration.ofSeconds(900))
        }
        show(WatchFlow.SelfDistance(choices, notice = WatchFlow.Notice.LOCATION_OFF))

        clickNotice()

        assertEquals(1, opens)
    }

    @Test
    fun `셀럽 목록의 위치 안내를 누르면 위치 설정을 연다`() {
        val celebrity = RecordOpponent(CachedTrack(celebrityTrack(1, 5_000, 1_500), Instant.EPOCH))
        show(WatchFlow.CelebList(Distance(5_000), listOf(celebrity), notice = WatchFlow.Notice.LOCATION_OFF))

        clickNotice()

        assertEquals(1, opens)
    }

    @Test
    fun `메인의 위치 안내를 누르면 위치 설정을 연다(W53)`() {
        show(WatchFlow.Main(WatchMode.FREE, gps = WatchFlow.GpsStatus.LOCATION_OFF))

        clickNotice()

        assertEquals(1, opens)
    }

    private companion object {
        const val NOTICE = "워치 위치를 켜 주세요"
        const val LABEL = "위치 설정 열기"
    }
}
