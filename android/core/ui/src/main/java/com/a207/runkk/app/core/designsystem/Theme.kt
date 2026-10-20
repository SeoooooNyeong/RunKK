package com.a207.runkk.app.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val RunkkColorScheme = darkColorScheme(
    primary = RunkkColors.Lime,
    onPrimary = RunkkColors.OnLime,
    primaryContainer = RunkkColors.LimeContainer,
    onPrimaryContainer = RunkkColors.Lime,
    secondary = RunkkColors.Lavender,
    onSecondary = RunkkColors.OnLime,
    tertiary = RunkkColors.Violet,
    background = RunkkColors.Background,
    onBackground = RunkkColors.TextPrimary,
    surface = RunkkColors.Surface,
    onSurface = RunkkColors.TextPrimary,
    surfaceVariant = RunkkColors.Surface,
    onSurfaceVariant = RunkkColors.TextSecondary,
    surfaceContainer = RunkkColors.Surface,
    surfaceContainerHigh = RunkkColors.Surface,
    surfaceContainerHighest = RunkkColors.Border,
    outline = RunkkColors.BorderStrong,
    outlineVariant = RunkkColors.Border,
    error = RunkkColors.Lavender,
    onError = RunkkColors.OnLime
)

/** 시안의 모서리 반경(12·16·20·24dp). */
object RunkkShapes {
    val Small = RoundedCornerShape(12.dp)
    val Medium = RoundedCornerShape(16.dp)
    val Large = RoundedCornerShape(20.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
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
 * 런크크 테마(다크 하나). 라이트 테마·동적 색은 쓰지 않는다(하이파이 시안).
 * [reduceMotion]을 넘기지 않으면 기기 설정을 읽는다.
 */
@Composable
fun RunkkTheme(reduceMotion: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = reduceMotion ?: remember(context) { context.prefersReducedMotion() }
    MaterialTheme(
        colorScheme = RunkkColorScheme,
        typography = RunkkTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RunkkShapes.Small,
            medium = RunkkShapes.Medium,
            large = RunkkShapes.Large,
            extraLarge = RunkkShapes.ExtraLarge
        )
    ) {
        CompositionLocalProvider(LocalReduceMotion provides reduce, content = content)
    }
}
