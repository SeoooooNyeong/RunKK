package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * 아바타 무대(내 러너·꾸미기·상점·러닝 결과·기록 카드, design-oo 05-30 4.3 규격 1080×1200, 바닥선 아래 216px) 배경 그림.
 * 화면 전체에 까는 기본 배경([avatarBackdropRes], 세로 그림)과 기본 그림만 다르고, 단계 그림은 같다.
 * [isDark]면 같은 이름의 밤 그림(`_night`)이다: 기본 무대뿐 아니라 도시 22장과 단계 3장도 밤 그림이 있다([임시:D-성-308]).
 */
@DrawableRes
fun avatarStageBackdropRes(backgroundKey: String?, stage: String? = null, isDark: Boolean): Int {
    val res = avatarBackdropRes(backgroundKey, stage)
    if (res == R.drawable.avatar_backdrop_default) {
        return if (isDark) R.drawable.avatar_stage_default_night else R.drawable.avatar_stage_default
    }
    return if (isDark) nightBackdropRes(res) else res
}

/**
 * 무대용 배경 그림을 칸에 맞춰 자른다(배경 고르기 칸·해금 미리보기처럼 무대와 같은 그림을 보여야 하는 곳). 장식 그림이라 보조 기술에 알리지 않는다.
 * [alignment]는 칸이 그림보다 낮을 때 남길 쪽이다: 트랙 보기의 하늘 칸은 [TrackHorizonAlignment]로 바닥선 바로 위 풍경을 남긴다.
 */
@Composable
fun AvatarStageBackdrop(
    backgroundKey: String?,
    modifier: Modifier = Modifier,
    stage: String? = null,
    alignment: Alignment = Alignment.Center
) {
    Image(
        painter = painterResource(avatarStageBackdropRes(backgroundKey, stage, LocalRunkkColors.current.isDark)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = alignment,
        modifier = modifier
    )
}

/** 무대 그림(1080×1200)에서 트랙이 시작하는 바닥선의 높이 비율: 바닥선 아래가 216px이다. */
const val STAGE_FLOOR_FRACTION = 984f / 1200f

/**
 * 칸([boxHeight]) 안에서 무대 그림([imageHeight], 칸 너비에 맞춰 늘린 높이)을 놓을 위쪽 좌표.
 * 그림의 바닥선을 칸 바닥에 맞춰 명소와 지평선이 레인 바로 위에 보이게 한다. 그림이 칸을 벗어나 빈 곳이 생기지 않게 가둔다.
 */
fun trackHorizonTop(imageHeight: Int, boxHeight: Int): Int {
    val wanted = boxHeight - (imageHeight * STAGE_FLOOR_FRACTION).roundToInt()
    return wanted.coerceIn(minOf(boxHeight - imageHeight, 0), 0)
}

/** 트랙 보기 하늘 칸용 맞춤: 가로는 가운데, 세로는 [trackHorizonTop]. */
object TrackHorizonAlignment : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
        IntOffset((space.width - size.width) / 2, trackHorizonTop(size.height, space.height))
}
