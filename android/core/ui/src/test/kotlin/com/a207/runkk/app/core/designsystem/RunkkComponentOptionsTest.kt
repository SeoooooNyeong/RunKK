package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 기존 디자인 시스템 부품에 더한 선택 인자(기본값은 지금 모양 그대로, S15P31A207-300). */
@RunWith(RobolectricTestRunner::class)
class RunkkComponentOptionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `새 아이콘은 24 격자이고 이름이 겹치지 않는다`() {
        val icons = listOf(
            RunkkIcons.Refresh,
            RunkkIcons.MoreHoriz,
            RunkkIcons.ArrowForward,
            RunkkIcons.Edit,
            RunkkIcons.ThumbsUp,
            RunkkIcons.ArrowUp
        )
        icons.forEach { icon ->
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24f, icon.viewportWidth)
        }
        assertEquals(
            listOf(
                "Runkk.Refresh",
                "Runkk.MoreHoriz",
                "Runkk.ArrowForward",
                "Runkk.Edit",
                "Runkk.ThumbsUp",
                "Runkk.ArrowUp"
            ),
            icons.map { it.name }
        )
    }

    @Test
    fun `그림자 색과 투명도 토큰은 두 벌 같고 꺼짐 값이 유리 값보다 옅다`() {
        listOf(RunkkColors.light, RunkkColors.dark).forEach { colors ->
            assertEquals(Color(0x29171A12), colors.shadow)
            assertEquals(Color(0x1F171A12), colors.shadowSoft)
        }
        assertTrue(RunkkAlpha.DisabledFaint < RunkkAlpha.DisabledContent)
        assertTrue(RunkkAlpha.DisabledContent < RunkkAlpha.Disabled)
        assertTrue(RunkkAlpha.Disabled < RunkkAlpha.Glass)
        assertEquals(listOf(4.dp, 6.dp, 8.dp), listOf(RunkkElevation.Low, RunkkElevation.Medium, RunkkElevation.High))
    }

    @Test
    fun `하단 바 버튼 크기는 높이 52에 14 Medium이고 그림 아이콘 버튼도 누를 수 있다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkButton(
                        "매칭 시작",
                        onClick = { clicks++ },
                        size = RunkkButtonSize.Bar,
                        style = RunkkButtonStyle.Purple,
                        modifier = Modifier.testTag("bar")
                    )
                    RunkkButton(
                        "꾸미기",
                        onClick = { clicks++ },
                        leadingIcon = rememberVectorPainter(RunkkIcons.Edit),
                        modifier = Modifier.testTag("painter")
                    )
                }
            }
        }
        assertEquals(14.sp, RunkkButtonSize.Bar.textStyle.fontSize)
        assertEquals(FontWeight.Medium, RunkkButtonSize.Bar.textStyle.fontWeight)
        compose.onNodeWithTag("bar").assertHeightIsEqualTo(52.dp)
        compose.onNodeWithTag("painter").assertHeightIsEqualTo(52.dp)
        compose.onNodeWithText("꾸미기").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `아이콘 버튼은 꺼지면 누를 수 없고 그림 아이콘도 받는다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkIconButton(
                        RunkkIcons.Refresh,
                        contentDescription = "새로고침",
                        onClick = { clicks++ },
                        isEnabled = false
                    )
                    RunkkIconButton(
                        rememberVectorPainter(RunkkIcons.MoreHoriz),
                        contentDescription = "더 보기",
                        onClick = { clicks++ },
                        visualSize = 36.dp
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("새로고침").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("더 보기").performClick()
        assertEquals(1, clicks)
        compose.onAllNodesWithTag(ICON_BUTTON_BODY_TAG, useUnmergedTree = true)[1].assertHeightIsEqualTo(36.dp)
    }

    @Test
    fun `고르기 칩은 크기마다 높이가 다르고 라디오 역할이면 고름으로 읽힌다`() {
        val picks = mutableListOf<Boolean>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column(Modifier.selectableGroup()) {
                    RunkkChoiceChip(
                        "주간",
                        isSelected = true,
                        onSelectedChange = { picks += it },
                        tone = RunkkChipTone.Lime,
                        size = RunkkChipSize.Medium,
                        selectionRole = Role.RadioButton
                    )
                    RunkkChoiceChip(
                        "5km",
                        isSelected = false,
                        onSelectedChange = { picks += it },
                        tone = RunkkChipTone.Violet,
                        size = RunkkChipSize.Large,
                        leadingIcon = RunkkIcons.ThumbsUp,
                        selectionRole = Role.RadioButton
                    )
                    RunkkChoiceChip("저녁", isSelected = false, onSelectedChange = {}, tone = RunkkChipTone.Ink)
                }
            }
        }
        val bodies = compose.onAllNodesWithTag(CHOICE_CHIP_BODY_TAG, useUnmergedTree = true)
        bodies[0].assertHeightIsEqualTo(36.dp)
        bodies[1].assertHeightIsEqualTo(48.dp)
        bodies[2].assertHeightIsEqualTo(32.dp)
        compose.onNodeWithText("주간").assertIsSelected()
        compose.onNodeWithText("5km").assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        assertEquals(listOf(true), picks)
    }

    @Test
    fun `테두리 통계 칸은 모서리 24·안쪽 14이고 라벨을 값 아래로 내릴 수 있다`() {
        lateinit var outlined: StatTileStyle
        compose.setContent {
            RunkkTheme(darkTheme = false, reduceMotion = true) {
                outlined = RunkkStatTileDefaults.Outlined
                RunkkStatTile(
                    "누적 거리",
                    StatTileValue("128.4"),
                    style = outlined.copy(isCentered = true, labelPosition = StatTileLabelPosition.Bottom),
                    leadingIcon = RunkkIcons.DirectionsRun
                )
            }
        }
        assertEquals(RunkkShapes.ExtraLarge, outlined.shape)
        assertEquals(PaddingValues(14.dp), outlined.contentPadding)
        assertEquals(6.dp, outlined.spacing)
        assertEquals(20.sp, outlined.valueStyle.fontSize)
        assertEquals(FontWeight.Bold, outlined.valueStyle.fontWeight)
        assertEquals(RunkkColors.light.border, outlined.borderColor)
        val value = compose.onNodeWithText("128.4", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("누적 거리", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(label.top >= value.bottom)
    }

    @Test
    fun `대화상자는 닫기를 주면 오른쪽 위 ✕로 닫는다`() {
        var closes = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkDialog(title = "초대 코드", onDismissRequest = {}, onClose = { closes++ }) {
                    RunkkDialogMessage("ABC123")
                }
            }
        }
        compose.onNodeWithContentDescription("닫기").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun `대화상자는 닫기를 주지 않으면 ✕가 없다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkDialog(title = "삭제할까요?", onDismissRequest = {}) { RunkkDialogMessage("되돌릴 수 없어요") }
            }
        }
        compose.onAllNodesWithContentDescription("닫기").assertCountEquals(0)
    }

    @Test
    fun `바텀시트는 손잡이를 달고 ✕를 뺄 수 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomSheet(onDismissRequest = {}, title = "대결 시작", hasDragHandle = true, hasCloseButton = false) {
                    RunkkButton("랜덤 매칭", onClick = {})
                }
            }
        }
        compose.onNodeWithText("대결 시작").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onAllNodesWithTag(SHEET_DRAG_HANDLE_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithContentDescription("닫기").assertCountEquals(0)
    }

    @Test
    fun `글자 링크는 밑줄과 글자 모양을 바꿀 수 있다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTextLink(
                    "탈퇴하기",
                    onClick = { clicks++ },
                    isUnderlined = true,
                    style = RunkkTextStyles.text(13, lineHeight = 18),
                    color = RunkkColors.TextSecondary
                )
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("탈퇴하기", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(TextDecoration.Underline, layouts.first().layoutInput.style.textDecoration)
        assertEquals(13.sp, layouts.first().layoutInput.style.fontSize)
        compose.onNodeWithText("탈퇴하기").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `메뉴 줄은 높이를 고정하고 아래 구분선을 그을 수 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkMenuRow(
                        "지난 크루런",
                        onClick = {},
                        minHeight = 52.dp,
                        horizontalPadding = 0.dp,
                        hasDivider = true,
                        modifier = Modifier.testTag("nav")
                    )
                    RunkkMenuRow("설정", onClick = {}, modifier = Modifier.testTag("menu"))
                }
            }
        }
        compose.onNodeWithTag("nav").assertHeightIsEqualTo(53.dp)
        val menu = compose.onNodeWithTag("menu").getUnclippedBoundsInRoot()
        assertTrue(menu.bottom - menu.top >= 48.dp)
    }

    @Test
    fun `불러오기는 높이를 주면 그 높이 칸에 그린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkLoading(height = 120.dp, modifier = Modifier.testTag("loading"))
                }
            }
        }
        compose.onNodeWithTag("loading").assertHeightIsEqualTo(120.dp)
        compose.onNodeWithContentDescription("불러오는 중")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun `넘김 점은 점 크기를 바꿀 수 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkPageDots(
                    current = 0,
                    count = 3,
                    description = "1 / 3",
                    dotSize = 8.dp,
                    modifier = Modifier.testTag("dots")
                )
            }
        }
        compose.onNodeWithTag("dots").assertHeightIsEqualTo(8.dp)
        compose.onNodeWithContentDescription("1 / 3").assertExists()
    }

    @Test
    fun `밑줄 탭은 밑줄 폭과 두께를 정할 수 있다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkUnderlineTabs(
                    listOf("얼굴", "배경"),
                    selectedIndex = 0,
                    onSelect = {},
                    indicatorWidth = 40.dp,
                    indicatorHeight = 2.dp,
                    hasBaseline = false
                )
            }
        }
        val lines = compose.onAllNodesWithTag(UNDERLINE_TAB_LINE_TAG, useUnmergedTree = true)
        lines[0].assertWidthIsEqualTo(40.dp).assertHeightIsEqualTo(2.dp)
        compose.onNodeWithText("얼굴").assertIsSelected()
    }

    @Test
    fun `반전 꼬리표는 높이 28 이상이다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBadge(
                    "승",
                    tone = RunkkBadgeTone.Inverted,
                    shape = RunkkShapes.Pill,
                    modifier = Modifier.testTag("win")
                )
            }
        }
        val bounds = compose.onNodeWithTag("win").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 28.dp)
    }

    @Test
    fun `선택지 줄은 잠긴 칸을 누를 수 없고 잠김으로 읽는다`() {
        val picks = mutableListOf<Int>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkSegmentRow(
                    listOf("개인", "크루"),
                    selectedIndex = 0,
                    onSelect = { picks += it },
                    tone = RunkkSegmentTone.Violet,
                    lockedIndices = setOf(1),
                    optionHeight = 36.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        compose.onNodeWithContentDescription("크루, 잠김").assertIsNotEnabled().performClick()
        compose.onAllNodesWithTag(CHIP_LOCK_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("개인").assertIsSelected()
        assertEquals(emptyList(), picks)
    }
}
