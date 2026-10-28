package com.a207.runkk.app

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
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

/** 경로마다 이름만 보이는 가짜 화면들. */
private object FakeEntries : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.LOGIN) { Text("로그인 화면") }
        builder.route(Routes.SIGN_UP) { Text("가입 화면") }
        builder.route(Routes.HOME) {
            val navigator = LocalRunkkNavigator.current
            Column {
                Text("홈 화면")
                TextButton(onClick = { navigator.navigate(Routes.RECORDS) }) { Text("기록으로") }
            }
        }
        builder.route(Routes.ME) { Text("마이 화면") }
    }
}

/** 시작 경로(로그인 상태)·로그아웃 처리·하단 탭(SRS 5.1). */
@RunWith(RobolectricTestRunner::class)
class RunkkAppContentTest {
    @get:Rule
    val compose = createComposeRule()

    private var loggedIn by mutableStateOf<Boolean?>(null)

    private fun show() = compose.setContent {
        RunkkTheme(reduceMotion = true) { RunkkAppContent(setOf(FakeEntries), loggedIn = loggedIn, onExit = {}) }
    }

    @Test
    fun `로그인 상태를 모르면 스플래시, 알면 시작 경로를 고른다`() {
        show()
        compose.onNodeWithContentDescription("런크크 시작 중").assertExists()
        loggedIn = false
        compose.onNodeWithText("로그인 화면").assertExists()
    }

    @Test
    fun `로그인한 상태로 시작하면 홈과 하단 탭이 보인다`() {
        loggedIn = true
        show()
        compose.onNodeWithText("홈 화면").assertExists()
        compose.onNodeWithText("홈").assertIsSelected()
        compose.onNodeWithText("마이").performClick()
        compose.onNodeWithText("마이 화면").assertExists()
    }

    @Test
    fun `로그인하지 않은 상태로 시작하면 하단 탭 없이 로그인이 보인다`() {
        loggedIn = false
        show()
        compose.onNodeWithText("로그인 화면").assertExists()
        compose.onNodeWithText("셀프").assertDoesNotExist()
    }

    @Test
    fun `도중에 로그인 상태가 풀리면 로그인으로 돌아간다`() {
        loggedIn = true
        show()
        compose.onNodeWithText("기록으로").performClick()
        compose.onNodeWithText("준비 중인 화면이에요").assertExists()
        loggedIn = false
        compose.onNodeWithText("로그인 화면").assertExists()
    }

    @Test
    fun `시작 경로 규칙`() {
        assertEquals(Routes.HOME, startRouteFor(true))
        assertEquals(Routes.LOGIN, startRouteFor(false))
    }
}
