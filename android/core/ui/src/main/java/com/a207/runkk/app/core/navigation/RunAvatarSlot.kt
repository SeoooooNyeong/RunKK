package com.a207.runkk.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 측정 화면의 내 위치 그림([임시:D-성-121]). 아바타 칸(SlotKey.RUN_AVATAR)을 채운 기능이 있으면 [pose]와 진행 방향 원값 [facingLeft]
 * (`rememberHeadingLeft(route)` 값)를 칸 인자로 넣어 그 칸을 그리고, 없으면(미리보기·화면 시험) [fallback](지금 그림)을 그린다.
 * 칸 내용은 부모 크기(88dp 마커 칸)를 채운다. 측정 화면은 아바타 준비를 기다리지 않는다. [subject]가 다른 러너면(트랙 보기의 상대,
 * [임시:D-성-259]) 칸 내용이 그 러너의 모습으로 그린다. 기본은 내 아바타다.
 */
@Composable
fun RunAvatarSlot(
    pose: AvatarSlotPose,
    facingLeft: Boolean,
    runSpeed: Float = 1f,
    subject: RunAvatarSubject = RunAvatarSubject.Mine,
    fallback: @Composable () -> Unit
) {
    val slot = LocalSlots.current[SlotKey.RUN_AVATAR]
    if (slot.isNullOrEmpty()) {
        fallback()
        return
    }
    CompositionLocalProvider(
        LocalSlotAvatarPose provides pose,
        LocalSlotAvatarFacingLeft provides facingLeft,
        LocalSlotAvatarRunSpeed provides runSpeed,
        LocalSlotAvatarRunSubject provides subject
    ) {
        slot.forEach { it() }
    }
}

/** [key] 칸을 채운 기능이 있는가. 칸이 있을 때만 바뀌는 자리(내 마커 88dp 등)를 화면이 고를 때 쓴다. */
@Composable
fun isSlotFilled(key: SlotKey): Boolean = !LocalSlots.current[key].isNullOrEmpty()
