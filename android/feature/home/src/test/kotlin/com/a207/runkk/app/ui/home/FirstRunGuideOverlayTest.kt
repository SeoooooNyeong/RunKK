package com.a207.runkk.app.ui.home

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.a207.runkk.app.core.designsystem.GuideStep
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.navigation.Routes
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 첫 사용 가이드 막(J-1~J-8, D-277). 시험 설계 10-06 TC-9711~TC-9713, TC-9716. */
@RunWith(RobolectricTestRunner::class)
class FirstRunGuideOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private var nexts = 0
    private var skips = 0

    private fun show(step: GuideStep, index: Int, count: Int) {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                FirstRunGuideOverlay(
                    step = step,
                    index = index,
                    stepCount = count,
                    anchor = Rect(40f, 400f, 500f, 600f),
                    onNext = { nexts++ },
                    onSkip = { skips++ }
                )
            }
        }
    }

    @Test
    fun `TC-9711 1차 배포는 크루·포인트를 뺀 6단계이고 열면 순서대로 늘어난다`() {
        assertEquals(
            listOf(
                GuideStep.FREE_RUN,
                GuideStep.BATTLE_RUN,
                GuideStep.WEEKLY_GOAL,
                GuideStep.RECORD_RUN,
                GuideStep.RANKING,
                GuideStep.ME
            ),
            GuideStep.visibleIn(ReleaseScope.FIRST_RELEASE)
        )
        assertEquals(7, GuideStep.visibleIn(ReleaseScope.opened(setOf(UnreleasedFeature.CREW))).size)
        assertEquals(7, GuideStep.visibleIn(ReleaseScope.opened(setOf(UnreleasedFeature.AVATAR_CUSTOMIZE))).size)
        assertEquals(GuideStep.entries, GuideStep.visibleIn(ReleaseScope.ALL_OPEN))
    }

    @Test
    fun `TC-9712 단계 문구는 디자인 그대로다`() {
        val res = RuntimeEnvironment.getApplication().resources
        assertEquals(
            listOf(
                "혼자 달리고 싶을 때,\n자유런을 시작해 보세요",
                "다른 러너와 같은 거리를 달리며\n대결해 보세요",
                "이번 주 목표를 얼마나 달성했는지\n확인해 보세요",
                "내 이전 기록이나 셀럽의 기록에\n도전해 보세요",
                "다른 러너와 비교해\n내 순위를 확인해 보세요",
                "내 러닝 기록과 아바타를\n한곳에서 확인해 보세요"
            ),
            GuideStep.visibleIn(ReleaseScope.FIRST_RELEASE).map { res.getString(it.textRes) }
        )
    }

    @Test
    fun `TC-9713 중간 단계는 쪽수와 건너뛰기·다음을 보인다`() {
        show(GuideStep.FREE_RUN, index = 0, count = 6)
        compose.onNodeWithText("1/6").assertExists()
        compose.onNodeWithText("혼자 달리고 싶을 때,\n자유런을 시작해 보세요").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("건너뛰기").performClick()
        compose.onNodeWithText("시작하기").assertDoesNotExist()
        assertEquals(1, nexts)
        assertEquals(1, skips)
    }

    @Test
    fun `TC-9713 마지막 단계는 시작하기만 있고 건너뛰기가 없다(TF-V9-03 디자인 j8 기준)`() {
        show(GuideStep.ME, index = 5, count = 6)
        compose.onNodeWithText("6/6").assertExists()
        compose.onNodeWithText("건너뛰기").assertDoesNotExist()
        compose.onNodeWithText("시작하기").performClick()
        assertEquals(1, nexts)
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
    }
}
