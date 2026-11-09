package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 피그마 맞춤 W1 공통 부품(상단 바·알약 버튼·확인 대화상자·바텀시트·칩·세그먼트·메타 줄, S15P31A207-292). */
@RunWith(RobolectricTestRunner::class)
class RunkkFigmaComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `상단 바는 높이 57에 제목을 가운데 두고 원 없는 뒤로 버튼을 단다`() {
        var backs = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTopBar(title = "크루런 결과", onBack = { backs++ }, modifier = Modifier.testTag("top"))
            }
        }
        compose.onNodeWithTag("top").assertHeightIsEqualTo(57.dp)
        val bar = compose.onNodeWithTag("top").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("크루런 결과").getUnclippedBoundsInRoot()
        assertEquals(((bar.left + bar.right) / 2).value, ((title.left + title.right) / 2).value, 1f)
        compose.onNodeWithContentDescription("뒤로").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `알약 버튼은 크기마다 피그마 높이이고 꺼지면 누를 수 없다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkButton("확인", onClick = { clicks++ }, modifier = Modifier.testTag("large"))
                    RunkkButton(
                        "취소",
                        onClick = { clicks++ },
                        style = RunkkButtonStyle.Outline,
                        size = RunkkButtonSize.Medium,
                        modifier = Modifier.testTag("medium")
                    )
                    RunkkButton("다음", onClick = { clicks++ }, isEnabled = false)
                }
            }
        }
        compose.onNodeWithTag("large").assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("medium").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("다음").assertIsNotEnabled()
        compose.onNodeWithText("확인").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `확인 대화상자는 제목·본문과 취소·확인 버튼을 보이고 누른 버튼의 행동을 부른다`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkConfirmDialog(
                    title = "크루를 탈퇴할까요?",
                    message = "탈퇴 후 24시간 동안 다른 크루에 가입할 수 없어요.",
                    confirmLabel = "탈퇴",
                    onConfirm = { calls += "confirm" },
                    onDismiss = { calls += "dismiss" }
                )
            }
        }
        compose.onNode(isDialog()).assertExists()
        compose.onNodeWithText("크루를 탈퇴할까요?").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("탈퇴 후 24시간 동안 다른 크루에 가입할 수 없어요.").assertExists()
        compose.onNodeWithText("취소").performClick()
        compose.onNodeWithText("탈퇴").performClick()
        assertEquals(listOf("dismiss", "confirm"), calls)
    }

    @Test
    fun `대화상자 버튼은 높이 48 알약이다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkConfirmDialog(title = "삭제할까요?", message = "", confirmLabel = "삭제", onConfirm = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("삭제").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `바텀시트는 제목과 닫기를 보이고 닫기는 시트를 닫는다`() {
        var dismissed = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomSheet(onDismissRequest = { dismissed++ }, title = "조건 설정하기") {
                    RunkkButton("적용하기", onClick = {}, size = RunkkButtonSize.Medium)
                }
            }
        }
        compose.onNodeWithText("조건 설정하기").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithContentDescription("닫기").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed)
    }

    @Test
    fun `고르기 칩은 높이 32이고 고르면 체크 없이 채운다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkChoiceChip("저녁", isSelected = true, onSelectedChange = {}, modifier = Modifier.testTag("on"))
                    RunkkChoiceChip("아침", isSelected = false, onSelectedChange = {})
                }
            }
        }
        compose.onAllNodesWithTag(CHIP_CHECK_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("on").assertIsOn()
        compose.onAllNodesWithTag(CHOICE_CHIP_BODY_TAG, useUnmergedTree = true)[0].assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun `세그먼트는 탭 역할로 고른 칸을 알리고 누르면 바꾼다`() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkSegmented(
                    listOf("공개", "비공개"),
                    selectedIndex = 0,
                    onSelect = { picked += it },
                    modifier = Modifier.testTag("seg")
                )
            }
        }
        compose.onNodeWithTag("seg").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("공개").assertIsSelected()
        // 보이는 칸은 36이지만 누르는 칸은 48이다(리뷰 지적, 이전 RunkkTabRow와 같다).
        compose.onNodeWithText("비공개").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("비공개").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText("비공개").performClick()
        assertEquals(listOf(1), picked)
    }

    @Test
    fun `메타 줄은 아이콘을 읽지 않고 항목을 쉼표로 이어 읽는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                MetaRow(
                    listOf(MetaItem(RunkkIcons.CalendarToday, "2026.10.12 개설"), MetaItem(RunkkIcons.Group, "7명")),
                    modifier = Modifier.testTag("meta")
                )
            }
        }
        compose.onNodeWithContentDescription("2026.10.12 개설, 7명").assertExists()
    }

    @Test
    fun `메타 아이콘 8종은 14 격자, 뒤로 화살표는 24 격자다`() {
        val meta = listOf(
            RunkkIcons.CalendarToday,
            RunkkIcons.Group,
            RunkkIcons.Schedule,
            RunkkIcons.DirectionsRun,
            RunkkIcons.CheckCircle,
            RunkkIcons.EmojiEvents,
            RunkkIcons.TrendingUp,
            RunkkIcons.Person
        )
        assertEquals(List(8) { 14f }, meta.map { it.viewportWidth })
        assertEquals(8, meta.map { it.name }.toSet().size)
        assertEquals(24f, RunkkIcons.ArrowBack.viewportWidth)
    }
}
