package com.a207.runkk.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun notoSansKr(weight: Int): Font = Font(
    resId = R.font.noto_sans_kr,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/**
 * 본문 글꼴 Noto Sans KR(OFL, 400·500·700·900). 가변 글꼴 하나를 KS X 1001 한글 2,350자 + 라틴으로 줄여 넣었다.
 * 목록에 없는 글자는 시스템 글꼴(Android 기본 한글은 Noto Sans CJK)로 이어 그린다.
 */
val NotoSansKr: FontFamily = FontFamily(notoSansKr(400), notoSansKr(500), notoSansKr(700), notoSansKr(900))

/** 숫자·영문 디스플레이 글꼴 Barlow Condensed Italic(OFL, 700·800). */
val BarlowCondensed: FontFamily = FontFamily(
    Font(R.font.barlow_condensed_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.barlow_condensed_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic)
)

/** 시안의 글자 크기·굵기 묶음. 한글은 [text], 숫자·영문 디스플레이는 [display]. */
object RunkkTextStyles {
    /** 본문(Noto Sans KR) 스타일. 시안의 font-size·font-weight를 그대로 넘긴다. */
    fun text(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        lineHeight: Int? = null,
        letterSpacingEm: Float = 0f
    ): TextStyle = TextStyle(
        fontFamily = NotoSansKr,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeight?.sp ?: TextUnit.Unspecified,
        letterSpacing = if (letterSpacingEm == 0f) TextUnit.Unspecified else letterSpacingEm.em
    )

    /** 디스플레이(Barlow Condensed Italic) 스타일. 기본 800. */
    fun display(
        size: Int,
        weight: FontWeight = FontWeight.ExtraBold,
        letterSpacingEm: Float = 0f,
        lineHeightRatio: Float? = null
    ): TextStyle = TextStyle(
        fontFamily = BarlowCondensed,
        fontStyle = FontStyle.Italic,
        fontSize = size.sp,
        fontWeight = weight,
        lineHeight = lineHeightRatio?.let { (size * it).sp } ?: TextUnit.Unspecified,
        letterSpacing = if (letterSpacingEm == 0f) TextUnit.Unspecified else letterSpacingEm.em
    )

    /** 화면 제목(가입 단계 h1): 24/32, 900, -0.03em. */
    val Headline: TextStyle = text(24, FontWeight.Black, lineHeight = 32, letterSpacingEm = -0.03f)

    /** 상단 바 제목: 18, 900. */
    val TopBarTitle: TextStyle = text(18, FontWeight.Black)

    /** 단계 상단 바 제목: 16, 700. */
    val StepTitle: TextStyle = text(16, FontWeight.Bold)

    /** 카드 제목: 16, 900. */
    val CardTitle: TextStyle = text(16, FontWeight.Black)

    /** 본문: 14. */
    val Body: TextStyle = text(14)

    /** 본문 강조: 14, 700. */
    val BodyStrong: TextStyle = text(14, FontWeight.Bold)

    /** 라벨: 13. */
    val Label: TextStyle = text(13)

    /** 캡션: 12. */
    val Caption: TextStyle = text(12)

    /** 작은 캡션(하단 탭, 통계 라벨): 11. */
    val Micro: TextStyle = text(11)

    /** 기본 CTA 버튼 글자: 16, 900. */
    val Button: TextStyle = text(16, FontWeight.Black)

    /** 큰 수치(러닝 거리): 136. */
    val DisplayHuge: TextStyle = display(136, letterSpacingEm = -0.02f, lineHeightRatio = 0.9f)

    /** 결과 거리: 104. */
    val DisplayLarge: TextStyle = display(104, lineHeightRatio = 0.9f)

    /** 통계 타일 값: 36. */
    val DisplayMedium: TextStyle = display(36, lineHeightRatio = 1f)

    /** 작은 통계 값: 26. */
    val DisplaySmall: TextStyle = display(26, lineHeightRatio = 1f)

    /** 모드 라벨("RUNNING · 5km"): 16, 0.08em. */
    val Overline: TextStyle = display(16, letterSpacingEm = 0.08f)
}

/** Material 컴포넌트(TextField, Dialog 등)가 쓰는 기본 글꼴을 Noto Sans KR로 맞춘다. */
internal val RunkkTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = BarlowCondensed, fontStyle = FontStyle.Italic),
        displayMedium = displayMedium.copy(fontFamily = BarlowCondensed, fontStyle = FontStyle.Italic),
        displaySmall = displaySmall.copy(fontFamily = BarlowCondensed, fontStyle = FontStyle.Italic),
        headlineLarge = headlineLarge.copy(fontFamily = NotoSansKr),
        headlineMedium = headlineMedium.copy(fontFamily = NotoSansKr),
        headlineSmall = RunkkTextStyles.Headline,
        titleLarge = RunkkTextStyles.TopBarTitle,
        titleMedium = RunkkTextStyles.CardTitle,
        titleSmall = RunkkTextStyles.BodyStrong,
        bodyLarge = RunkkTextStyles.text(16),
        bodyMedium = RunkkTextStyles.Body,
        bodySmall = RunkkTextStyles.Caption,
        labelLarge = RunkkTextStyles.text(14, FontWeight.Bold),
        labelMedium = RunkkTextStyles.Caption,
        labelSmall = RunkkTextStyles.Micro
    )
}
