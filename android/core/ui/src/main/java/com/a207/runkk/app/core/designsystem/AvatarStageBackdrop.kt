package com.a207.runkk.app.core.designsystem

import androidx.annotation.DrawableRes

/**
 * 아바타 무대(내 러너·꾸미기·상점, design-oo 05-30 4.3 규격 1080×1200, 바닥선 아래 216px) 배경 그림.
 * 화면 전체에 까는 기본 배경([avatarBackdropRes], 세로 그림)과 기본 그림만 다르고, 단계 그림은 같다.
 * 홈·결과·기록 카드처럼 화면 전체에 까는 곳은 [AvatarBackdrop]을 그대로 쓴다([임시:D-성-172]).
 */
@DrawableRes
fun avatarStageBackdropRes(backgroundKey: String?, stage: String? = null, isDark: Boolean): Int {
    val res = avatarBackdropRes(backgroundKey, stage)
    if (res != R.drawable.avatar_backdrop_default) return res
    return if (isDark) R.drawable.avatar_stage_default_night else R.drawable.avatar_stage_default
}
