package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 런크크 색 토큰(하이파이 "다크 네온" 시안). 다크 테마 하나만 있다.
 * 화면은 MaterialTheme.colorScheme 대신 이 토큰을 직접 쓴다(시안 색을 그대로 옮기기 위해).
 */
object RunkkColors {
    /** 화면 배경. */
    val Background = Color(0xFF09090B)

    /** 화면 잠금 배경(OLED 절전). */
    val BackgroundLocked = Color(0xFF000000)

    /** 배경 위 장식 글자(로그인 "RUN"). */
    val BackgroundDecor = Color(0xFF141416)

    /** 카드·버튼 표면. */
    val Surface = Color(0xFF18181B)

    /** 눌린 표면(hover 근사). */
    val SurfacePressed = Color(0xFF1F1F23)

    /** 기본 테두리. */
    val Border = Color(0xFF27272A)

    /** 강한 테두리(보조 버튼, 입력칸). */
    val BorderStrong = Color(0xFF3F3F46)

    /** 입력칸·체크박스 경계(배경 위 약 4.1:1, 표면 위 약 3.7:1, WCAG 1.4.11 비텍스트 대비). */
    val InputBorder = Color(0xFF71717A)

    /** 눌린 테두리(hover 근사), 비강조 막대. */
    val BorderHover = Color(0xFF52525B)

    /** 본문 글자. */
    val TextPrimary = Color(0xFFFAFAFA)

    /** 보조 글자. */
    val TextSecondary = Color(0xFFA1A1AA)

    /** 포인트 라임(기본 CTA, 선택). */
    val Lime = Color(0xFFCBEA2B)

    /** 라임 눌림(hover 근사). */
    val LimePressed = Color(0xFFD6F04F)

    /** 어두운 라임(심박 구간 등 단계 표시). */
    val LimeDim = Color(0xFFA3BD22)

    /** 라임 위 글자. */
    val OnLime = Color(0xFF09090B)

    /** 선택 칩·강조 배너 배경. */
    val LimeContainer = Color(0xFF262B10)

    /** 상대·실시간·종료·그만두기 강조. */
    val Lavender = Color(0xFF9C92F0)

    /** 브랜드 바이올렛. */
    val Violet = Color(0xFF5B4FD9)

    /** 브랜드 잉크. */
    val Ink = Color(0xFF1C2112)
}
