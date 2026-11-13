package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 이벤트 화면에서 디자인 시스템으로 옮긴 부품(피그마 RunKK / 이벤트 컴포넌트 62268:2782)의 동작과 접근성. */
@RunWith(RobolectricTestRunner::class)
class RunkkEventComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `밑줄 탭은 탭 역할과 선택 상태를 갖고 누르면 그 칸 번호를 넘긴다`() {
        var selected by mutableIntStateOf(0)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkUnderlineTabs(listOf("진행 중", "지난 이벤트"), selectedIndex = selected, onSelect = { selected = it })
            }
        }
        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onNodeWithText("진행 중").assertIsSelected().assert(isTab).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("지난 이벤트").assertIsNotSelected().performClick()
        assertEquals(1, selected)
        compose.onNodeWithText("지난 이벤트").assertIsSelected()
    }

    @Test
    fun `넘김 점은 점 하나하나가 아니라 순서 문구 하나로 읽힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkPageDots(current = 1, count = 3, description = "2 / 3", modifier = Modifier.testTag("dots"))
            }
        }
        compose.onNodeWithContentDescription("2 / 3").assertExists()
        compose.onAllNodesWithTag("dots").assertCountEquals(1)
    }

    @Test
    fun `상태 칩과 D-day 배지는 받은 문구를 그대로 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkStatusChip("새 이벤트", RunkkStatusChipTone.Violet)
                    RunkkStatusChip("참여 중", RunkkStatusChipTone.Lime)
                    RunkkDdayBadge("D-8")
                }
            }
        }
        compose.onNodeWithText("새 이벤트").assertExists()
        compose.onNodeWithText("참여 중").assertExists()
        compose.onNodeWithText("D-8").assertExists()
    }

    @Test
    fun `알림 점은 지름 12이고 테두리는 자리를 차지하지 않는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkNotificationDot(Modifier.testTag("dot")) }
        }
        compose.onNodeWithTag("dot").assertWidthIsEqualTo(12.dp)
    }

    @Test
    fun `알림 점을 단 아이콘 버튼은 새 소식 상태를 읽힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = {}, hasBadge = true)
            }
        }
        compose.onNodeWithContentDescription("알림함")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "새 소식 있음"))
    }

    @Test
    fun `코치 셀럽은 얼굴 그림이 있어 둥근 얼굴로, 나머지는 서 있는 그림으로 그린다`() {
        assertNotNull(CelebrityCharacter.JOO.face)
        assertNotNull(CelebrityCharacter.KIM.face)
        assertNull(CelebrityCharacter.LEE.face)
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    CelebrityPortrait(figureKey = "joo")
                    CelebrityPortrait(figureKey = "lee")
                    CelebrityPortrait(figureKey = null)
                }
            }
        }
        val figures = compose.onAllNodesWithTag(CELEBRITY_FIGURE_TAG, useUnmergedTree = true)
        figures.assertCountEquals(3)
        repeat(3) { figures[it].assertWidthIsEqualTo(58.dp) }
    }
}
