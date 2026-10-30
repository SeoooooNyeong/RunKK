package com.a207.runkk.app.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 서버 티어 코드(Tier enum 이름: UNRANKED, BRONZE, SILVER, GOLD, PLATINUM, MASTER)를 화면 이름으로 바꾼다.
 * 빈 값은 null, 모르는 값은 받은 그대로 보인다(서버에 새 티어가 생긴 경우).
 */
@Composable
fun tierName(code: String?): String? {
    if (code.isNullOrBlank()) return null
    val res = when (code) {
        "UNRANKED" -> R.string.tier_unranked
        "BRONZE" -> R.string.tier_bronze
        "SILVER" -> R.string.tier_silver
        "GOLD" -> R.string.tier_gold
        "PLATINUM" -> R.string.tier_platinum
        "MASTER" -> R.string.tier_master
        else -> return code
    }
    return stringResource(res)
}
