package com.a207.runkk.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * 전신 정지 그림을 그릴 사람(SlotKey.AVATAR_STILL). [isMine]이면 내 아바타 모습(의상·체형·러닝량·셀카 머리) 그대로이고, 다른 러너는
 * 표시 성별 [gender]의 기본 모습(기본 의상·기본 체형·기본 머리)이다. 랭킹 응답에는 다른 러너의 의상·체형·러닝량·얼굴이 없다.
 */
data class AvatarStillSubject(
    val isMine: Boolean,
    /** 다른 러너의 표시 성별이 여자면 참, 남자면 거짓, 모르면 null(남자 기본 모습). core.navigation은 core.model을 import하지 않는다(5.1.2). */
    val isFemale: Boolean? = null,
    /** 참이면 러닝량과 상관없이 가장 밝은 피부(러닝량 0)로 그린다(대결 고르기 카드, 2026-11-12 PM 요구). */
    val isLightSkin: Boolean = false
)

/**
 * 전신 정지 그림 한 칸(랭킹 TOP3 시상대, 기록런 VS 칸, S15P31A207-300). 아바타 꾸미기(AVATAR_CUSTOMIZE)가 열려 있고 칸을 채운 기능
 * (ui.avatar)이 있으면 [modifier] 칸 안에 [subject]의 서 있는 전신 그림(Rive를 한 번 그려 읽은 정지 그림, 발이 칸 바닥)을 그린다.
 * 아니면(꾸미기 닫힘, 미리보기·화면 시험) [fallback](지금 그림)을 그린다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun AvatarStillSlot(subject: AvatarStillSubject, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val slot = LocalSlots.current[SlotKey.AVATAR_STILL]
    if (slot.isNullOrEmpty()) {
        fallback()
        return
    }
    Box(modifier) {
        // 꾸미기 공개 여부는 칸을 채운 기능이 본다. 닫혀 있으면 그 기능이 [fallback]을 그린다(5.1.2: 이 패키지는 designsystem을 import하지 않는다).
        CompositionLocalProvider(
            LocalSlotAvatarStill provides subject,
            LocalSlotAvatarStillFallback provides fallback
        ) {
            slot.forEach { it() }
        }
    }
}
