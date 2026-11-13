package com.a207.runkk.app.ui.avatar

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.a207.runkk.app.domain.avatar.Shop

/**
 * 상점 그림 규칙(design-oo 05-30 5절 [임시:D-성-113], 피그마 AV-9 썸네일 규격): 아이템 그림 키(서버 assetKey) `k`의 그림은 drawable
 * `shop_k`(투명 바탕 512×512, 무손실 WebP)다. 지금 그림은 피그마 상점 제안의 원본 16종(V62 시드)을 줄인 임시본이고, 의상 파이프라인이
 * 같은 이름으로 만들면 바꿔 넣는다. 표에 없는 키는 null이고 칸은 부위 실루엣([ItemArt])을 그린다.
 */
@DrawableRes
internal fun shopArtRes(imageKey: String?): Int? = when (imageKey) {
    "top_lime_singlet" -> R.drawable.shop_top_lime_singlet
    "top_dawn_windbreaker" -> R.drawable.shop_top_dawn_windbreaker
    "top_crew_tee" -> R.drawable.shop_top_crew_tee
    "top_winter_hoodie" -> R.drawable.shop_top_winter_hoodie
    "top_cherry_vest" -> R.drawable.shop_top_cherry_vest
    "top_finisher_tee" -> R.drawable.shop_top_finisher_tee
    "bottom_running_shorts" -> R.drawable.shop_bottom_running_shorts
    "bottom_jogger_pants" -> R.drawable.shop_bottom_jogger_pants
    "bottom_leggings" -> R.drawable.shop_bottom_leggings
    "shoes_basic_runner" -> R.drawable.shop_shoes_basic_runner
    "shoes_lime_runner" -> R.drawable.shop_shoes_lime_runner
    "shoes_carbon_racer" -> R.drawable.shop_shoes_carbon_racer
    "hat_running_cap" -> R.drawable.shop_hat_running_cap
    "hat_headband" -> R.drawable.shop_hat_headband
    "hat_beanie" -> R.drawable.shop_hat_beanie
    "hat_ten_runs_cap" -> R.drawable.shop_hat_ten_runs_cap
    else -> null
}

/**
 * 잠긴 아이템 칸의 짧은 조건(피그마 AV-9 상태 규칙: 21.1km, 100km, 10회). 서버는 문장(unlockCondition)만 주므로 V62 잠긴 세 키만 앱이
 * 숫자로 줄여 보이고, 그 밖은 null(자물쇠만)이다. 문장은 읽어 주기와 고를 때 안내에 그대로 쓴다.
 */
@StringRes
internal fun unlockShortLabelRes(imageKey: String?): Int? = when (imageKey) {
    "top_finisher_tee" -> R.string.avatar_unlock_short_half
    "shoes_carbon_racer" -> R.string.avatar_unlock_short_100km
    "hat_ten_runs_cap" -> R.string.avatar_unlock_short_ten_runs
    else -> null
}

/** 아이템 그림: 상점 그림이 있으면 그 그림, 없으면 부위 실루엣. 장식이라 읽지 않는다(이름은 칸이 읽는다). */
@Composable
internal fun ItemPicture(item: Shop.Item, modifier: Modifier = Modifier) {
    val res = shopArtRes(item.imageKey)
    if (res == null) {
        ItemArt(item, isSelected = false, modifier = modifier)
    } else {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.clearAndSetSemantics {}
        )
    }
}
