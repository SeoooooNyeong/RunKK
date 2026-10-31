package com.a207.runkk.watch.ui

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
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
 * 숫자 글꼴. 하이파이의 Barlow Condensed Italic 800을 글꼴 파일 없이 시스템 sans-serif-condensed +
 * ExtraBold + Italic으로 근사한다(글꼴 리소스를 저장소에 넣지 않았다).
 */
private val NumberFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

internal fun numberStyle(size: TextUnit, color: Color = WatchColors.OnSurface): TextStyle = TextStyle(
    fontFamily = NumberFamily,
    fontWeight = FontWeight.ExtraBold,
    fontStyle = FontStyle.Italic,
    fontSize = size,
    color = color
)

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
