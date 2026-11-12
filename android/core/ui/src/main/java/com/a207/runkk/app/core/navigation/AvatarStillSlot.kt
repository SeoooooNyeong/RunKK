package com.a207.runkk.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.model.Gender

/**
 * 전신 정지 그림을 그릴 사람(SlotKey.AVATAR_STILL). [isMine]이면 내 아바타 모습(의상·체형·러닝량·셀카 머리) 그대로이고, 다른 러너는
 * 표시 성별 [gender]의 기본 모습(기본 의상·기본 체형·기본 머리)이다. 랭킹 응답에는 다른 러너의 의상·체형·러닝량·얼굴이 없다.
 */
data class AvatarStillSubject(val isMine: Boolean, val gender: Gender? = null)

/**
 * 전신 정지 그림 한 칸(랭킹 TOP3 시상대, 기록런 VS 칸, S15P31A207-300). 아바타 꾸미기(AVATAR_CUSTOMIZE)가 열려 있고 칸을 채운 기능
 * (ui.avatar)이 있으면 [modifier] 칸 안에 [subject]의 서 있는 전신 그림(Rive를 한 번 그려 읽은 정지 그림, 발이 칸 바닥)을 그린다.
 * 아니면(꾸미기 닫힘, 미리보기·화면 시험) [fallback](지금 그림)을 그린다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 */
@Composable
fun AvatarStillSlot(subject: AvatarStillSubject, modifier: Modifier = Modifier, fallback: @Composable () -> Unit) {
    val slot = LocalSlots.current[SlotKey.AVATAR_STILL]
    if (slot.isNullOrEmpty() || !LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)) {
        fallback()
        return
    }
    Box(modifier) {
        CompositionLocalProvider(LocalSlotAvatarStill provides subject) {
            slot.forEach { it() }
        }
    }
}
