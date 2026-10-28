package com.a207.runkk.app.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 명도 대비(NFR-041, FR-203): 두 색 벌 모두 본문 글자/배경 짝이 4.5:1 이상이다.
 * 글자 토큰(이름이 text·on으로 시작하는 것과 강조 글자 lime·lavender)은 모두 짝 목록에 있어야 한다.
 * 새 글자 토큰을 짝 목록에 넣지 않으면 시험이 "짝 목록 누락"으로 실패한다(TC-6534).
 */
class ColorContrastTest {
    @Test
    fun `TC-6531 대비 식은 WCAG 상대 휘도로 계산하고 4·5 경계를 가른다`() {
        val cases =
            listOf(
                Triple(0xFF000000, 0xFFFFFFFF, 21.0),
                Triple(0xFF767676, 0xFFFFFFFF, 4.54),
                Triple(0xFF777777, 0xFFFFFFFF, 4.48),
                Triple(0xFFFFFFFF, 0xFFFFFFFF, 1.0)
            )
        val ratios = cases.map { (fg, bg, _) -> contrastRatio(Color(fg), Color(bg)).round2() }
        assertEquals(cases.map { it.third }, ratios)
        assertEquals(listOf(true, true, false, false), cases.map { (fg, bg, _) -> isReadable(Color(fg), Color(bg)) })
    }

    @Test
    fun `TC-6532 라이트 벌의 본문 글자·배경 짝은 모두 4·5 이상이다`() {
        assertTrue(TEXT_PAIRS.isNotEmpty())
        assertEquals(emptyList(), contrastViolations(tokensOf(RunkkColors.light), TEXT_PAIRS))
    }

    @Test
    fun `TC-6533 다크 벌의 본문 글자·배경 짝은 모두 4·5 이상이다`() {
        assertEquals(emptyList(), contrastViolations(tokensOf(RunkkColors.dark), TEXT_PAIRS))
    }

    @Test
    fun `라이트 벌의 컨트롤 경계와 단계 막대는 바탕 대비 3 이상이다 (WCAG 1·4·11)`() {
        val light = tokensOf(RunkkColors.light)
        val low =
            listOf("borderStrong", "inputBorder", "limeDim").flatMap { token ->
                listOf("background", "surface").mapNotNull { bg ->
                    val ratio = contrastRatio(light.getValue(token), light.getValue(bg))
                    if (ratio >= NON_TEXT_RATIO) null else "$token/$bg ${ratio.round2()}"
                }
            }
        assertEquals(emptyList(), low)
    }

    @Test
    fun `TC-6534 짝 목록에 없는 글자 토큰이 있으면 누락으로 실패한다`() {
        val fixture = tokensOf(RunkkColors.light) + ("textTertiary" to Color(0xFF52525B))
        val violations = contrastViolations(fixture, TEXT_PAIRS)
        assertEquals(listOf("짝 목록 누락: textTertiary"), violations)
    }

    @Test
    fun `두 벌은 같은 토큰을 갖고 글자 토큰을 찾는다`() {
        val light = tokensOf(RunkkColors.light)
        assertEquals(tokensOf(RunkkColors.dark).keys, light.keys)
        assertEquals(setOf("textPrimary", "textSecondary", "onLime", "lime", "lavender"), textTokens(light))
    }

    /** 글자 토큰: text·on으로 시작하는 이름과 강조 글자에도 쓰는 lime·lavender. */
    private fun textTokens(tokens: Map<String, Color>): Set<String> =
        tokens.keys.filter { it.startsWith("text") || ON_PREFIX.matches(it) || it in ACCENT_TEXT }.toSet()

    private fun contrastViolations(tokens: Map<String, Color>, pairs: List<Pair<String, String>>): List<String> {
        val missing = (textTokens(tokens) - pairs.map { it.first }.toSet()).sorted().map { "짝 목록 누락: $it" }
        val low =
            pairs.mapNotNull { (text, background) ->
                val fg = tokens.getValue(text)
                val bg = tokens.getValue(background)
                val ratio = contrastRatio(fg, bg)
                if (ratio >= MIN_RATIO) null else "$text/$background ${ratio.round2()}"
            }
        return missing + low
    }

    /** 색 벌의 Color 속성 이름 → 값(Color는 JVM에서 long 필드다). */
    private fun tokensOf(colors: RunkkColors): Map<String, Color> = RunkkColors::class.java.declaredFields
        .filter { it.type == Long::class.javaPrimitiveType }
        .associate { field ->
            field.isAccessible = true
            field.name to Color(field.getLong(colors).toULong())
        }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val a = fg.luminance().toDouble() + LUMINANCE_OFFSET
        val b = bg.luminance().toDouble() + LUMINANCE_OFFSET
        return maxOf(a, b) / minOf(a, b)
    }

    private fun isReadable(fg: Color, bg: Color): Boolean = contrastRatio(fg, bg).round2() >= MIN_RATIO

    private fun Double.round2(): Double = (this * 100).roundToInt() / 100.0

    private companion object {
        const val MIN_RATIO = 4.5
        const val NON_TEXT_RATIO = 3.0
        const val LUMINANCE_OFFSET = 0.05
        val ON_PREFIX = Regex("on[A-Z].*")
        val ACCENT_TEXT = setOf("lime", "lavender")

        /** 화면에서 글자를 그 배경 위에 그리는 짝(글자 토큰, 배경 토큰). */
        val TEXT_PAIRS: List<Pair<String, String>> =
            listOf(
                "textPrimary" to "background",
                "textPrimary" to "surface",
                "textPrimary" to "surfacePressed",
                "textPrimary" to "limeContainer",
                "textPrimary" to "backgroundLocked",
                "textSecondary" to "background",
                "textSecondary" to "surface",
                "textSecondary" to "backgroundLocked",
                "textSecondary" to "limeContainer",
                "textSecondary" to "surfacePressed",
                "textPrimary" to "border",
                "lime" to "background",
                "lime" to "surface",
                "lime" to "limeContainer",
                "lime" to "backgroundLocked",
                "onLime" to "lime",
                "onLime" to "limePressed",
                "onLime" to "lavender",
                "lavender" to "background",
                "lavender" to "surface",
                "lavender" to "backgroundLocked"
            )
    }
}
