package com.a207.runkk.app.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 첫 사용 가이드 단계 막([임시:FR-성-35], [임시:D-성-310], [임시:D-성-311], D-277): 4단계(자유런, 배틀런, 기록런, 내 러너) 말풍선의
 * 제목·설명·쪽수와 「건너뛰기」·「이전」·「다음」(마지막은 「시작하기」). 시험 설계 10-06 TC-9711~TC-9713, TC-9716.
 */
@RunWith(RobolectricTestRunner::class)
class FirstRunGuideOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private var nexts = 0
    private var previouses = 0
    private var backs = 0

    private fun show(step: GuideStep, index: Int, count: Int, anchor: Rect = Rect(40f, 400f, 500f, 600f)) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                FirstRunGuideOverlay(
                    step = step,
                    index = index,
                    stepCount = count,
                    anchor = anchor,
                    onNext = { nexts++ },
                    onPrevious = { previouses++ },
                    onBack = { backs++ }
                )
            }
        }
    }

    @Test
    fun `TC-9711 열린 배포는 4단계이고 내 러너가 닫힌 1차 배포는 3단계다`() {
        assertEquals(
            listOf(GuideStep.FREE_RUN, GuideStep.BATTLE_RUN, GuideStep.RECORD_RUN, GuideStep.MY_RUNNER),
            GuideStep.visibleIn(ReleaseScope.ALL_OPEN)
        )
        assertEquals(
            listOf(GuideStep.FREE_RUN, GuideStep.BATTLE_RUN, GuideStep.RECORD_RUN),
            GuideStep.visibleIn(ReleaseScope.FIRST_RELEASE)
        )
    }

    @Test
    fun `TC-9712 단계 제목과 설명은 시안 그대로다(D-성-311)`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals(
            listOf("자유런", "배틀런", "기록런", "내 러너"),
            GuideStep.entries.map { res.getString(it.titleRes) }
        )
        assertEquals(
            listOf(
                "혼자 달리고 싶을 때 눌러 주세요. 거리와 페이스가 자동으로 기록돼요.",
                "다른 러너와 같은 거리를 달리며 대결해요.",
                "어제의 내 기록이나 셀럽의 기록과 대결해요. 거리를 고르면 그 기록을 세운 날의 내 모습이 보여요.",
                "달릴수록 바뀌는 내 러너를 확인하고 꾸며 보세요."
            ),
            GuideStep.entries.map { res.getString(it.textRes) }
        )
    }

    @Test
    fun `TC-9713 1단계는 쪽수 1 ÷ 4와 건너뛰기·다음이 있고 이전이 없다`() {
        show(GuideStep.FREE_RUN, index = 0, count = 4)
        compose.onNodeWithText("1 / 4").assertExists()
        compose.onNodeWithText("자유런").assertExists()
        compose.onNodeWithText("혼자 달리고 싶을 때 눌러 주세요. 거리와 페이스가 자동으로 기록돼요.").assertExists()
        compose.onNodeWithText("이전").assertDoesNotExist()
        compose.onNodeWithText("시작하기").assertDoesNotExist()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("건너뛰기").performClick()
        assertEquals(1, nexts)
        assertEquals(1, backs, "단계의 건너뛰기는 뒤로 가기와 같은 종료다(D-성-310)")
        assertEquals(0, previouses)
    }

    @Test
    fun `2단계부터 이전이 있다`() {
        show(GuideStep.BATTLE_RUN, index = 1, count = 4)
        compose.onNodeWithText("2 / 4").assertExists()
        compose.onNodeWithText("배틀런").assertExists()
        compose.onNodeWithText("건너뛰기").assertExists()
        compose.onNodeWithText("다음").assertExists()
        compose.onNodeWithText("이전").performClick()
        assertEquals(1, previouses)
        assertEquals(0, nexts)
    }

    @Test
    fun `TC-9713 마지막 단추는 시작하기이고 건너뛰기가 없다`() {
        show(GuideStep.MY_RUNNER, index = 3, count = 4)
        compose.onNodeWithText("4 / 4").assertExists()
        compose.onNodeWithText("내 러너").assertExists()
        compose.onNodeWithText("다음").assertDoesNotExist()
        compose.onNodeWithText("건너뛰기").assertDoesNotExist()
        compose.onNodeWithText("이전").assertExists()
        compose.onNodeWithText("시작하기").performClick()
        assertEquals(1, nexts)
    }

    @Test
    fun `닫힌 기능이 있으면 쪽수는 n ÷ 3이고 기록런이 마지막이다`() {
        show(GuideStep.RECORD_RUN, index = 2, count = 3)
        compose.onNodeWithText("3 / 3").assertExists()
        compose.onNodeWithText("기록런").assertExists()
        compose.onNodeWithText("시작하기").assertExists()
    }

    @Test
    fun `R2-16 앵커가 위쪽이면 말풍선은 앵커 바로 아래에 꼬리를 위로 달고 꼬리는 앵커 가운데를 가리킨다`() {
        val hole = Rect(20f, 180f, 200f, 280f)
        val place = bubblePlacement(
            hole = hole,
            screen = Size(390f, 844f),
            bubble = Size(300f, 150f),
            margin = 16f,
            gap = 6f,
            tailInset = 28f
        )
        assertEquals(GuideTail.UP, place.tail)
        assertEquals(286f, place.top)
        assertEquals(16f, place.left)
        assertEquals(300f, place.width)
        assertEquals(hole.center.x, place.left + place.tailX)
    }

    @Test
    fun `R2-16 앵커가 아래쪽이면 말풍선은 앵커 바로 위에 꼬리를 아래로 달고 화면 오른쪽 여백 안에 든다`() {
        val hole = Rect(300f, 760f, 370f, 830f)
        val place = bubblePlacement(
            hole = hole,
            screen = Size(390f, 844f),
            bubble = Size(300f, 150f),
            margin = 16f,
            gap = 6f,
            tailInset = 28f
        )
        assertEquals(GuideTail.DOWN, place.tail)
        assertEquals(760f - 6f - 150f, place.top)
        assertEquals(390f - 16f - 300f, place.left)
        assertEquals(hole.center.x, place.left + place.tailX)
    }

    @Test
    fun `R2-16 앵커를 모르면 꼬리 없이 가운데에 띄운다`() {
        val place = bubblePlacement(null, Size(390f, 844f), Size(300f, 150f), 16f, 6f, 28f)
        assertEquals(GuideTail.NONE, place.tail)
        assertEquals(45f, place.left)
        assertEquals((844f - 150f) / 2f, place.top)
    }

    @Test
    @Config(qualifiers = RUNKK_PHONE)
    fun `R2-16 말풍선은 너비 300dp이고 꼬리가 앵커 쪽에 붙는다`() {
        show(GuideStep.FREE_RUN, index = 0, count = 4, anchor = Rect(20f, 40f, 160f, 120f))
        compose.onNodeWithTag(GUIDE_BUBBLE_TAG).assertWidthIsEqualTo(BUBBLE_WIDTH)
        compose.onNodeWithTag(GUIDE_TAIL_TAG).assertExists()
        val density = compose.density.density
        val bubbleTop = compose.onNodeWithTag(GUIDE_BUBBLE_TAG).getUnclippedBoundsInRoot().top.value * density
        // 강조 자리(앵커 + 6dp) 바로 아래 6dp에 붙는다.
        assertEquals(120f + 12f * density, bubbleTop, 1f)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun `좁은 화면(320)에서도 아래 줄 단추가 말풍선 안에 든다`() {
        show(GuideStep.RECORD_RUN, index = 2, count = 4)
        val bubble = compose.onNodeWithTag(GUIDE_BUBBLE_TAG).getUnclippedBoundsInRoot()
        listOf("건너뛰기", "이전", "다음").forEach {
            val button = compose.onNodeWithText(it).getUnclippedBoundsInRoot()
            assertEquals(true, button.left >= bubble.left && button.right <= bubble.right, "$it 단추가 말풍선 밖이다")
        }
    }

    @Test
    fun `TC-9716 맨 위가 홈이 아니면 가이드를 그리지 않는다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                HomeFeatureEntry().Overlay(Routes.Key(Routes.SETTINGS))
                HomeFeatureEntry().Overlay(Routes.Key(Routes.RUN))
                HomeFeatureEntry().Overlay(null)
            }
        }
        compose.onNodeWithTag(GUIDE_OVERLAY_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertDoesNotExist()
    }
}
