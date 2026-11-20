package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
                    RunkkOverline("LIVE BATTLE · 5km")
                    RunkkInfoChip("10km", isDisplay = true)
                    RunkkStatusPill("3km")
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
    fun `R2-06 하단 탭 바는 정해진 높이 안에서 양옆과 아래를 띄운 떠 있는 줄이다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}, modifier = Modifier.testTag("bar"))
            }
        }
        compose.onNodeWithTag("bar").assertHeightIsEqualTo(RUNKK_BOTTOM_BAR_HEIGHT)
        val bar = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val crew = compose.onNodeWithText("크루").getUnclippedBoundsInRoot()
        assertTrue(crew.left - bar.left >= 18.dp, "탭 줄이 양옆을 띄운다")
    }

    @Test
    fun `D96 D100 고른 탭은 탭 바 위로 넘쳐 솟은 원에 들어가고 라벨은 표면 안에 있다`() {
        val anchors = GuideAnchors()
        compose.setContent {
            CompositionLocalProvider(LocalGuideAnchors provides anchors) {
                RunkkTheme(reduceMotion = true) {
                    RunkkBottomBar(selected = RunkkTab.HOME, onSelect = {}, modifier = Modifier.testTag("bar"))
                }
            }
        }
        // D100: 탭 화면이 비켜 주는 높이는 표면 70 + 아래 틈 12다. 솟은 원 20은 내용 위에 겹쳐 그린다.
        assertEquals(82.dp, RUNKK_BOTTOM_BAR_HEIGHT, "표면 70 + 아래 틈 12")
        assertEquals(20.dp, RUNKK_BOTTOM_BAR_RISE, "원이 표면 위로 솟는 높이")
        val bar = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val surface = compose.onNodeWithTag(BOTTOM_BAR_SURFACE_TAG).getUnclippedBoundsInRoot()
        val circle = compose.onNodeWithTag(BOTTOM_BAR_INDICATOR_TAG).getUnclippedBoundsInRoot()
        val home = compose.onNodeWithText("홈", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(70f, (surface.bottom - surface.top).value, 0.5f, "표면 높이")
        assertEquals(12f, (bar.bottom - surface.bottom).value, 0.5f, "아래 틈")
        assertEquals(46f, (circle.right - circle.left).value, 0.5f, "원 지름")
        assertEquals(20f, (surface.top - circle.top).value, 0.5f, "원이 표면 위로 20 솟는다")
        assertEquals(bar.top.value, surface.top.value, 0.5f, "표면이 탭 바 맨 위다")
        assertEquals(20f, (bar.top - circle.top).value, 0.5f, "원은 탭 바 위로 넘쳐 그린다")
        assertEquals(
            ((home.left + home.right) / 2).value,
            ((circle.left + circle.right) / 2).value,
            1f,
            "원은 고른 탭 가운데"
        )
        assertTrue(home.top >= circle.bottom, "라벨은 원 아래")
        assertTrue(home.bottom <= surface.bottom, "라벨은 표면 안")
        // 가이드 강조 구멍(기록런 단계)은 솟은 원 자리가 아니라 표면 안의 탭 칸이다.
        compose.waitForIdle()
        val recordRun = anchors[GuideStep.RECORD_RUN]!!
        val density = compose.density.density
        assertEquals(surface.top.value, recordRun.top / density, 1f, "기록런 앵커 위")
        assertEquals(surface.bottom.value, recordRun.bottom / density, 1f, "기록런 앵커 아래")
    }

    @Test
    fun `D96 고른 탭이 바뀌면 원이 그 탭으로 옮긴다`() {
        var selected by mutableStateOf(RunkkTab.HOME)
        compose.setContent {
            RunkkTheme(reduceMotion = true) { RunkkBottomBar(selected = selected, onSelect = { selected = it }) }
        }
        compose.onNodeWithText("마이").performClick()
        compose.waitForIdle()
        val circle = compose.onNodeWithTag(BOTTOM_BAR_INDICATOR_TAG).getUnclippedBoundsInRoot()
        val me = compose.onNodeWithText("마이", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(((me.left + me.right) / 2).value, ((circle.left + circle.right) / 2).value, 1f)
        compose.onNodeWithText("마이").assertIsSelected()
    }

    @Test
    fun `고른 선택지에는 체크 아이콘이 붙고 고르기 칩에는 붙지 않는다(피그마 세그먼트·시간대 칩)`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    RunkkSegmentRow(listOf("자유", "3km", "5km"), selectedIndex = 2, onSelect = {})
                    RunkkChoiceChip("함께가 좋아요", isSelected = true, onSelectedChange = {})
                    RunkkChoiceChip("혼자가 편해요", isSelected = false, onSelectedChange = {})
                }
            }
        }
        compose.onAllNodesWithTag(CHIP_CHECK_TAG, useUnmergedTree = true).assertCountEquals(1)
        val check = compose.onAllNodesWithTag(CHIP_CHECK_TAG, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("5km", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(check.right <= label.left, "체크는 글자 앞")
        assertEquals(18f, (check.right - check.left).value, 0.5f)
    }

    @Test
    fun `R2-07 가운데 제목 상단 바는 뒤로 버튼이 있어도 제목을 바 가운데에 둔다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkTopBar(title = "시작하기", onBack = {}, isTitleCentered = true, modifier = Modifier.testTag("top"))
            }
        }
        val bar = compose.onNodeWithTag("top").getUnclippedBoundsInRoot()
        val title = compose.onNodeWithText("시작하기").getUnclippedBoundsInRoot()
        assertEquals(((bar.left + bar.right) / 2).value, ((title.left + title.right) / 2).value, 1f)
        compose.onNodeWithContentDescription("뒤로").assertExists()
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
    fun `TC-5831 언랭은 배치 중으로 읽는다`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals("배치 중", res.getString(R.string.tier_unranked))
        assertEquals("임시 골드", res.getString(R.string.tier_provisional_format, "골드"))
        var name: String? = null
        compose.setContent { name = tierName("UNRANKED") }
        compose.waitForIdle()
        assertEquals("배치 중", name)
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
