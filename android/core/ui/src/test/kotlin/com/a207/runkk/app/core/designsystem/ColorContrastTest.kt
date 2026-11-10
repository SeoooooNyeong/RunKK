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
        assertEquals(
            setOf(
                "textPrimary",
                "textSecondary",
                "onLime",
                "lime",
                "lavender",
                "onLimeFill",
                "onSelectedFill",
                "onTabSelected",
                "onSecondary",
                "onAccent",
                "onTagPurple",
                "onTagLime",
                "onChipSelected"
            ),
            textTokens(light)
        )
    }

    @Test
    fun `S15P31A207-292 라이트 벌은 중립색이 원래 무채색이고 브랜드 색이 피그마 Runkk · Theme Light 값이다`() {
        val light = RunkkColors.light
        val expected = mapOf(
            "background" to 0xFFFAFAFA,
            "surface" to 0xFFFFFFFF,
            "surfaceHigh" to 0xFFE4E4E7,
            "border" to 0xFFE4E4E7,
            "textPrimary" to 0xFF18181B,
            "textSecondary" to 0xFF52525B,
            "limeFill" to 0xFFCAEB2E,
            "onLimeFill" to 0xFF171B12,
            "violet" to 0xFF5C4BDB,
            "onSecondary" to 0xFFFFFFFF,
            "accent" to 0xFF9B8DEE,
            "onAccent" to 0xFF171B12,
            "accentContainer" to 0xFFEBE7FC,
            "secondaryContainer" to 0xFFE3DFF9,
            "accentMuted" to 0xFF7569BF,
            "barCrew" to 0xFFD4D4D8,
            "tagPurple" to 0xFFEBE7FC,
            "onTagPurple" to 0xFF5C4BDB,
            "tagLime" to 0xFFF2F9D0,
            "onTagLime" to 0xFF3D4A00,
            "segmentSelected" to 0xFFFFFFFF,
            "chipSelected" to 0xFF52525B,
            "onChipSelected" to 0xFFFFFFFF,
            "outline" to 0xFFD4D4D8,
            "radioStroke" to 0xFFE4E4E7,
            "radioSelectedOuter" to 0xFFE4E4E7,
            "radioDot" to 0xFF18181B,
            "todayMark" to 0xFFE4E4E7,
            "inputHigh" to 0xFFE4E4E7,
            "menuDivider" to 0xFFE4E4E7,
            "toggleOff" to 0xFFE4E4E7,
            "lockShortcutIcon" to 0xFFFFFFFF,
            "chatMine" to 0xFFFFFFFF,
            "ink" to 0xFF171B12
        )
        assertEquals(
            expected.mapValues {
                Color(it.value)
            },
            expected.keys.associateWith { tokensOf(light).getValue(it) }
        )
        assertEquals(Color.Black.copy(alpha = 0.45f), light.scrim)
        assertEquals(Color(0x00000000), light.imageScrim)
    }

    @Test
    fun `S15P31A207-292 다크 벌은 중립색이 원래 무채색이고 브랜드 색이 피그마 Runkk · Theme Dark 값이다`() {
        val dark = RunkkColors.dark
        val expected = mapOf(
            "background" to 0xFF09090B,
            "surface" to 0xFF18181B,
            "surfaceHigh" to 0xFF27272A,
            "border" to 0xFF27272A,
            "textPrimary" to 0xFFFAFAFA,
            "textSecondary" to 0xFFA1A1AA,
            "limeFill" to 0xFFCAEB2E,
            "onLimeFill" to 0xFF171B12,
            "violet" to 0xFF5C4BDB,
            "onSecondary" to 0xFFFFFFFF,
            "accent" to 0xFF9B8DEE,
            "onAccent" to 0xFF171B12,
            "accentContainer" to 0xFF3A3452,
            "secondaryContainer" to 0xFF302856,
            "accentMuted" to 0xFF9E94D4,
            "barCrew" to 0xFFA1A1AA,
            "tagPurple" to 0xFFCFC4FF,
            "onTagPurple" to 0xFF2B1F6B,
            "tagLime" to 0xFFF2F9D0,
            "onTagLime" to 0xFF3D4A00,
            "segmentSelected" to 0xFF52525B,
            "chipSelected" to 0xFFE4E4E7,
            "onChipSelected" to 0xFF18181B,
            "outline" to 0xFF71717A,
            "radioStroke" to 0xFF71717A,
            "radioSelectedOuter" to 0xFF5C4ADB,
            "radioDot" to 0xFFFFFFFF,
            "todayMark" to 0xFF3F3F46,
            "inputHigh" to 0xFF3F3F46,
            "menuDivider" to 0xFF71717A,
            "toggleOff" to 0xFF52525B,
            "lockShortcutIcon" to 0xFF171B12,
            "ink" to 0xFF171B12
        )
        assertEquals(
            expected.mapValues {
                Color(it.value)
            },
            expected.keys.associateWith { tokensOf(dark).getValue(it) }
        )
        assertEquals(Color.Black.copy(alpha = 0.45f), dark.scrim)
        assertEquals(Color.Black.copy(alpha = 0.58f), dark.imageScrim)
    }

    @Test
    fun `S15P31A207-292 중립색 토큰은 두 벌 모두 녹색 기운이 없는 무채색이다 (D-성-93)`() {
        val greenish =
            listOf("light" to RunkkColors.light, "dark" to RunkkColors.dark).flatMap { (name, colors) ->
                val tokens = tokensOf(colors) + ("scrim" to colors.scrim) + ("imageScrim" to colors.imageScrim)
                NEUTRAL_TOKENS.mapNotNull { token ->
                    val color = tokens.getValue(token)
                    // zinc 계열은 R = G ≤ B다. 초록이 빨강·파랑보다 크면 피그마 회녹색이 남은 것이다.
                    if (color.green > maxOf(color.red, color.blue)) "$name $token" else null
                }
            }
        assertEquals(emptyList(), greenish)
    }

    @Test
    fun `S15P31A207-292 고른 칸·탭은 두 벌 모두 라임 채움에 검은 글자이고 Runimal 바탕은 surfaceHigh다`() {
        listOf(RunkkColors.light, RunkkColors.dark).forEach { colors ->
            assertEquals(
                listOf(colors.limeFill, colors.limeFill, colors.limeFill),
                listOf(colors.selectedFill, colors.selectedBorder, colors.tabSelected)
            )
            assertEquals(colors.onLimeFill, colors.onSelectedFill)
            assertEquals(colors.onLimeFill, colors.onTabSelected)
            assertEquals(colors.surfaceHigh, colors.runimalTile)
        }
    }

    @Test
    fun `TC-5818 티어 배지 글자와 바탕 대비는 라이트·다크 모두 4·5 이상이다`() {
        val sets = listOf(
            Triple("light", RunkkTierColors.light, RunkkColors.light),
            Triple("dark", RunkkTierColors.dark, RunkkColors.dark)
        )
        val low = sets.flatMap { (name, tiers, colors) ->
            val confirmed = listOf("BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER").map { tier ->
                val palette = tiers.of(tier)
                listOf("$name $tier 확정" to (palette.content to palette.container)) +
                    // 임시 배지는 바탕이 표면이다.
                    ("$name $tier 임시" to (palette.content to colors.surface))
            }.flatten()
            val placing = listOf("$name 배치 중" to (tiers.unranked.content to tiers.unranked.container))
            val podium = listOf(tiers.podiumFirst, tiers.podiumSecond, tiers.podiumThird).map {
                "$name 받침 $it" to (tiers.onPodium to it)
            }
            (confirmed + placing + podium).mapNotNull { (label, pair) ->
                val ratio = contrastRatio(pair.first, pair.second).round2()
                if (ratio >= MIN_RATIO) null else "$label $ratio"
            }
        }
        assertEquals(emptyList(), low)
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

        /** 원래 앱 무채색을 유지하는 중립 토큰(사용자 결정 2026-11-10). */
        val NEUTRAL_TOKENS =
            listOf(
                "background",
                "backgroundLocked",
                "backgroundDecor",
                "surface",
                "surfacePressed",
                "surfaceHigh",
                "border",
                "borderStrong",
                "inputBorder",
                "borderHover",
                "outline",
                "textPrimary",
                "textSecondary",
                "runimalTile",
                "runimalTileBorder",
                "barCrew",
                "segmentSelected",
                "chipSelected",
                "onChipSelected",
                "chatMineStroke",
                "radioStroke",
                "todayMark",
                "inputHigh",
                "menuDivider",
                "toggleOff",
                "scrim",
                "imageScrim"
            )

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
                "textSecondary" to "surfaceHigh",
                "textPrimary" to "border",
                "lime" to "background",
                "lime" to "surface",
                "lime" to "limeContainer",
                "lime" to "backgroundLocked",
                "onLime" to "lime",
                "onLime" to "limePressed",
                "onLime" to "lavender",
                "onLimeFill" to "limeFill",
                "onLimeFill" to "limeFillPressed",
                "onSelectedFill" to "selectedFill",
                "onTabSelected" to "tabSelected",
                "onSecondary" to "violet",
                "onAccent" to "accent",
                "onTagPurple" to "tagPurple",
                "onTagLime" to "tagLime",
                "onChipSelected" to "chipSelected",
                "textPrimary" to "surfaceHigh",
                "textPrimary" to "segmentSelected",
                "lavender" to "background",
                "lavender" to "surface",
                "lavender" to "backgroundLocked"
            )
    }
}
