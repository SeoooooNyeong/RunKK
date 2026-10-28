package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 런크크 색 토큰 한 벌(FR-203, NFR-041). [dark]는 하이파이 "다크 네온" 시안이고 [light]는 같은 역할을 밝은 바탕에 옮긴 벌이다.
 * RunkkTheme(darkTheme)이 벌을 골라 [LocalRunkkColors]로 내려 주고, 화면은 `RunkkColors.TextPrimary`처럼 지금 벌의 토큰을 읽는다.
 * 본문 글자/배경 짝은 두 벌 모두 명도 대비 4.5:1 이상이다(ColorContrastTest가 짝 목록으로 지킨다).
 * Compose 밖(공유 카드 비트맵 등)에서는 [dark]·[light]의 소문자 속성을 직접 읽는다.
 */
@Immutable
class RunkkColors(
    /** 화면 배경. */
    val background: Color,
    /** 화면 잠금 배경(다크는 OLED 절전 검정). */
    val backgroundLocked: Color,
    /** 배경 위 장식 글자(로그인 "RUN"). 읽는 글자가 아니다. */
    val backgroundDecor: Color,
    /** 카드·버튼 표면. */
    val surface: Color,
    /** 눌린 표면(hover 근사). */
    val surfacePressed: Color,
    /** 기본 테두리. */
    val border: Color,
    /** 강한 테두리(보조 버튼·아이콘 버튼·칩 등 컨트롤 경계). 라이트 벌은 배경 대비 3:1 이상(WCAG 1.4.11). */
    val borderStrong: Color,
    /** 입력칸·체크박스 경계(WCAG 1.4.11 비텍스트 대비 3:1 이상). */
    val inputBorder: Color,
    /** 눌린 테두리(hover 근사), 비강조 막대. */
    val borderHover: Color,
    /** 본문 글자. */
    val textPrimary: Color,
    /** 보조 글자. */
    val textSecondary: Color,
    /** 포인트 색(기본 CTA, 선택, 강조 글자). 다크는 라임, 라이트는 짙은 올리브다. */
    val lime: Color,
    /** 포인트 눌림(hover 근사). */
    val limePressed: Color,
    /** 단계 표시용 어두운 포인트(심박 구간 막대 등, 글자에 쓰지 않는다). */
    val limeDim: Color,
    /** 포인트 위 글자. */
    val onLime: Color,
    /** 선택 칩·강조 배너 배경. */
    val limeContainer: Color,
    /** 상대·실시간·종료·그만두기 강조. */
    val lavender: Color,
    /** 브랜드 바이올렛. */
    val violet: Color,
    /** 브랜드 잉크. */
    val ink: Color,
    /** 어두운 벌인가(시스템 막대 아이콘 색 등). */
    val isDark: Boolean
) {
    companion object {
        /** 하이파이 "다크 네온" 시안 색. */
        val dark =
            RunkkColors(
                background = Color(0xFF09090B),
                backgroundLocked = Color(0xFF000000),
                backgroundDecor = Color(0xFF141416),
                surface = Color(0xFF18181B),
                surfacePressed = Color(0xFF1F1F23),
                border = Color(0xFF27272A),
                borderStrong = Color(0xFF3F3F46),
                inputBorder = Color(0xFF71717A),
                borderHover = Color(0xFF52525B),
                textPrimary = Color(0xFFFAFAFA),
                textSecondary = Color(0xFFA1A1AA),
                lime = Color(0xFFCBEA2B),
                limePressed = Color(0xFFD6F04F),
                limeDim = Color(0xFFA3BD22),
                onLime = Color(0xFF09090B),
                limeContainer = Color(0xFF262B10),
                lavender = Color(0xFF9C92F0),
                violet = Color(0xFF5B4FD9),
                ink = Color(0xFF1C2112),
                isDark = true
            )

        /**
         * 밝은 바탕 벌. 라임(#CBEA2B)은 흰 바탕 위 글자 대비가 1.4:1이라 포인트 색을 짙은 올리브로 옮기고 그 위 글자를 흰색으로 둔다.
         * 상대 강조는 브랜드 바이올렛을 쓴다.
         */
        val light =
            RunkkColors(
                background = Color(0xFFFAFAFA),
                backgroundLocked = Color(0xFFE4E4E7),
                backgroundDecor = Color(0xFFEFEFF1),
                surface = Color(0xFFFFFFFF),
                surfacePressed = Color(0xFFF0F0F2),
                border = Color(0xFFE4E4E7),
                borderStrong = Color(0xFF8A8A93),
                inputBorder = Color(0xFF71717A),
                borderHover = Color(0xFFA1A1AA),
                textPrimary = Color(0xFF18181B),
                textSecondary = Color(0xFF52525B),
                lime = Color(0xFF3F6212),
                limePressed = Color(0xFF4D7C0F),
                limeDim = Color(0xFF5F8A1C),
                onLime = Color(0xFFFFFFFF),
                limeContainer = Color(0xFFECF5D0),
                lavender = Color(0xFF5B4FD9),
                violet = Color(0xFF5B4FD9),
                ink = Color(0xFF1C2112),
                isDark = false
            )

        // 지금 테마 벌의 토큰(화면 코드가 읽는 이름). RunkkTheme 밖에서 읽으면 다크 벌이다.

        val Background: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.background

        val BackgroundLocked: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backgroundLocked

        val BackgroundDecor: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backgroundDecor

        val Surface: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.surface

        val SurfacePressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.surfacePressed

        val Border: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.border

        val BorderStrong: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.borderStrong

        val InputBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.inputBorder

        val BorderHover: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.borderHover

        val TextPrimary: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.textPrimary

        val TextSecondary: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.textSecondary

        val Lime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.lime

        val LimePressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limePressed

        val LimeDim: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeDim

        val OnLime: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onLime

        val LimeContainer: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeContainer

        val Lavender: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.lavender

        val Violet: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.violet

        val Ink: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.ink
    }
}

/** 지금 테마의 색 벌. RunkkTheme이 넣는다. 넣지 않으면 다크 벌이다. */
val LocalRunkkColors = staticCompositionLocalOf { RunkkColors.dark }
