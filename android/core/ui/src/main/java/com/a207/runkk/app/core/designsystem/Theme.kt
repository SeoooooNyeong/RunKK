package com.a207.runkk.app.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Material 구성 요소(대화상자·스낵바·스위치 등)가 읽는 색. 런크크 토큰 벌에서 만든다. */
private fun RunkkColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = lime,
        onPrimary = onLime,
        primaryContainer = limeContainer,
        onPrimaryContainer = lime,
        secondary = violet,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        tertiary = accent,
        onTertiary = onAccent,
        tertiaryContainer = accentContainer,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surface,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceHigh,
        outline = borderStrong,
        outlineVariant = border,
        scrim = scrim,
        error = lavender,
        onError = onLime
    )
}

private val DarkColorScheme = RunkkColors.dark.toColorScheme()
private val LightColorScheme = RunkkColors.light.toColorScheme()

/** 시안의 모서리 반경(12·16·20·24dp)과 피그마 알약·대화상자·바텀시트 모서리. */
object RunkkShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)

    /** 알약(피그마 모서리 100): 버튼·칩·세그먼트. */
    val Pill = RoundedCornerShape(percent = 50)

    /** 대화상자 카드(피그마 28). */
    val Dialog = RoundedCornerShape(28.dp)

    /** 바텀시트 위 모서리(피그마 28/28/0/0). */
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

/**
 * 동작 줄이기(접근성). 참이면 펄스·확대 같은 장식 애니메이션을 끈다.
 * 기기의 "애니메이션 삭제"(ANIMATOR_DURATION_SCALE = 0)를 따른다. 시험·미리보기에서는 직접 넣는다.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 기기 설정이 애니메이션 삭제인지 읽는다. */
fun Context.prefersReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * 런크크 테마(FR-203). [darkTheme]이 색 벌을 고른다: 참이면 RunkkColors.dark, 거짓이면 RunkkColors.light.
 * 앱은 설정 사본의 화면 테마(SYSTEM이면 기기 설정)로 정해 넘기고(MainActivity, D-92), 넘기지 않으면 기기 설정을 따른다.
 * 동적 색은 쓰지 않는다(하이파이 시안). [reduceMotion]을 넘기지 않으면 기기 설정을 읽는다.
 */
@Composable
fun RunkkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reduce = reduceMotion ?: remember(context) { context.prefersReducedMotion() }
    val colors = if (darkTheme) RunkkColors.dark else RunkkColors.light
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = RunkkTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RunkkShapes.Small,
            medium = RunkkShapes.Medium,
            large = RunkkShapes.Large,
            extraLarge = RunkkShapes.ExtraLarge
        )
    ) {
        CompositionLocalProvider(
            LocalRunkkColors provides colors,
            LocalRunkkTierColors provides if (darkTheme) RunkkTierColors.dark else RunkkTierColors.light,
            LocalReduceMotion provides reduce,
            content = content
        )
    }
}
