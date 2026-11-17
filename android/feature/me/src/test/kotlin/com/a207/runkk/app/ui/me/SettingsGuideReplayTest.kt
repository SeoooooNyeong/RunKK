package com.a207.runkk.app.ui.me

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.testing.RUNKK_PHONE
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.MemberSettings
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 설정의 「가이드 다시 보기」([임시:FR-성-36], [임시:D-성-299], [임시:D-성-312]): 줄과 설명(단계 수는 배포 범위를 따른다)을 보이고,
 * 누르면 홈만 남긴 뒤 다시 보기 신호 경로(Routes.GuideReplay)를 올린다. 신호는 경로 값이라 공유 상태가 없다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = RUNKK_PHONE)
class SettingsGuideReplayTest {
    @get:Rule
    val compose = createComposeRule()

    private var replays = 0

    private fun show(scope: ReleaseScope) = compose.setContent {
        CompositionLocalProvider(LocalReleaseScope provides scope) {
            RunkkTheme(reduceMotion = true) {
                SettingsScreen(
                    page = SettingsPage.APP,
                    state = SettingsUiState(isLoading = false, settings = MemberSettings(emptyMap(), emptyMap())),
                    actions = SettingsActions(onReplayGuide = { replays++ })
                )
            }
        }
    }

    @Test
    fun `가이드 다시 보기 줄이 보이고 누르면 다시 보기 행동이 불린다`() {
        show(ReleaseScope.ALL_OPEN)
        compose.onNodeWithText("가이드 다시 보기").performScrollTo().assertExists()
        compose.onNodeWithText("홈 사용법 4단계를 처음부터 다시 봐요").assertExists()
        compose.onNodeWithText("가이드 다시 보기").performClick()
        assertEquals(1, replays)
    }

    @Test
    fun `설명 문구의 단계 수가 배포 범위를 따른다(1차 배포는 3단계)`() {
        show(ReleaseScope.FIRST_RELEASE)
        compose.onNodeWithText("홈 사용법 3단계를 처음부터 다시 봐요").performScrollTo().assertExists()
    }

    @Test
    fun `다시 보기는 홈 탭을 고른 뒤 신호 경로를 올린다`() {
        val calls = mutableListOf<String>()
        replayGuide(
            selectTab = { calls += "tab:$it" },
            navigate = { calls += "navigate:$it" }
        )
        assertEquals(listOf("tab:${Routes.HOME}", "navigate:${Routes.GuideReplay}"), calls)
    }
}
