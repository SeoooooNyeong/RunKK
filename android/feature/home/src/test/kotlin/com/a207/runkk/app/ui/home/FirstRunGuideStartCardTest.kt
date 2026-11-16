package com.a207.runkk.app.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.settings.FirstRunGuide
import com.a207.runkk.app.domain.settings.GuideCopy
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 첫 사용 가이드 시작 카드와 화면 흐름([임시:FR-성-35], [임시:FR-성-36], [임시:D-성-312]): 처음 홈에 들어가면 시작 카드, 「둘러보기」로
 * 단계, 설정의 「가이드 다시 보기」는 경로 값 Routes.GuideReplay로 와서 1단계부터 보이고 그 경로를 뺀다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class FirstRunGuideStartCardTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val remote = FakeGuideRemote()
    private val session = FakeSessionRepository()
    private val viewModel by lazy { FirstRunGuideViewModel(FirstRunGuide(settings, remote), session) }

    private val calls = mutableListOf<String>()
    private val navigator = object : RunkkNavigator {
        override fun navigate(route: String) = Unit.also { calls += "navigate:$route" }

        override fun navigate(key: NavKey) = Unit.also { calls += "navigate:$key" }

        override fun back() = Unit.also { calls += "back" }

        override fun replace(key: NavKey) = Unit.also { calls += "replace:$key" }

        override fun selectTab(route: String) = Unit.also { calls += "tab:$route" }

        override fun restart(route: String) = Unit.also { calls += "restart:$route" }
    }

    private fun showRoute(top: NavKey?, scope: ReleaseScope = ReleaseScope.ALL_OPEN) = compose.setContent {
        CompositionLocalProvider(LocalReleaseScope provides scope) {
            RunkkTheme(reduceMotion = true) { FirstRunGuideRoute(top, viewModel, navigator) }
        }
    }

    @Test
    fun `시작 카드는 제목, 단계 수가 든 본문, 둘러보기, 건너뛰기, 다시 보기 안내를 보인다`() {
        var starts = 0
        var skips = 0
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                FirstRunGuideStartCard(stepCount = 4, onStart = { starts++ }, onSkip = { skips++ })
            }
        }
        compose.onNodeWithText("런크크가 처음이신가요?").assertExists()
        compose.onNodeWithText("달리기를 시작하는 법 4가지를 30초 안에 알려 드릴게요.").assertExists()
        compose.onNodeWithText("설정 > 가이드 다시 보기에서 언제든 다시 볼 수 있어요").assertExists()
        compose.onNodeWithText("둘러보기").performClick()
        compose.onNodeWithText("건너뛰기").performClick()
        assertEquals(1 to 1, starts to skips)
    }

    @Test
    fun `시작 카드 본문에 배포 범위의 단계 수가 들어간다(1차 배포는 3가지)`() {
        showRoute(HOME, ReleaseScope.FIRST_RELEASE)
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertExists()
        compose.onNodeWithText("달리기를 시작하는 법 3가지를 30초 안에 알려 드릴게요.").assertExists()
    }

    @Test
    fun `처음 홈에서는 시작 카드가 보이고 둘러보기를 누르면 1단계 말풍선이다`() {
        showRoute(HOME)
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertExists()
        compose.onNodeWithTag(GUIDE_OVERLAY_TAG).assertDoesNotExist()
        compose.onNodeWithText("둘러보기").performClick()
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GUIDE_OVERLAY_TAG).assertExists()
        compose.onNodeWithText("1 / 4").assertExists()
        compose.onNodeWithText("자유런").assertExists()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText("2 / 4").assertExists()
        compose.onNodeWithText("이전").performClick()
        compose.onNodeWithText("1 / 4").assertExists()
        assertEquals(0, remote.markCalls)
    }

    @Test
    fun `시작 카드의 건너뛰기는 본 것으로 저장하고 닫는다`() {
        showRoute(HOME)
        compose.onNodeWithText("건너뛰기").performClick()
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertDoesNotExist()
        assertEquals(1, remote.markCalls)
        assertEquals(GuideCopy.SEEN, settings.guide)
    }

    @Test
    fun `다시 보기 경로가 맨 위에 오면 가이드를 다시 열고 그 경로를 뺀다(D-성-312)`() {
        settings.guide = GuideCopy.SEEN
        showRoute(Routes.GuideReplay)
        compose.waitForIdle()
        assertEquals(listOf("back"), calls, "경로는 신호일 뿐이라 바로 뺀다")
        assertEquals(FirstRunGuideUiState(GuidePhase.STEPS, index = 0, isReplay = true), viewModel.state.value)
        // 신호 경로가 맨 위인 동안에는 그리지 않는다(홈이 맨 위가 되면 그린다).
        compose.onNodeWithTag(GUIDE_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `다시 보기는 시작 카드 없이 1단계부터 보이고 끝내도 본 표시를 바꾸지 않는다`() {
        settings.guide = GuideCopy.SEEN
        viewModel.onReplay()
        showRoute(HOME)
        compose.onNodeWithTag(GUIDE_START_CARD_TAG).assertDoesNotExist()
        compose.onNodeWithText("1 / 4").assertExists()
        compose.onNodeWithText("건너뛰기").performClick()
        compose.onNodeWithTag(GUIDE_OVERLAY_TAG).assertDoesNotExist()
        assertEquals(0, remote.markCalls)
        assertEquals(emptyList(), calls, "끝내면 홈에 그대로 남는다")
    }

    private companion object {
        val HOME = Routes.Key(Routes.HOME)
    }
}
