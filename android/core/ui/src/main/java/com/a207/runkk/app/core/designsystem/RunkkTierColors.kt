package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 티어 색 토큰 한 벌(피그마 RunKK · Tier 변수, D-496). RunkkTheme이 RunkkColors와 함께 [LocalRunkkTierColors]로 내려 준다.
 * 시상대 받침 3색·받침 숫자색·안 고른 기간 칩 바탕도 이 벌에 둔다(랭킹, 05-21 8절 7·17).
 * 확정 배지 글자/바탕 대비는 두 벌 모두 4.5:1 이상이다(ColorContrastTest TC-5818). 그래서 라이트 브론즈·골드 글자는 피그마 값보다
 * 조금 어둡다(브론즈 #8A4B1F → #85481E, 골드 #8A6400 → #6E4F00, 피그마 값은 대비 4.38·3.28).
 */
@Immutable
class RunkkTierColors(
    val bronze: TierPalette,
    val silver: TierPalette,
    val gold: TierPalette,
    val platinum: TierPalette,
    val master: TierPalette,
    /** 배치 중(UNRANKED). 글자는 TextSecondary, 바탕은 Surface를 쓴다. */
    val unranked: TierPalette,
    /** 마스터 왕관의 라임 별. */
    val masterAccent: Color,
    val podiumFirst: Color,
    val podiumSecond: Color,
    val podiumThird: Color,
    /** 시상대 받침 숫자(받침 색이 두 벌 같아 숫자도 같다). */
    val onPodium: Color,
    /** 랭킹의 안 고른 기간 칩 바탕(고른 칩은 SelectedFill). */
    val periodChip: Color
) {
    /** 서버 Tier 이름의 색. null·UNRANKED·모르는 값은 배치 중 색이다. */
    fun of(tier: String?): TierPalette = when (tier) {
        "BRONZE" -> bronze
        "SILVER" -> silver
        "GOLD" -> gold
        "PLATINUM" -> platinum
        "MASTER" -> master
        else -> unranked
    }

    companion object {
        val light =
            RunkkTierColors(
                bronze = TierPalette(
                    content = Color(0xFF85481E),
                    container = Color(0xFFF0C9A6),
                    emblemBase = Color(0xFFE9A774),
                    emblemShade = Color(0xFFB5693A),
                    emblemHighlight = Color(0xFFFFE3CC)
                ),
                silver = TierPalette(
                    content = Color(0xFF535B66),
                    container = Color(0xFFDCE1E8),
                    emblemBase = Color(0xFFD3DAE3),
                    emblemShade = Color(0xFF8A95A3),
                    emblemHighlight = Color(0xFFFFFFFF)
                ),
                gold = TierPalette(
                    content = Color(0xFF6E4F00),
                    container = Color(0xFFEBC66A),
                    emblemBase = Color(0xFFF5C84B),
                    emblemShade = Color(0xFFC9932A),
                    emblemHighlight = Color(0xFFFFF2C2)
                ),
                platinum = TierPalette(
                    content = Color(0xFF0D6B68),
                    container = Color(0xFFB4E8E3),
                    emblemBase = Color(0xFF6FD6CB),
                    emblemShade = Color(0xFF2E9E95),
                    emblemHighlight = Color(0xFFDDFBF7)
                ),
                master = TierPalette(
                    content = Color(0xFF6328C9),
                    container = Color(0xFFDCCBFF),
                    emblemBase = Color(0xFF8B7BF0),
                    emblemShade = Color(0xFF5C4BDB),
                    emblemHighlight = Color(0xFFE5E0FF)
                ),
                unranked = TierPalette(
                    content = RunkkColors.light.textSecondary,
                    container = RunkkColors.light.surface,
                    emblemBase = Color(0xFFE4E7EC),
                    emblemShade = Color(0xFF9AA0A8),
                    emblemHighlight = Color(0xFFFFFFFF)
                ),
                masterAccent = Color(0xFFCAEB2E),
                podiumFirst = Color(0xFFEBC66A),
                podiumSecond = Color(0xFFDCDFE5),
                podiumThird = Color(0xFFEBC3A0),
                onPodium = Color(0xFF171B12),
                periodChip = Color(0xFFEBE7FC)
            )

        val dark =
            RunkkTierColors(
                bronze = TierPalette(
                    content = Color(0xFFE8A26F),
                    container = Color(0xFF5A3418),
                    emblemBase = Color(0xFFE39C66),
                    emblemShade = Color(0xFFA65E31),
                    emblemHighlight = Color(0xFFFFE3CC)
                ),
                silver = TierPalette(
                    content = Color(0xFFD3D9E2),
                    container = Color(0xFF3A4049),
                    emblemBase = Color(0xFFC6CED9),
                    emblemShade = Color(0xFF7F8A98),
                    emblemHighlight = Color(0xFFFFFFFF)
                ),
                gold = TierPalette(
                    content = Color(0xFFEBC66A),
                    container = Color(0xFF5C4610),
                    emblemBase = Color(0xFFF0BE3A),
                    emblemShade = Color(0xFFB98522),
                    emblemHighlight = Color(0xFFFFF2C2)
                ),
                platinum = TierPalette(
                    content = Color(0xFF7FDCD3),
                    container = Color(0xFF12403F),
                    emblemBase = Color(0xFF5ECDC1),
                    emblemShade = Color(0xFF27918A),
                    emblemHighlight = Color(0xFFDDFBF7)
                ),
                master = TierPalette(
                    content = Color(0xFFC9AEFF),
                    container = Color(0xFF3B2370),
                    emblemBase = Color(0xFF9A8BF5),
                    emblemShade = Color(0xFF6B5AE6),
                    emblemHighlight = Color(0xFFE5E0FF)
                ),
                unranked = TierPalette(
                    content = RunkkColors.dark.textSecondary,
                    container = RunkkColors.dark.surface,
                    emblemBase = Color(0xFFC9CDD3),
                    emblemShade = Color(0xFF7D838C),
                    emblemHighlight = Color(0xFFF2F4F6)
                ),
                masterAccent = Color(0xFFCAEB2E),
                podiumFirst = Color(0xFFEBC66A),
                podiumSecond = Color(0xFFDCDFE5),
                podiumThird = Color(0xFFEBC3A0),
                onPodium = Color(0xFF171B12),
                periodChip = Color(0xFF3B2370)
            )

        /** 지금 테마 벌에서 [tier]의 색. */
        @Composable
        @ReadOnlyComposable
        fun of(tier: String?): TierPalette = LocalRunkkTierColors.current.of(tier)
    }
}

/** 지금 테마의 티어 색 벌. RunkkTheme이 넣는다. 넣지 않으면 다크 벌이다(LocalRunkkColors와 같다). */
val LocalRunkkTierColors = staticCompositionLocalOf { RunkkTierColors.dark }
