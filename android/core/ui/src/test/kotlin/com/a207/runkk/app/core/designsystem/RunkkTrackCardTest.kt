package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.model.RunViewMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 트랙 카드, 진행 막대, 보기 전환([임시:D-성-313], [임시:D-성-268], [임시:D-성-306]). */
@RunWith(RobolectricTestRunner::class)
class RunkkTrackCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun card(
        layout: TrackLayout.Result,
        aheadChip: String? = null,
        behindChip: String? = null,
        rulerLabel: String? = null
    ) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTrackCard(
                    layout = layout,
                    myTag = "나",
                    otherTag = "바람처럼",
                    myAvatar = {},
                    otherAvatar = {},
                    modifier = Modifier.size(width = 320.dp, height = 300.dp),
                    myPace = "5'30\"",
                    aheadChip = aheadChip,
                    behindChip = behindChip,
                    rulerLabel = rulerLabel
                )
            }
        }
    }

    private fun centerX(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().let {
        (it.left + it.right) /
            2
    }

    @Test
    fun `나는 폭의 40퍼센트에 있고 앞선 상대는 거리만큼 오른쪽에 그린다`() {
        card(TrackLayout.of(aheadMeters = 12.0, remainingMeters = 2_000.0), rulerLabel = "12 m")

        assertEquals(128.dp, centerX(TRACK_ME_TAG))
        assertEquals(256.dp, centerX(TRACK_OTHER_TAG))
        compose.onNodeWithText("바람처럼").assertExists()
        compose.onNodeWithText("5'30\"").assertExists()
        compose.onNodeWithText("12 m").assertExists()
        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)
    }

    @Test
    fun `뒤선 상대는 내 왼쪽에 그린다`() {
        card(TrackLayout.of(aheadMeters = -6.0, remainingMeters = 2_000.0))

        assertTrue(centerX(TRACK_OTHER_TAG) < centerX(TRACK_ME_TAG))
    }

    @Test
    fun `상대가 화면 밖이면 아바타 없이 그쪽 가장자리 칩을 그린다`() {
        card(
            TrackLayout.of(aheadMeters = 240.0, remainingMeters = 2_000.0),
            aheadChip = "바람처럼 +240 m",
            rulerLabel = "240 m"
        )

        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
        compose.onNodeWithTag(TRACK_AHEAD_TAG).assertExists()
        compose.onNodeWithText("바람처럼 +240 m").assertExists()
        compose.onAllNodesWithTag(TRACK_BEHIND_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_RULER_TAG).assertCountEquals(0)
    }

    @Test
    fun `결승선은 화면 안에 들어올 때만 그린다`() {
        card(TrackLayout.of(aheadMeters = 3.0, remainingMeters = 9.0))

        assertEquals(224.dp, centerX(TRACK_FINISH_TAG))
    }

    @Test
    fun `결승선이 화면 밖이면 그리지 않는다`() {
        card(TrackLayout.of(aheadMeters = 3.0, remainingMeters = 25.0))

        compose.onAllNodesWithTag(TRACK_FINISH_TAG).assertCountEquals(0)
    }

    @Test
    fun `격차를 모르면 상대와 거리 자 알약을 그리지 않는다`() {
        card(TrackLayout.of(aheadMeters = null, remainingMeters = 2_000.0), rulerLabel = null)

        compose.onNodeWithTag(TRACK_ME_TAG).assertExists()
        compose.onAllNodesWithTag(TRACK_OTHER_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(TRACK_RULER_TAG).assertCountEquals(0)
    }

    @Test
    fun `보기 전환은 고른 칸을 알리고 누르면 그 보기를 넘긴다`() {
        var mode by mutableStateOf(RunViewMode.TRACK)
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkViewModeToggle(selected = mode, onSelect = { mode = it }) }
        }

        compose.onNodeWithTag(viewModeTag(RunViewMode.TRACK)).assertIsSelected()
        compose.onNodeWithTag(viewModeTag(RunViewMode.MAP)).assertIsNotSelected()
        compose.onNodeWithText("지도").performClick()

        assertEquals(RunViewMode.MAP, mode)
        compose.onNodeWithTag(viewModeTag(RunViewMode.MAP)).assertIsSelected()
    }

    @Test
    fun `진행 막대는 참가자마다 점을 찍고 거리 글자를 적는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Box(Modifier.size(width = 320.dp, height = 80.dp)) {
                    RunkkTrackProgressBar(
                        dots = listOf(
                            ProgressDot(0.47f, isMine = true),
                            ProgressDot(0.5f, false),
                            ProgressDot(1.4f, false)
                        ),
                        leading = "2.34 / 5 km",
                        trailing = "남은 거리 2.66 km"
                    )
                }
            }
        }

        compose.onAllNodesWithTag(TRACK_DOT_MINE_TAG).assertCountEquals(1)
        compose.onAllNodesWithTag(TRACK_DOT_OTHER_TAG).assertCountEquals(2)
        compose.onNodeWithText("2.34 / 5 km").assertExists()
        compose.onNodeWithText("남은 거리 2.66 km").assertExists()
    }
}
