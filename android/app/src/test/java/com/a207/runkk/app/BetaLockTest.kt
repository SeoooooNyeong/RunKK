package com.a207.runkk.app

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 경로마다 이름만 보이는 가짜 화면들(크루·셀럽·셀프·홈). */
private object LockEntries : FeatureEntry {
    override val route: String = Routes.HOME

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.HOME) { Text("홈 화면") }
        builder.route(Routes.CREW) { Text("크루 화면") }
        builder.route(Routes.CELEB) { Text("셀럽 화면") }
        builder.route(Routes.SELF) { Text("셀프 화면") }
    }
}

/** 베타(prod)에서 열지 않는 기능: 크루·아바타·셀럽런만 막고 나머지는 연다(S15P31A207-218). */
@RunWith(RobolectricTestRunner::class)
class BetaLockTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `크루·아바타·셀럽런 경로만 막는다`() {
        listOf<NavKey>(
            Routes.Key(Routes.CREW),
            Routes.Key(Routes.CREW_CREATE),
            Routes.Key(Routes.CREW_CHAT),
            Routes.Key(Routes.AVATAR),
            Routes.Key(Routes.SHOP),
            Routes.Key(Routes.AVATAR_BACKGROUND),
            Routes.Key(Routes.CELEB),
            Routes.CrewHome(1),
            Routes.CrewChat(1),
            Routes.CrewRunLobby(1)
        ).forEach { assertTrue(BetaLock.isLocked(it), "$it") }
        listOf<NavKey>(
            Routes.Key(Routes.HOME),
            Routes.Key(Routes.SELF),
            Routes.Key(Routes.BATTLE_RANDOM),
            Routes.Key(Routes.RUNIMAL),
            Routes.Key(Routes.RECORDS),
            Routes.Key(Routes.ME),
            Routes.RunResult("r1"),
            Routes.BattleResult(1)
        ).forEach { assertFalse(BetaLock.isLocked(it), "$it") }
    }

    @Test
    fun `베타에서는 크루·셀럽 탭이 추후 추가 예정으로 바뀌고 셀프는 그대로다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkNavHost(
                    featureEntries = setOf(LockEntries),
                    startRoute = Routes.HOME,
                    onExit = {},
                    loggedIn = true,
                    lockUnreleased = true
                )
            }
        }
        compose.onNodeWithText("크루").performClick()
        compose.onNodeWithText("추후 추가 예정입니다").assertExists()
        compose.onNodeWithText("크루 화면").assertDoesNotExist()
        compose.onNodeWithText("셀럽").performClick()
        compose.onNodeWithText("추후 추가 예정입니다").assertExists()
        compose.onNodeWithText("셀프").performClick()
        compose.onNodeWithText("셀프 화면").assertExists()
    }

    @Test
    fun `베타가 아니면 크루가 열린다`() {
        compose.setContent {
            RunkkTheme(reduceMotion = true) {
                RunkkNavHost(
                    featureEntries = setOf(LockEntries),
                    startRoute = Routes.HOME,
                    onExit = {},
                    loggedIn = true,
                    lockUnreleased = false
                )
            }
        }
        compose.onNodeWithText("크루").performClick()
        compose.onNodeWithText("크루 화면").assertExists()
    }
}
