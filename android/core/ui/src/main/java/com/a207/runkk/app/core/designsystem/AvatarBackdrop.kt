package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * 아바타 랜드마크 배경(FR-201, FR-202, D-107). 고른 배경 키로 그림을 찾아 아바타·프로필·러닝 카드 뒤에 깐다.
 * 장식 그림이라 보조 기술에는 알리지 않는다(이름은 화면이 글자로 보인다).
 * [backgroundKey]가 null이면 기본 배경, 모르는 키는 [stage](서버 단계 이름)별 기본 그림, 단계도 모르면 기본 배경이다.
 */
@Composable
fun AvatarBackdrop(backgroundKey: String?, modifier: Modifier = Modifier, stage: String? = null) {
    Image(
        painter = painterResource(avatarBackdropRes(backgroundKey, stage)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** 배경 키 → 그림 리소스. Compose 밖(공유 카드 비트맵)에서도 쓴다. */
@DrawableRes
fun avatarBackdropRes(backgroundKey: String?, stage: String? = null): Int {
    if (backgroundKey == null) return R.drawable.avatar_backdrop_default
    return when (STAGE_OF_KEY[backgroundKey] ?: stage) {
        DOMESTIC -> R.drawable.avatar_backdrop_domestic
        EAST_ASIA -> R.drawable.avatar_backdrop_east_asia
        INTERCONTINENTAL -> R.drawable.avatar_backdrop_intercontinental
        else -> R.drawable.avatar_backdrop_default
    }
}

private const val DOMESTIC = "DOMESTIC"
private const val EAST_ASIA = "EAST_ASIA"
private const val INTERCONTINENTAL = "INTERCONTINENTAL"

/** D-107 (1) 기본 키(개인 단계 22개) → 단계. 운영 기준값이 더한 키(LM_n)는 목록의 stage로 찾는다. */
private val STAGE_OF_KEY: Map<String, String> =
    listOf("INCHEON", "KAESONG", "DAEJEON", "JEONJU", "YEOSU", "BUSAN", "JEJU").associateWith { DOMESTIC } +
        listOf("BEIJING", "TOKYO", "TAIPEI", "HONG_KONG", "HANOI", "BANGKOK").associateWith { EAST_ASIA } +
        listOf(
            "KUALA_LUMPUR",
            "HONOLULU",
            "SYDNEY",
            "LONDON",
            "PARIS",
            "LOS_ANGELES",
            "NEW_YORK",
            "SANTIAGO",
            "EARTH_LAP"
        ).associateWith { INTERCONTINENTAL }
