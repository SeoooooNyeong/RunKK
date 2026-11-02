package com.a207.runkk.watch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

/** 폰 하이파이와 같은 다크 네온 색(배경·표면·테두리·본문·보조, 포인트 라임, 실시간 라벤더). */
internal object WatchColors {
    val Background = Color(0xFF09090B)
    val Surface = Color(0xFF18181B)
    val Border = Color(0xFF27272A)
    val OnSurface = Color(0xFFFAFAFA)
    val Muted = Color(0xFFA1A1AA)
    val Lime = Color(0xFFCBEA2B)
    val Lavender = Color(0xFF9C92F0)

    /** 뒤처짐·STOP·LOSE(피그마 BR1 "−6m", SS3 "STOP"). */
    val Orange = Color(0xFFF2A46B)

    /** 심박 하트(피그마 FR3 "♥ 151"). */
    val Heart = Color(0xFFF2735F)

    /** 진한 남색 바탕 위 버튼·고리 바탕(피그마 F1 "−"·"+", 측정 고리 남은 부분). */
    val Dark = Color(0xFF1C2620)

    /** 카운트다운 숫자·라임 위 글자(피그마 FC3). */
    val Ink = Color(0xFF1F2A22)

    /** 트랙 그림 위에 글자가 잘 보이게 까는 막(피그마 F1·FR3의 푸른 톤). */
    val Scrim = Color(0x8C203A55)
}

/**
 * 숫자 글꼴(피그마 텍스트 "numerals"): Barlow Condensed 바로 선 글꼴. 큰 숫자·머리말(STOP·GOOD·WIN)은 ExtraBold(800), 작은 값(시간·
 * 페이스)은 Bold(700)다. 파일은 res/font, 라이선스(SIL OFL 1.1)는 assets/licenses/OFL-BarlowCondensed.txt다.
 */
private val NumberFamily = FontFamily(
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold)
)

/** 숫자 글자. [weight]는 큰 숫자면 ExtraBold, 작은 값이면 Bold(피그마 FR3 "1.50"·"9:45"). */
internal fun numberStyle(
    size: TextUnit,
    color: Color = WatchColors.OnSurface,
    weight: FontWeight = FontWeight.ExtraBold
): TextStyle = TextStyle(
    fontFamily = NumberFamily,
    fontWeight = weight,
    fontSize = size,
    color = color
)

/** 결과 머리말(피그마 FS3 "STOP"·SL3 "GOOD", ExtraBold, 자간 0.02em). */
internal fun headlineStyle(size: TextUnit, color: Color): TextStyle =
    numberStyle(size, color).copy(letterSpacing = 0.02.em)

internal fun labelStyle(color: Color = WatchColors.Muted, size: TextUnit = 12.sp): TextStyle = TextStyle(
    fontSize = size,
    fontWeight = FontWeight.Medium,
    color = color
)

private val RunkkWatchColorScheme = ColorScheme(
    primary = WatchColors.Lime,
    onPrimary = WatchColors.Background,
    secondary = WatchColors.Lavender,
    onSecondary = WatchColors.Background,
    background = WatchColors.Background,
    onBackground = WatchColors.OnSurface,
    surfaceContainer = WatchColors.Surface,
    onSurface = WatchColors.OnSurface,
    onSurfaceVariant = WatchColors.Muted,
    outline = WatchColors.Border
)

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RunkkWatchColorScheme, content = content)
}

/**
 * 피그마 300px 화면을 작은 원형 워치(192dp)로 옮긴 글자 크기(×0.64). 결과 머리말 72px, 결과 값 30px(피그마 FS3·SL3).
 */
internal val RESULT_HEADLINE_SIZE = 46.sp
internal val RESULT_VALUE_SIZE = 19.sp
