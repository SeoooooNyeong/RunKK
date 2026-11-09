package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 티어 엠블럼 그림(FR-283, D-495): 배치 중 회색 원 「?」, 브론즈 원 메달 별1, 실버 둥근 방패, 골드 둥근 육각 별3,
 * 플래티넘 보석, 마스터 왕관 별4. [tier]가 null·UNRANKED·모르는 값이면 배치 중 그림이다. 그림은 낭독하지 않는다
 * (이름은 TierBadge가 읽힌다). 앱 테마는 Compose 수준이라 drawable-night 대신 `_dark` 드로어블을 고른다.
 * 큰 엠블럼의 그림자(drop-shadow)는 벡터로 그릴 수 없어 그리지 않는다.
 */
@Composable
fun TierEmblem(tier: String?, size: TierEmblemSize, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val isDark = LocalRunkkColors.current.isDark
    val isPlacing = tierEmblemKey(tier) == UNRANKED_KEY
    Box(
        modifier = modifier.size(size.dp).alpha(alpha).clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(tierEmblemRes(tier, size, isDark)),
            contentDescription = null,
            modifier = Modifier.size(size.dp)
        )
        // 배치 중 큰 그림의 「?」는 경로가 없어 글자로 겹친다(44/112). 글자 크기 설정과 상관없이 그림 비율을 지킨다.
        if (isPlacing && size == TierEmblemSize.LARGE) {
            val fontSize = with(LocalDensity.current) { (size.dp * QUESTION_RATIO).toSp() }
            Text(
                QUESTION,
                style = TextStyle(
                    fontFamily = IbmPlexSansKr,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    lineHeight = fontSize
                ),
                color = RunkkTierColors.of(null).emblemShade
            )
        }
    }
}

/**
 * 티어·크기·다크에 맞는 엠블럼 드로어블(순수 함수, TC-5816·TC-5817). 아주 작게(XSMALL)는 작은 그림을 쓴다.
 * 이름 규칙: tier_emblem_{티어}_{large·small}(_dark).
 */
@DrawableRes
fun tierEmblemRes(tier: String?, size: TierEmblemSize, isDark: Boolean): Int {
    val isLarge = size == TierEmblemSize.LARGE
    return when (tierEmblemKey(tier)) {
        "bronze" -> pick(isLarge, isDark, BRONZE)
        "silver" -> pick(isLarge, isDark, SILVER)
        "gold" -> pick(isLarge, isDark, GOLD)
        "platinum" -> pick(isLarge, isDark, PLATINUM)
        "master" -> pick(isLarge, isDark, MASTER)
        else -> pick(isLarge, isDark, UNRANKED)
    }
}

private fun tierEmblemKey(tier: String?): String = when (tier) {
    "BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER" -> tier.lowercase()
    else -> UNRANKED_KEY
}

/** 큰 라이트, 큰 다크, 작은 라이트, 작은 다크 순서. */
private fun pick(isLarge: Boolean, isDark: Boolean, set: IntArray): Int =
    set[(if (isLarge) 0 else 2) + (if (isDark) 1 else 0)]

private val UNRANKED = intArrayOf(
    R.drawable.tier_emblem_unranked_large,
    R.drawable.tier_emblem_unranked_large_dark,
    R.drawable.tier_emblem_unranked_small,
    R.drawable.tier_emblem_unranked_small_dark
)
private val BRONZE = intArrayOf(
    R.drawable.tier_emblem_bronze_large,
    R.drawable.tier_emblem_bronze_large_dark,
    R.drawable.tier_emblem_bronze_small,
    R.drawable.tier_emblem_bronze_small_dark
)
private val SILVER = intArrayOf(
    R.drawable.tier_emblem_silver_large,
    R.drawable.tier_emblem_silver_large_dark,
    R.drawable.tier_emblem_silver_small,
    R.drawable.tier_emblem_silver_small_dark
)
private val GOLD = intArrayOf(
    R.drawable.tier_emblem_gold_large,
    R.drawable.tier_emblem_gold_large_dark,
    R.drawable.tier_emblem_gold_small,
    R.drawable.tier_emblem_gold_small_dark
)
private val PLATINUM = intArrayOf(
    R.drawable.tier_emblem_platinum_large,
    R.drawable.tier_emblem_platinum_large_dark,
    R.drawable.tier_emblem_platinum_small,
    R.drawable.tier_emblem_platinum_small_dark
)
private val MASTER = intArrayOf(
    R.drawable.tier_emblem_master_large,
    R.drawable.tier_emblem_master_large_dark,
    R.drawable.tier_emblem_master_small,
    R.drawable.tier_emblem_master_small_dark
)

private const val UNRANKED_KEY = "unranked"
private const val QUESTION = "?"
private const val QUESTION_RATIO = 44f / 112f
