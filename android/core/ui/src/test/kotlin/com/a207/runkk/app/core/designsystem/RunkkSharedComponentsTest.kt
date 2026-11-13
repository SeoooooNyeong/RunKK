package com.a207.runkk.app.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 새 공용 부품(꼬리표·구역 머리·기간 넘기기·안내 카드·하단 바·라디오·사람 줄·도는 표시·꺾쇠 링크·측정 HUD, S15P31A207-300). */
@RunWith(RobolectricTestRunner::class)
class RunkkSharedComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `꼬리표 알약은 Medium이 높이 28이고 읽기 문구로 읽힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkTagPill(
                        "주말 아침",
                        tone = RunkkTagTone.LimeTint,
                        size = RunkkTagSize.Medium,
                        modifier = Modifier.testTag("medium")
                    )
                    RunkkTagPill("5km", spoken = "5킬로미터", leadingIcon = RunkkIcons.DirectionsRun)
                }
            }
        }
        compose.onNodeWithTag("medium").assertHeightIsEqualTo(28.dp)
        compose.onNodeWithContentDescription("5킬로미터").assertExists()
    }

    @Test
    fun `구역 머리는 제목과 개수를 한 제목으로 읽고 오른쪽 행동을 둔다`() {
        var edits = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkSectionHeader("크루원", count = "7") {
                    RunkkTextLink("편집", onClick = { edits++ })
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(hasText("크루원"))
            .assert(hasText("7"))
        compose.onNodeWithText("편집").performClick()
        assertEquals(1, edits)
    }

    @Test
    fun `기간 넘기기는 콜백이 없는 쪽 화살표를 누를 수 없다`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkPeriodNavigator(
                        "2026년 10월",
                        onPrevious = null,
                        onNext = { calls += "next" },
                        onLabelClick = { calls += "label" },
                        labelClickLabel = "기간 고르기"
                    )
                    RunkkPeriodNavigator(
                        "11월 2주",
                        onPrevious = { calls += "prev" },
                        onNext = null,
                        style = RunkkPeriodNavigatorStyle.Circled,
                        previousDescription = "이전 주",
                        nextDescription = "다음 주"
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("이전").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("다음").performClick()
        compose.onNodeWithText("2026년 10월").performClick()
        compose.onNodeWithContentDescription("다음 주").assertIsNotEnabled()
        compose.onNodeWithContentDescription("이전 주").performClick()
        assertEquals(listOf("next", "label", "prev"), calls)
    }

    @Test
    fun `안내 카드는 제목·본문과 행동 알약을 보인다`() {
        var retries = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkNoticeCard(
                    "결과를 정리하고 있어요",
                    title = "잠시만요",
                    tone = RunkkNoticeTone.Violet,
                    icon = RunkkIcons.Bell,
                    actionLabel = "다시 확인",
                    onAction = { retries++ },
                    isLive = true
                )
            }
        }
        compose.onNodeWithText("잠시만요").assertExists()
        compose.onNodeWithText("결과를 정리하고 있어요").assertExists()
        compose.onNodeWithText("다시 확인").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `하단 행동 바는 위 12·아래 24 안쪽에 52 버튼을 담는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomActionBar(Modifier.testTag("bar")) {
                    RunkkButton("매칭 시작", onClick = {}, style = RunkkButtonStyle.Purple, size = RunkkButtonSize.Bar)
                }
            }
        }
        compose.onNodeWithTag("bar").assertHeightIsEqualTo(88.dp)
    }

    @Test
    fun `라디오 줄은 라디오 단추로 읽히고 22 표시를 단다`() {
        var picked = ""
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column(Modifier.selectableGroup()) {
                    RunkkRadioRow("매주", isSelected = true, onClick = {
                        picked = "매주"
                    }, modifier = Modifier.testTag("row"))
                    RunkkRadioRow("격주", isSelected = false, onClick = { picked = "격주" }, supporting = "2주마다")
                }
            }
        }
        compose.onNodeWithText("매주").assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithTag("row").assertHeightIsEqualTo(52.dp)
        compose.onAllNodesWithTag(RADIO_MARK_TAG, useUnmergedTree = true)[0]
            .assertWidthIsEqualTo(22.dp)
            .assertHeightIsEqualTo(22.dp)
        compose.onNodeWithText("격주").assertIsNotSelected().performClick()
        assertEquals("격주", picked)
    }

    @Test
    fun `아바타 원은 지름대로 그리고 보조 기술에 숨는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAvatarCircle(36.dp, modifier = Modifier.testTag("avatar"))
            }
        }
        compose.onNodeWithTag("avatar", useUnmergedTree = true)
            .assertWidthIsEqualTo(36.dp)
            .assertHeightIsEqualTo(36.dp)
    }

    @Test
    fun `사람 줄은 눌러 행동하고 점선 구분선을 그을 수 있다`() {
        var opens = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkPersonRow(
                    "러너",
                    isMe = true,
                    badge = { RunkkTagPill("크루장") },
                    trailing = { Text("12.3km") },
                    onClick = { opens++ },
                    onClickLabel = "크루원 정보 열기",
                    divider = RunkkRowDivider.Dashed,
                    modifier = Modifier.testTag("person")
                )
            }
        }
        compose.onNodeWithText("러너", substring = true).performClick()
        assertEquals(1, opens)
        compose.onAllNodesWithTag(DASHED_DIVIDER_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("person").assertHeightIsEqualTo(53.dp)
    }

    @Test
    fun `도는 표시는 설명을 주면 진행으로 읽고 없으면 숨는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkSpinner(contentDescription = "변환 중", modifier = Modifier.testTag("spoken"))
                    RunkkSpinner(size = 20.dp, color = RunkkColors.Lime)
                }
            }
        }
        compose.onNodeWithContentDescription("변환 중")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertWidthIsEqualTo(16.dp)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(1)
    }

    @Test
    fun `꺾쇠 링크는 48 누름 칸의 단추다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkChevronLink("러니멀 보기", onClick = { clicks++ }, modifier = Modifier.testTag("link"))
            }
        }
        compose.onNodeWithTag("link").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        val bounds = compose.onNodeWithTag("link").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 48.dp)
        compose.onNodeWithText("러니멀 보기").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `측정 거리와 지표는 읽기 문구 하나로 읽힌다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkRunDistance("2.14", unit = "km", spoken = "2.14킬로미터")
                    RunkkRunStats(
                        listOf(
                            RunkkRunStat("27:48", "시간", spoken = "27분 48초"),
                            RunkkRunStat("5'32\"", "평균 페이스", spoken = "1킬로미터당 5분 32초"),
                            RunkkRunStat("--", "심박")
                        ),
                        tone = RunkkHudTone.Crew
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("2.14킬로미터").assertExists()
        compose.onNodeWithContentDescription("시간 27분 48초").assertExists()
        compose.onNodeWithContentDescription("평균 페이스 1킬로미터당 5분 32초").assertExists()
        compose.onNodeWithContentDescription("심박 --").assertExists()
    }

    @Test
    fun `측정 둥근 단추는 지름대로 그리고 꺼지면 누를 수 없다`() {
        var taps = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                Column {
                    RunkkRoundControl(
                        RunkkIcons.Close,
                        contentDescription = "취소",
                        onClick = { taps++ },
                        size = 56.dp,
                        caption = "취소"
                    )
                    RunkkRoundControl(
                        RunkkIcons.Pause,
                        contentDescription = "일시정지",
                        onClick = { taps++ },
                        isEnabled = false
                    )
                    RunkkStopSquare(Modifier.testTag("stop"))
                }
            }
        }
        compose.onNodeWithContentDescription("취소").assertWidthIsEqualTo(56.dp).performClick()
        compose.onNodeWithContentDescription("일시정지").assertWidthIsEqualTo(88.dp).assertIsNotEnabled().performClick()
        assertEquals(1, taps)
        compose.onNodeWithTag("stop").assertWidthIsEqualTo(32.dp)
    }

    @Test
    fun `일시정지 덮개는 화면 이름을 알리고 안의 단추를 보인다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkPauseScrim("일시정지", tone = RunkkHudTone.Crew) {
                    RunkkRoundControl(RunkkIcons.Play, contentDescription = "재개", onClick = {})
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "일시정지")).assertExists()
        compose.onAllNodesWithContentDescription("재개").assertCountEquals(1)
    }
}
