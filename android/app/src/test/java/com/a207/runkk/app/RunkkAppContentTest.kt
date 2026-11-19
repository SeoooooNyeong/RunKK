package com.a207.runkk.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.HoldSplashUntil
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.domain.sync.PreloadProgress
import com.a207.runkk.app.domain.sync.PreloadState
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

/** 홈이 [isReady]가 참이 될 때까지 스플래시를 붙잡는 가짜 화면(FR-239, D-352). */
private class HoldingEntries(private val isReady: () -> Boolean) : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) {
            HoldSplashUntil(isReady = isReady())
            Text("홈 화면")
        }
    }
}

/** 스플래시를 계속 붙잡고 화면 전체가 눌리는 가짜 홈(덮인 동안 손가락이 새지 않는지). */
private class TappableHoldingEntries(private val onTap: () -> Unit) : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) {
            HoldSplashUntil(isReady = false)
            Box(Modifier.fillMaxSize().clickable(onClick = onTap))
        }
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
    fun `첫 화면이 다 읽을 때까지 스플래시를 두고 다 읽으면 걷는다(FR-239)`() {
        var isReady by mutableStateOf(false)
        loggedIn = true
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(setOf(HoldingEntries { isReady }), loggedIn = loggedIn, onExit = {})
            }
        }
        compose.onNodeWithContentDescription("런크크 시작 중").assertExists()
        compose.onNodeWithText("홈 화면").assertDoesNotExist() // 덮인 동안은 보조 기술에서 숨긴다(AN11-4)
        isReady = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("런크크 시작 중").assertDoesNotExist()
        compose.onNodeWithText("홈 화면").assertExists()
    }

    @Test
    fun `첫 화면이 계속 붙잡아도 4초 뒤에는 스플래시를 걷는다(FR-239)`() {
        loggedIn = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(setOf(HoldingEntries { false }), loggedIn = loggedIn, onExit = {})
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithContentDescription("런크크 시작 중").assertExists()
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithContentDescription("런크크 시작 중").assertDoesNotExist()
    }

    @Test
    fun `에셋을 다 받기 전에는 시간이 지나도 다음 화면으로 가지 않고 막대와 퍼센트를 보인다(S15P31A207-345)`() {
        var preload by mutableStateOf<PreloadState>(PreloadState.Loading(PreloadProgress(3, 7)))
        loggedIn = true
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(setOf(FakeEntries), loggedIn = loggedIn, onExit = {}, preload = preload)
            }
        }
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithContentDescription("런크크 시작 중").assertExists()
        compose.onNodeWithText("42%").assertExists()
        compose.onNodeWithText("홈 화면").assertDoesNotExist()

        // 다 받으면 넘어간다(시계를 다시 흐르게 해 바뀐 상태를 그리게 한다).
        preload = PreloadState.Ready(PreloadProgress(7, 7))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("런크크 시작 중").assertDoesNotExist()
        compose.onNodeWithText("홈 화면").assertExists()

        // 다 받은 뒤 (로그인 전환 등으로) 다시 받기 시작해도 첫 화면을 다시 덮지 않는다.
        preload = PreloadState.Loading(PreloadProgress(0, 7))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("런크크 시작 중").assertDoesNotExist()
        compose.onNodeWithText("홈 화면").assertExists()
    }

    @Test
    fun `캐시에 다 있으면 막대 없이 바로 넘어간다`() {
        loggedIn = false
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(
                    setOf(FakeEntries),
                    loggedIn = loggedIn,
                    onExit = {},
                    preload = PreloadState.Ready(PreloadProgress.UNKNOWN)
                )
            }
        }
        compose.onNodeWithText("로그인 화면").assertExists()
        compose.onNodeWithText("100%").assertDoesNotExist()
    }

    @Test
    fun `받지 못하면 홈으로 넘어가지 않고 다시 시도를 보이며 누르면 이어 받기를 부른다`() {
        var retries = 0
        loggedIn = true
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(
                    setOf(FakeEntries),
                    loggedIn = loggedIn,
                    onExit = {},
                    preload = PreloadState.Failed(PreloadProgress(3, 7), AppError.Network),
                    onRetryPreload = { retries++ }
                )
            }
        }
        compose.onNodeWithText("에셋을 받지 못했어요").assertExists()
        compose.onNodeWithText("홈 화면").assertDoesNotExist()
        compose.onNodeWithText("다시 시도").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `실패 화면의 다시 시도는 누르는 동안 손가락이 조금 움직여도 첫 누름에 이어 받기를 부른다`() {
        var retries = 0
        loggedIn = true
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(
                    setOf(FakeEntries),
                    loggedIn = loggedIn,
                    onExit = {},
                    preload = PreloadState.Failed(PreloadProgress(3, 7), AppError.Network),
                    onRetryPreload = { retries++ }
                )
            }
        }
        // 실기기 손가락은 누르는 사이 몇 픽셀 움직인다(움직임 이벤트가 끼어든다).
        compose.onNodeWithText("다시 시도").performTouchInput {
            down(center)
            moveBy(Offset(0f, 2f))
            up()
        }
        assertEquals(1, retries, "첫 누름에 다시 시도한다")
    }

    @Test
    fun `스플래시가 덮은 동안 아래 첫 화면은 눌리지 않는다`() {
        var taps = 0
        loggedIn = true
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkAppContent(setOf(TappableHoldingEntries { taps++ }), loggedIn = loggedIn, onExit = {})
            }
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.onNodeWithContentDescription("런크크 시작 중").assertExists()
        assertEquals(0, taps, "덮인 첫 화면은 손가락을 받지 않는다")
    }

    @Test
    fun `시작 경로 규칙`() {
        assertEquals(Routes.HOME, startRouteFor(true))
        assertEquals(Routes.LOGIN, startRouteFor(false))
    }
}
