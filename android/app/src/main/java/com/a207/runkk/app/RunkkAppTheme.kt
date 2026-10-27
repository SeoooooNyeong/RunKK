package com.a207.runkk.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.domain.settings.ScreenTheme

/** 화면 테마 설정 → 어두운 벌을 쓸지(FR-203). SYSTEM이면 기기 설정을 따른다. */
internal fun isDarkTheme(theme: ScreenTheme, isSystemDark: Boolean): Boolean = when (theme) {
    ScreenTheme.SYSTEM -> isSystemDark
    ScreenTheme.LIGHT -> false
    ScreenTheme.DARK -> true
}

/**
 * 설정 사본의 화면 테마로 RunkkTheme을 그린다(D-92). [theme]이 바뀌면 다시 그려 앱을 다시 시작하지 않고 바로 반영한다.
 * MainActivity가 SettingsRepository.changes()의 theme을 넘긴다.
 */
@Composable
internal fun RunkkAppTheme(theme: ScreenTheme, content: @Composable () -> Unit) {
    RunkkTheme(darkTheme = isDarkTheme(theme, isSystemInDarkTheme()), content = content)
}
