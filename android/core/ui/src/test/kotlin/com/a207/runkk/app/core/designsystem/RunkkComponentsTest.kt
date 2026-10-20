package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
                    RunkkPrimaryButton("가입", onClick = { clicks++ }, enabled = false)
                }
            }
        }
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("가입").assertIsNotEnabled()
        assertEquals(1, clicks)
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
                    RunkkChoiceChip("함께가 좋아요", selected = true, onSelectedChange = {})
                    RunkkChoiceChip("혼자가 편해요", selected = false, onSelectedChange = {})
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
        RunkkTab.entries.forEach { compose.onNodeWithText(it.label).assertExists() }
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("크루").performClick()
        assertEquals(RunkkTab.CREW, picked)
    }

    @Test
    fun `진행 막대는 진행 비율을 보조 기술에 알린다`() {
        compose.setContent { RunkkTheme(reduceMotion = true) { RunkkStepTopBar(title = "회원 가입", step = 2, total = 5) } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(
                        0.4f,
                        0f..1f
                    )
                )
            )
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
