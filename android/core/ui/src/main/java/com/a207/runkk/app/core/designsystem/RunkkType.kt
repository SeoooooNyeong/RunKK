package com.a207.runkk.app.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun notoSansKr(weight: Int): Font = Font(
    resId = R.font.noto_sans_kr,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/**
 * 본문 글꼴 Noto Sans KR(OFL, 가변 글꼴). KS X 1001 한글 2,350자 + 라틴으로 줄여 넣었다. 목록에 없는 글자는 시스템 글꼴로 이어 그린다.
 * 2026-11-12 PM 결정: 글꼴은 prod(v0.11.6)와 같게 되돌리고, 피그마 글자 크기·굵기 묶음은 그대로 쓴다.
 */
val NotoSansKr: FontFamily =
    FontFamily(notoSansKr(400), notoSansKr(500), notoSansKr(600), notoSansKr(700), notoSansKr(800), notoSansKr(900))

/** 숫자·영문 디스플레이 글꼴 Barlow Condensed Italic(OFL, 700·800, prod와 같다). */
val BarlowCondensed: FontFamily = FontFamily(
    Font(R.font.barlow_condensed_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.barlow_condensed_extrabold_italic, FontWeight.ExtraBold, FontStyle.Italic)
)

/**
 * 글자 크기·굵기 묶음(피그마 RunKK 글자 스타일). 이름 있는 스타일을 먼저 쓰고, 표에 없는 조합만 [text]로 만든다.
 * - [Title] Settings/Title Bold 20/30, [TopBarTitle] Header 제목 Bold 19/23
 * - [DialogTitle] 대화상자 제목 Bold 18/26, [SheetTitle] 바텀시트 제목 Bold 18/22
 * - [Body]·[BodyMedium] Settings/Body·Label 14/22 Regular·Medium, [BodyLarge] 16
 * - [Meta] 메타 줄(C안) 13/20 Regular, [Caption] Settings/Caption 12/18, [Micro] 11
 * - 버튼 글자는 [RunkkButtonSize.textStyle]: Large 16·Medium 15 SemiBold, Small 14·XSmall 12 Medium
 */
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
        letterSpacing = if (letterSpacingEm == 0f) TextUnit.Unspecified else letterSpacingEm.em,
        localeList = KoreanLocale,
        lineBreak = KoreanLineBreak
    )

    /**
     * 한글 줄바꿈: 기본 줄바꿈은 음절마다 끊어 "달려/요"처럼 한 글자만 다음 줄로 넘긴다. 어절(띄어쓰기) 단위로 끊고(Phrase,
     * Android 13 이상) 줄 길이를 고르게 맞춘다. 어절 판단은 글자 로캘을 따르므로 기기 언어와 상관없이 한국어로 둔다.
     */
    private val KoreanLocale = LocaleList("ko-KR")
    private val KoreanLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)

    /** 디스플레이(Barlow Condensed Italic) 스타일. 기본 800(prod와 같다). */
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

    /** 화면 제목(가입 단계 h1): 24/32, Bold, -0.03em. */
    val Headline: TextStyle = text(24, FontWeight.Bold, lineHeight = 32, letterSpacingEm = -0.03f)

    /** 설정 화면 제목(피그마 RunKK/Settings/Title): 20/30, Bold. */
    val Title: TextStyle = text(20, FontWeight.Bold, lineHeight = 30)

    /** 상단 바 제목(피그마 RunKK / Header): 19/23, Bold. */
    val TopBarTitle: TextStyle = text(19, FontWeight.Bold, lineHeight = 23)

    /** 대화상자 제목: 18/26, Bold. */
    val DialogTitle: TextStyle = text(18, FontWeight.Bold, lineHeight = 26)

    /** 바텀시트 제목: 18/22, Bold. */
    val SheetTitle: TextStyle = text(18, FontWeight.Bold, lineHeight = 22)

    /** 단계 상단 바 제목: 16, Bold. */
    val StepTitle: TextStyle = text(16, FontWeight.Bold)

    /** 카드 제목: 16, Bold. */
    val CardTitle: TextStyle = text(16, FontWeight.Bold)

    /** 본문(피그마 Settings/Body): 14/22. */
    val Body: TextStyle = text(14, lineHeight = 22)

    /** 본문 중간 굵기(피그마 Settings/Label): 14/22, Medium. */
    val BodyMedium: TextStyle = text(14, FontWeight.Medium, lineHeight = 22)

    /** 본문 강조: 14, Bold. */
    val BodyStrong: TextStyle = text(14, FontWeight.Bold)

    /** 큰 본문: 16. */
    val BodyLarge: TextStyle = text(16)

    /** 라벨: 13. */
    val Label: TextStyle = text(13)

    /** 메타 줄 글자(C안): 13/20, 보조 글자색으로 쓴다. */
    val Meta: TextStyle = text(13, lineHeight = 20)

    /** 캡션(피그마 Settings/Caption): 12/18. */
    val Caption: TextStyle = text(12, lineHeight = 18)

    /** 작은 캡션(하단 탭, 통계 라벨): 11. */
    val Micro: TextStyle = text(11)

    /** 기본 CTA 버튼 글자: 16, SemiBold(피그마 버튼 Large). */
    val Button: TextStyle = text(16, FontWeight.SemiBold)

    /** 큰 수치(러닝 거리, 피그마 B-2 Bold 96/72). */
    val DisplayHuge: TextStyle = display(96, lineHeightRatio = 0.75f)

    /** 결과 거리: 104. */
    val DisplayLarge: TextStyle = display(104, lineHeightRatio = 0.9f)

    /** 통계 타일 값: 36. */
    val DisplayMedium: TextStyle = display(36, lineHeightRatio = 1f)

    /** 작은 통계 값(피그마 B-2 26 Bold). */
    val DisplaySmall: TextStyle = display(26, lineHeightRatio = 1f)

    /** 모드 라벨("RUNNING · 5km"): 16, 0.08em. */
    val Overline: TextStyle = display(16, letterSpacingEm = 0.08f)
}

/** Material 컴포넌트(TextField, Dialog 등)가 쓰는 기본 글꼴을 Noto Sans KR로 맞춘다(큰 숫자는 Barlow Condensed 기울임, prod와 같다). */
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
        bodyLarge = RunkkTextStyles.BodyLarge,
        bodyMedium = RunkkTextStyles.Body,
        bodySmall = RunkkTextStyles.Caption,
        labelLarge = RunkkTextStyles.text(14, FontWeight.Medium),
        labelMedium = RunkkTextStyles.Caption,
        labelSmall = RunkkTextStyles.Micro
    )
}
