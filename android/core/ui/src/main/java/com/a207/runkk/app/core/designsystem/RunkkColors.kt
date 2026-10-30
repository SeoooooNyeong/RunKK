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
    /** 포인트 색(강조 글자·선·테두리). 다크는 라임, 라이트는 짙은 올리브다. 채움은 [limeFill]을 쓴다. */
    val lime: Color,
    /** 포인트 눌림(hover 근사). */
    val limePressed: Color,
    /** 단계 표시용 어두운 포인트(심박 구간 막대 등, 글자에 쓰지 않는다). */
    val limeDim: Color,
    /** 포인트 위 글자. */
    val onLime: Color,
    /** 선택 칩·강조 배너 배경. */
    val limeContainer: Color,
    /** 라임 채움(주 버튼·자유런 카드·포인트 칩·고른 칩·켜진 스위치). 두 벌 모두 라임이다(R2-05, D85). */
    val limeFill: Color,
    /** 라임 채움 눌림. */
    val limeFillPressed: Color,
    /** 라임 채움 위 글자. */
    val onLimeFill: Color,
    /** 하나만 고르는 선택지([RunkkSegmentOption])의 고른 칸 배경. 다크는 어두운 라임, 라이트는 라임 채움이다. */
    val selectedFill: Color,
    /** 고른 칸 글자. */
    val onSelectedFill: Color,
    /** 고른 칸 테두리. */
    val selectedBorder: Color,
    /** 탭 전환([RunkkSegmentedTabs])의 고른 탭 배경. 다크는 회색, 라이트는 라임 채움이다. */
    val tabSelected: Color,
    /** 고른 탭 글자. */
    val onTabSelected: Color,
    /** 상단 바 뒤로 버튼 원. 다크는 표면, 라이트는 보라 채움이다. */
    val backButton: Color,
    /** 뒤로 버튼 화살표. */
    val onBackButton: Color,
    /** 뒤로 버튼 테두리. */
    val backButtonBorder: Color,
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
                limeFill = Color(0xFFCBEA2B),
                limeFillPressed = Color(0xFFD6F04F),
                onLimeFill = Color(0xFF09090B),
                selectedFill = Color(0xFF262B10),
                onSelectedFill = Color(0xFFCBEA2B),
                selectedBorder = Color(0xFFCBEA2B),
                tabSelected = Color(0xFF27272A),
                onTabSelected = Color(0xFFFAFAFA),
                backButton = Color(0xFF18181B),
                onBackButton = Color(0xFFFAFAFA),
                backButtonBorder = Color(0xFF27272A),
                lavender = Color(0xFF9C92F0),
                violet = Color(0xFF5B4FD9),
                ink = Color(0xFF1C2112),
                isDark = true
            )

        /**
         * 밝은 바탕 벌. 라임(#CBEA2B)은 흰 바탕 위 글자 대비가 1.4:1이라 글자·선에 쓰는 포인트 색([lime])은 짙은 올리브로 둔다.
         * 채움(주 버튼·고른 칩·켜진 스위치)은 피그마대로 라임에 검은 글자다([limeFill], R2-05·D85). 뒤로 버튼은 보라 채움 원이고
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
                limeFill = Color(0xFFCBEA2B),
                limeFillPressed = Color(0xFFB9D624),
                onLimeFill = Color(0xFF171B12),
                selectedFill = Color(0xFFCBEA2B),
                onSelectedFill = Color(0xFF171B12),
                selectedBorder = Color(0xFFCBEA2B),
                tabSelected = Color(0xFFCBEA2B),
                onTabSelected = Color(0xFF171B12),
                backButton = Color(0xFF5C4BDB),
                onBackButton = Color(0xFFFFFFFF),
                backButtonBorder = Color(0xFF5C4BDB),
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

        val LimeFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeFill

        val LimeFillPressed: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.limeFillPressed

        val OnLimeFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onLimeFill

        val SelectedFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.selectedFill

        val OnSelectedFill: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onSelectedFill

        val SelectedBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.selectedBorder

        val TabSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.tabSelected

        val OnTabSelected: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onTabSelected

        val BackButton: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backButton

        val OnBackButton: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.onBackButton

        val BackButtonBorder: Color
            @Composable @ReadOnlyComposable
            get() = LocalRunkkColors.current.backButtonBorder

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
