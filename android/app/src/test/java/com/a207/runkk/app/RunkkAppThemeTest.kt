package com.a207.runkk.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.domain.settings.ScreenTheme
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** TC-6528: 화면 테마 설정 → RunkkTheme 색 벌(FR-203). 테마를 바꾸면 다시 시작하지 않고 그 자리에서 다시 그린다. */
@RunWith(RobolectricTestRunner::class)
class RunkkAppThemeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `설정 테마와 기기 설정으로 어두운 벌을 고른다`() {
        assertEquals(false, isDarkTheme(ScreenTheme.LIGHT, isSystemDark = true))
        assertEquals(true, isDarkTheme(ScreenTheme.DARK, isSystemDark = false))
        assertEquals(true, isDarkTheme(ScreenTheme.SYSTEM, isSystemDark = true))
        assertEquals(false, isDarkTheme(ScreenTheme.SYSTEM, isSystemDark = false))
    }

    @Test
    @Config(qualifiers = "night")
    fun `기기가 다크일 때 SYSTEM은 다크 벌, LIGHT로 바꾸면 그 자리에서 라이트 벌`() {
        var theme by mutableStateOf(ScreenTheme.SYSTEM)
        var colors: RunkkColors? = null
        var compositions = 0
        compose.setContent {
            RunkkAppTheme(theme) {
                colors = LocalRunkkColors.current
                compositions++
            }
        }
        compose.runOnIdle { assertSame(RunkkColors.dark, colors) }

        theme = ScreenTheme.LIGHT
        compose.runOnIdle { assertSame(RunkkColors.light, colors) }

        theme = ScreenTheme.DARK
        compose.runOnIdle {
            assertSame(RunkkColors.dark, colors)
            assertEquals(3, compositions)
        }
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `기기가 라이트일 때 SYSTEM은 라이트 벌`() {
        var colors: RunkkColors? = null
        compose.setContent { RunkkAppTheme(ScreenTheme.SYSTEM) { colors = LocalRunkkColors.current } }
        compose.runOnIdle { assertSame(RunkkColors.light, colors) }
    }
}
