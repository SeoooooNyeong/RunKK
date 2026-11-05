package com.a207.runkk.app.core.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 티어 엠블럼 드로어블 고르기(FR-283, D-495): 6티어 × 큰·작은 × 라이트·다크 = 24개. */
@RunWith(RobolectricTestRunner::class)
class TierEmblemTest {
    private val res get() = RuntimeEnvironment.getApplication().resources

    @Test
    fun `TC-5816 엠블럼 드로어블 24개는 티어·크기·다크마다 다르다`() {
        val tiers = listOf("UNRANKED", "BRONZE", "SILVER", "GOLD", "PLATINUM", "MASTER")
        val chosen =
            tiers.flatMap { tier ->
                listOf(TierEmblemSize.LARGE, TierEmblemSize.SMALL).flatMap { size ->
                    listOf(false, true).map { isDark ->
                        val name = res.getResourceEntryName(tierEmblemRes(tier, size, isDark))
                        val expected =
                            "tier_emblem_${tier.lowercase()}_${size.name.lowercase()}" + if (isDark) "_dark" else ""
                        assertEquals(expected, name)
                        name
                    }
                }
            }
        assertEquals(24, chosen.toSet().size)
    }

    @Test
    fun `TC-5817 모르는 티어와 아주 작은 크기는 배치 중·작은 그림을 쓴다`() {
        listOf(null, "UNRANKED", "DIAMOND").forEach { tier ->
            assertEquals(
                "tier_emblem_unranked_small",
                res.getResourceEntryName(tierEmblemRes(tier, TierEmblemSize.XSMALL, isDark = false)),
                "$tier"
            )
        }
        assertEquals(
            "tier_emblem_gold_small_dark",
            res.getResourceEntryName(tierEmblemRes("GOLD", TierEmblemSize.XSMALL, isDark = true))
        )
        assertEquals(14, TierEmblemSize.XSMALL.dp.value.toInt())
        assertEquals(112, TierEmblemSize.LARGE.dp.value.toInt())
    }
}
