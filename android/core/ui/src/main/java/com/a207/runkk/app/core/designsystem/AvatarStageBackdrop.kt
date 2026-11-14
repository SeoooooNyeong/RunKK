package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

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

/** 무대용 배경 그림을 칸에 맞춰 자른다(배경 고르기 칸·해금 미리보기처럼 무대와 같은 그림을 보여야 하는 곳). 장식 그림이라 보조 기술에 알리지 않는다. */
@Composable
fun AvatarStageBackdrop(backgroundKey: String?, modifier: Modifier = Modifier, stage: String? = null) {
    Image(
        painter = painterResource(avatarStageBackdropRes(backgroundKey, stage, LocalRunkkColors.current.isDark)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
