package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * 아바타 랜드마크 배경(FR-201, FR-202, D-107). 고른 배경 키로 그림을 찾아 아바타·프로필·러닝 카드 뒤에 깐다.
 * 장식 그림이라 보조 기술에는 알리지 않는다(이름은 화면이 글자로 보인다).
 * [backgroundKey]가 null이면 기본 배경, 기본 키는 도시 그림, 모르는 키는 [stage](서버 단계 이름)별 기본 그림, 단계도 모르면 기본 배경이다.
 * 칸에 맞춰 자를 때 [alignment] 기준으로 남긴다(납작한 칸은 바닥이 보이게 아래 기준을 넘긴다). [zoom]이 1보다 크면 아래 가운데를
 * 기준으로 그만큼 키워 그린다(홈: 아바타 발이 사진 트랙 위에 오게, 2026-11-12 PM 요구).
 */
@Composable
fun AvatarBackdrop(
    backgroundKey: String?,
    modifier: Modifier = Modifier,
    stage: String? = null,
    alignment: Alignment = Alignment.Center,
    zoom: Float = 1f
) {
    Image(
        painter = painterResource(
            // 다크 벌에서는 같은 장면의 밤 그림을 쓴다(기본 배경: 사용자 요청 2026-10-30, 도시와 단계 그림: [임시:D-성-308])
            if (LocalRunkkColors.current.isDark) {
                nightBackdropRes(avatarBackdropRes(backgroundKey, stage))
            } else {
                avatarBackdropRes(backgroundKey, stage)
            }
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        modifier = if (zoom == 1f) {
            modifier
        } else {
            modifier.clipToBounds().graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
        }
    )
}

/**
 * 배경 키 → 그림 리소스. Compose 밖(공유 카드 비트맵)에서도 쓴다. 기본 키는 도시마다 제 그림([임시:D-성-186]), 그림이 없는 키(운영
 * 기준값이 더한 LM_n)는 단계 그림이다.
 */
@DrawableRes
fun avatarBackdropRes(backgroundKey: String?, stage: String? = null): Int {
    if (backgroundKey == null) return R.drawable.avatar_backdrop_default
    CITY_BACKDROPS[backgroundKey]?.let { return it }
    // 그림이 없는 키(운영 기준값이 더한 LM_n)는 목록의 단계로 찾는다
    return when (stage) {
        DOMESTIC -> R.drawable.avatar_backdrop_domestic
        EAST_ASIA -> R.drawable.avatar_backdrop_east_asia
        INTERCONTINENTAL -> R.drawable.avatar_backdrop_intercontinental
        else -> R.drawable.avatar_backdrop_default
    }
}

private const val DOMESTIC = "DOMESTIC"
private const val EAST_ASIA = "EAST_ASIA"
private const val INTERCONTINENTAL = "INTERCONTINENTAL"

/** [임시:D-성-186] 기본 키(개인 단계 22개) → 도시 그림(무대 규격 1080×1200, 바닥선 아래 18%). */
private val CITY_BACKDROPS: Map<String, Int> = mapOf(
    "INCHEON" to R.drawable.avatar_backdrop_incheon,
    "CHUNCHEON" to R.drawable.avatar_backdrop_chuncheon,
    "DAEJEON" to R.drawable.avatar_backdrop_daejeon,
    "JEONJU" to R.drawable.avatar_backdrop_jeonju,
    "YEOSU" to R.drawable.avatar_backdrop_yeosu,
    "BUSAN" to R.drawable.avatar_backdrop_busan,
    "JEJU" to R.drawable.avatar_backdrop_jeju,
    "BEIJING" to R.drawable.avatar_backdrop_beijing,
    "TOKYO" to R.drawable.avatar_backdrop_tokyo,
    "TAIPEI" to R.drawable.avatar_backdrop_taipei,
    "HONG_KONG" to R.drawable.avatar_backdrop_hong_kong,
    "HANOI" to R.drawable.avatar_backdrop_hanoi,
    "BANGKOK" to R.drawable.avatar_backdrop_bangkok,
    "KUALA_LUMPUR" to R.drawable.avatar_backdrop_kuala_lumpur,
    "HONOLULU" to R.drawable.avatar_backdrop_honolulu,
    "SYDNEY" to R.drawable.avatar_backdrop_sydney,
    "LONDON" to R.drawable.avatar_backdrop_london,
    "PARIS" to R.drawable.avatar_backdrop_paris,
    "LOS_ANGELES" to R.drawable.avatar_backdrop_los_angeles,
    "NEW_YORK" to R.drawable.avatar_backdrop_new_york,
    "SANTIAGO" to R.drawable.avatar_backdrop_santiago,
    "EARTH_LAP" to R.drawable.avatar_backdrop_earth_lap
)

/**
 * 낮 그림 → 같은 장면의 밤 그림([임시:D-성-308]): 화면 전체 기본 배경, 도시 22장, 단계 3장. 밤 그림이 없는 그림은 그대로 돌려준다.
 */
@DrawableRes
internal fun nightBackdropRes(@DrawableRes day: Int): Int = NIGHT_BACKDROPS[day] ?: day

private val NIGHT_BACKDROPS: Map<Int, Int> = mapOf(
    R.drawable.avatar_backdrop_default to R.drawable.avatar_backdrop_default_night,
    R.drawable.avatar_backdrop_incheon to R.drawable.avatar_backdrop_incheon_night,
    R.drawable.avatar_backdrop_chuncheon to R.drawable.avatar_backdrop_chuncheon_night,
    R.drawable.avatar_backdrop_daejeon to R.drawable.avatar_backdrop_daejeon_night,
    R.drawable.avatar_backdrop_jeonju to R.drawable.avatar_backdrop_jeonju_night,
    R.drawable.avatar_backdrop_yeosu to R.drawable.avatar_backdrop_yeosu_night,
    R.drawable.avatar_backdrop_busan to R.drawable.avatar_backdrop_busan_night,
    R.drawable.avatar_backdrop_jeju to R.drawable.avatar_backdrop_jeju_night,
    R.drawable.avatar_backdrop_beijing to R.drawable.avatar_backdrop_beijing_night,
    R.drawable.avatar_backdrop_tokyo to R.drawable.avatar_backdrop_tokyo_night,
    R.drawable.avatar_backdrop_taipei to R.drawable.avatar_backdrop_taipei_night,
    R.drawable.avatar_backdrop_hong_kong to R.drawable.avatar_backdrop_hong_kong_night,
    R.drawable.avatar_backdrop_hanoi to R.drawable.avatar_backdrop_hanoi_night,
    R.drawable.avatar_backdrop_bangkok to R.drawable.avatar_backdrop_bangkok_night,
    R.drawable.avatar_backdrop_kuala_lumpur to R.drawable.avatar_backdrop_kuala_lumpur_night,
    R.drawable.avatar_backdrop_honolulu to R.drawable.avatar_backdrop_honolulu_night,
    R.drawable.avatar_backdrop_sydney to R.drawable.avatar_backdrop_sydney_night,
    R.drawable.avatar_backdrop_london to R.drawable.avatar_backdrop_london_night,
    R.drawable.avatar_backdrop_paris to R.drawable.avatar_backdrop_paris_night,
    R.drawable.avatar_backdrop_los_angeles to R.drawable.avatar_backdrop_los_angeles_night,
    R.drawable.avatar_backdrop_new_york to R.drawable.avatar_backdrop_new_york_night,
    R.drawable.avatar_backdrop_santiago to R.drawable.avatar_backdrop_santiago_night,
    R.drawable.avatar_backdrop_earth_lap to R.drawable.avatar_backdrop_earth_lap_night,
    R.drawable.avatar_backdrop_domestic to R.drawable.avatar_backdrop_domestic_night,
    R.drawable.avatar_backdrop_east_asia to R.drawable.avatar_backdrop_east_asia_night,
    R.drawable.avatar_backdrop_intercontinental to R.drawable.avatar_backdrop_intercontinental_night
)
