package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 디자인 시스템 구성 요소의 동작과 접근성 라벨(Robolectric Compose 시험). */
@RunWith(RobolectricTestRunner::class)
class RunkkComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `기본 버튼은 누르면 행동하고 꺼지면 누를 수 없다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    RunkkPrimaryButton("다음", onClick = { clicks++ })
                    RunkkPrimaryButton("가입", onClick = { clicks++ }, isEnabled = false)
                }
            }
        }
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("가입").assertIsNotEnabled()
        assertEquals(1, clicks)
    }

    @Test
    fun `읽기 문구가 있는 지표 칸도 누를 수 있으면 버튼 동작을 남긴다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkStatTile(
                    "이번 주 목표",
                    StatTileValue("60", unit = "%", spoken = "거리 60% 달성"),
                    onClick = { clicks++ }
                )
            }
        }
        compose.onNodeWithContentDescription("이번 주 목표 거리 60% 달성").assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `균등 탭 줄은 탭 역할로 고른 탭을 알리고 누르면 바꾼다`() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTabRow(listOf("홈", "응원보드", "크루런", "랭킹"), selectedIndex = 0, onSelect = { picked += it })
            }
        }
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("응원보드").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        compose.onNodeWithText("응원보드").performClick()
        assertEquals(listOf(1), picked)
    }

    @Test
    fun `라임 알약 버튼은 누르면 행동하고 꺼지면 누를 수 없다`() {
        var clicks = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Row {
                    RunkkLimePillButton("만들기", onClick = { clicks++ }, leadingIcon = RunkkIcons.Plus)
                    RunkkLimePillButton("가입", onClick = { clicks++ }, isEnabled = false)
                }
            }
        }
        compose.onNodeWithText("만들기").assertHasClickAction().performClick()
        compose.onNodeWithText("가입").assertIsNotEnabled()
        assertEquals(1, clicks)
    }

    @Test
    fun `종료 카운트다운 배너는 바뀌는 초 대신 고정 문구를 즉시 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkCountdownBanner(text = "상대 도착! 27초 안에 도착하지 못하면 대결이 끝나요", spoken = "상대 도착. 곧 대결이 끝나요")
            }
        }
        compose.onNodeWithContentDescription("상대 도착. 곧 대결이 끝나요")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    @Test
    fun `라디오 묶음은 묶음 이름을 보이고 선택지마다 읽기 문구를 쓸 수 있다`() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkSegmentRow(
                    options = listOf("3회", "4회"),
                    selectedIndex = 1,
                    onSelect = { picked += it },
                    groupLabel = "주간 목표 횟수",
                    spokenLabels = listOf("주 3회", "주 4회")
                )
            }
        }
        // 묶음 이름은 선택 묶음 노드가 한 번 읽는다(보이는 글자는 따로 읽지 않는다).
        compose.onNode(
            androidx.compose.ui.test.hasContentDescription("주간 목표 횟수") and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(
                    androidx.compose.ui.semantics.SemanticsProperties.SelectableGroup
                )
        ).assertExists()
        compose.onNodeWithText("주간 목표 횟수").assertDoesNotExist()
        compose.onNodeWithContentDescription("주 4회").assertIsSelected()
        compose.onNodeWithContentDescription("주 3회").performClick()
        assertEquals(listOf(0), picked)
    }

    @Test
    fun `입력칸은 키보드 완료 동작을 넘긴다`() {
        var searched = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTextField(
                    value = "새벽",
                    onValueChange = {},
                    label = "크루 검색",
                    imeAction = ImeAction.Search,
                    onImeAction = { searched++ }
                )
            }
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, searched)
        // 라벨이 입력칸 노드의 이름이 된다(값이 있어도 무엇을 넣는 칸인지 읽힌다).
        compose.onNode(hasSetTextAction() and hasText("크루 검색")).assertExists()
    }

    @Test
    fun `상태 알약의 알림 영역은 문구 노드에 걸린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkStatusPill("상대 연결 확인 중", live = LiveRegionMode.Polite)
            }
        }
        compose.onNodeWithText("상대 연결 확인 중")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `오버라인과 거리 칩은 한글 읽기 문구를 쓴다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    RunkkOverline("LIVE BATTLE · 5K")
                    RunkkInfoChip("10K", isDisplay = true)
                    RunkkStatusPill("3K")
                }
            }
        }
        compose.onNodeWithContentDescription("실시간 대결, 5킬로미터").assertExists()
        compose.onNodeWithContentDescription("10킬로미터").assertExists()
        compose.onNodeWithContentDescription("3킬로미터").assertExists()
    }

    @Test
    fun `아이콘 버튼은 설명 라벨로 찾을 수 있다`() {
        var opened = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = {
                    opened =
                        true
                })
            }
        }
        compose.onNodeWithContentDescription("알림함").assertHasClickAction().performClick()
        assertTrue(opened)
    }

    @Test
    fun `길게 누름 버튼은 짧게·길게를 다른 행동으로 받는다`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkLongPressButton(onTap = {
                    calls += "tap"
                }, onLongPress = { calls += "long" }, tapLabel = "종료 안내", longPressLabel = "길게 눌러 종료") {}
            }
        }
        val node = compose.onNodeWithContentDescription("길게 눌러 종료")
        node.performClick()
        node.performTouchInput { longClick() }
        node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        assertEquals(listOf("tap", "long"), calls)
    }

    @Test
    fun `고르기 칩은 켜짐·꺼짐 상태를 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Row {
                    RunkkChoiceChip("함께가 좋아요", isSelected = true, onSelectedChange = {})
                    RunkkChoiceChip("혼자가 편해요", isSelected = false, onSelectedChange = {})
                }
            }
        }
        compose.onNodeWithText("함께가 좋아요").assertIsOn()
        compose.onNodeWithText("혼자가 편해요").assertIsOff()
    }

    @Test
    fun `하단 탭은 다섯 개이고 고른 탭이 선택으로 보인다`() {
        var picked: RunkkTab? = null
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {
                    picked =
                        it
                })
            }
        }
        RunkkTab.entries.forEach {
            compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(it.labelRes)).assertExists()
        }
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("크루").performClick()
        assertEquals(RunkkTab.CREW, picked)
    }

    @Test
    fun `진행 막대는 진행 비율을, 단계 바는 "n단계 중 m단계"를 알린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    RunkkProgressBar(0.4f)
                    RunkkStepTopBar(title = "회원 가입", step = 2, total = 5)
                }
            }
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(0.4f, 0f..1f)
                )
            )
        compose.onNodeWithContentDescription("5단계 중 2단계").assertExists()
    }

    @Test
    fun `길게 누름 동작은 보조 기술 동작으로도 실행된다`() {
        var long = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkLongPressButton(onTap = {
                }, onLongPress = { long++ }, tapLabel = "해제 안내", longPressLabel = "길게 눌러 잠금 해제") {}
            }
        }
        compose.onNodeWithContentDescription("길게 눌러 잠금 해제").performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, long)
    }
}
