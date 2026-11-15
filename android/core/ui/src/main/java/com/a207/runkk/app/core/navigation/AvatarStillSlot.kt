package com.a207.runkk.app.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier

/**
 * 전신 정지 그림을 그릴 사람(SlotKey.AVATAR_STILL). [isMine]이면 내 아바타 모습(의상·체형·러닝량·셀카 머리) 그대로이고, 다른 러너는
 * [look]이 있으면 그 모습(랭킹 응답 줄의 모습, S15P31A207-300), 없으면(크루 줄·탈퇴 회원·옛 서버·대결 상대) 표시 성별의 기본 모습
 * (기본 의상·기본 체형·기본 머리)이다.
 */
data class AvatarStillSubject(
    val isMine: Boolean,
    /** 다른 러너의 표시 성별이 여자면 참, 남자면 거짓, 모르면 null(남자 기본 모습). core.navigation은 core.model을 import하지 않는다(5.1.2). */
    val isFemale: Boolean? = null,
    /** 참이면 러닝량과 상관없이 가장 밝은 피부(러닝량 0)로 그린다(대결 고르기 카드, 2026-11-12 PM 요구). */
    val isLightSkin: Boolean = false,
    /** 다른 러너의 모습. null이면 성별 기본 모습이다. 나([isMine])는 쓰지 않는다(내 아바타 저장소 모습). */
    val look: Look? = null,
    /** 아이템 ID로 받은 다른 러너의 모습. [look]보다 먼저 쓴다. */
    val slotLook: SlotRunnerLook? = null
) {
    companion object {
        val MINE = AvatarStillSubject(isMine = true)
    }

    /**
     * 다른 러너의 모습(원시값, 5.1.2: 이 패키지는 core.model·domain을 import하지 않는다).
     *
     * @property faceKey 얼굴 키(GET /avatar faceKey와 같은 값). 셀카 머리("head:…")면 그 머리로 그리고, 없으면 기본 머리
     * @property topKey 상의 상점 그림 키(Shop.Item.imageKey와 같은 값). 안 입었으면 null(기본 의상)
     * @property bottomKey 하의 그림 키
     * @property shoesKey 신발 그림 키
     * @property bodyVariant 체형 0..2(/avatar/runner bodyVariant와 같은 규칙)
     * @property runAmount 러닝량 0..100(피부 그을림)
     */
    data class Look(
        val faceKey: String?,
        val topKey: String?,
        val bottomKey: String?,
        val shoesKey: String?,
        val bodyVariant: Int,
        val runAmount: Int
    )
}

/**
 * 전신 정지 그림 한 칸(랭킹 TOP3 시상대, 기록런 VS 칸, S15P31A207-300). 아바타 꾸미기(AVATAR_CUSTOMIZE)가 열려 있고 칸을 채운 기능
 * (ui.avatar)이 있으면 [modifier] 칸 안에 [subject]의 서 있는 전신 그림(Rive를 한 번 그려 읽은 정지 그림, 발이 칸 바닥)을 그린다.
 * 아니면(꾸미기 닫힘, 미리보기·화면 시험) [fallback](지금 그림)을 그린다. 장식이므로 읽기 정보는 부르는 쪽이 붙인다.
 * [onReady]는 칸에 그림이 보이면 한 번 불린다([임시:D-성-292]): 정지 그림을 읽은 뒤, 또는 대체 그림([fallback], 성별 기본 그림)을 그릴 때다.
 * 그림을 읽는 동안에는 칸이 비어 있으므로, 칸을 찍어 내보내는 화면(기록 카드 공유)은 이 신호까지 기다린다.
 */
@Composable
fun AvatarStillSlot(
    subject: AvatarStillSubject,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    fallback: @Composable () -> Unit
) {
    val currentOnReady by rememberUpdatedState(onReady)
    val slot = LocalSlots.current[SlotKey.AVATAR_STILL]
    if (slot.isNullOrEmpty()) {
        fallback()
        LaunchedEffect(Unit) { currentOnReady() }
        return
    }
    // 칸 내용에는 바뀌지 않는 함수 하나를 넘긴다(부르는 쪽 람다가 다시 만들어져도 칸 내용을 다시 그리지 않게).
    val signal = remember { { currentOnReady() } }
    Box(modifier) {
        // 꾸미기 공개 여부는 칸을 채운 기능이 본다. 닫혀 있으면 그 기능이 [fallback]을 그린다(5.1.2: 이 패키지는 designsystem을 import하지 않는다).
        CompositionLocalProvider(
            LocalSlotAvatarStill provides subject,
            LocalSlotAvatarStillFallback provides fallback,
            LocalSlotAvatarStillOnReady provides signal
        ) {
            slot.forEach { it() }
        }
    }
}
