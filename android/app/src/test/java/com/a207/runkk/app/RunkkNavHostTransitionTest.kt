package com.a207.runkk.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_HEIGHT
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val HOME_TAG = "home-room"
private const val AVATAR_TAG = "avatar-screen"

/** 홈(탭)은 화면 높이를 채우는 우리 집과 아바타 누름, 아바타 꾸미기(탭 아님)는 화면 높이를 채우는 칸. */
private object TransitionEntries : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) {
            val navigator = LocalRunkkNavigator.current
            Box(Modifier.fillMaxSize().testTag(HOME_TAG)) {
                Text("아바타", modifier = Modifier.clickable { navigator.navigate(Routes.AVATAR) })
            }
        }
        builder.route(Routes.AVATAR) {
            val navigator = LocalRunkkNavigator.current
            Box(Modifier.fillMaxSize().testTag(AVATAR_TAG)) {
                Text("뒤로", modifier = Modifier.clickable { navigator.back() })
            }
        }
    }
}

/**
 * 탭 화면에서 탭이 아닌 화면으로 갈 때(홈 우리 집 아바타 → 아바타 꾸미기) 하단 탭은 바로 사라지지만, 사라지는 화면은 전환(페이드)이
 * 끝날 때까지 자기 크기를 지킨다. 하단 탭 자리를 화면마다 자기 경로로 정하므로 나가는 홈이 하단 탭 높이만큼 길어지지 않는다.
 */
@RunWith(RobolectricTestRunner::class)
class RunkkNavHostTransitionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show() = compose.setContent {
        RunkkTheme(reduceMotion = true) {
            RunkkNavHost(setOf(TransitionEntries), startRoute = Routes.HOME, onExit = {}, loggedIn = true)
        }
    }

    private fun heightOf(tag: String): Dp = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().height

    @Test
    fun `아바타를 눌러 화면이 바뀌는 동안 나가는 홈의 높이가 그대로다`() {
        show()
        val before = heightOf(HOME_TAG)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("아바타").performClick()
        advanceToTransitionMiddle()
        compose.onNodeWithTag(AVATAR_TAG).assertExists()
        assertEquals(before, heightOf(HOME_TAG), "전환 중 홈이 하단 탭 높이만큼 늘어났다")

        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText("홈").assertDoesNotExist()
        assertEquals(before + RUNKK_BOTTOM_BAR_HEIGHT, heightOf(AVATAR_TAG), "탭이 아닌 화면은 하단 탭 자리까지 쓴다")
    }

    @Test
    fun `아바타 꾸미기에서 홈으로 돌아오는 동안 나가는 화면의 높이가 그대로다`() {
        show()
        compose.onNodeWithText("아바타").performClick()
        compose.waitForIdle()
        val before = heightOf(AVATAR_TAG)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("뒤로").performClick()
        advanceToTransitionMiddle()
        compose.onNodeWithTag(HOME_TAG).assertExists()
        assertEquals(before, heightOf(AVATAR_TAG), "전환 중 나가는 화면이 하단 탭 높이만큼 줄었다")
    }

    /** NavDisplay 기본 전환(페이드 700ms)의 가운데쯤까지 시계를 나눠 진행한다. 걸음마다 재구성·배치를 마저 돌린다. */
    private fun advanceToTransitionMiddle() {
        repeat(TRANSITION_MIDDLE_STEPS) {
            compose.mainClock.advanceTimeBy(STEP_MILLIS)
            compose.waitForIdle()
        }
    }

    private companion object {
        const val TRANSITION_MIDDLE_STEPS = 6
        const val STEP_MILLIS = 50L
    }
}
