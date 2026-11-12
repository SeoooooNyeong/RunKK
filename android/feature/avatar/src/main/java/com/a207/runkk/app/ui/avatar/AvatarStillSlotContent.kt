package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.avatar.AvatarBodyShape
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.AvatarOutfit
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.RiveAvatarStill
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.defaultAvatarOf
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import kotlinx.coroutines.delay

/** 전신 정지 그림을 만들지 못했을 때 세우는 성별 기본 전신 그림의 시험 표지. */
internal const val AVATAR_STILL_FALLBACK_TAG = "avatar-still-fallback"

/** 내 모습 준비를 기다리는 최대 시간. 넘으면 성별 기본 전신 그림을 보인다(러닝 중 칸과 같은 값). */
internal const val AVATAR_STILL_READY_TIMEOUT_MS = RUN_AVATAR_READY_TIMEOUT_MS

/**
 * 전신 정지 그림 칸 내용(SlotKey.AVATAR_STILL, 랭킹 TOP3 시상대·기록런 VS 칸, S15P31A207-300). 부르는 쪽이 정한 칸을 채워 서 있는
 * 전신(머리·몸·의상)을 정지 그림 한 장으로 그린다([RiveAvatarStill]: Rive 서기를 한 번 그려 읽고 칸 크기로 줄여 캐시한다).
 *
 * - 나: 내 아바타 저장소의 모습(의상·체형·러닝량·셀카 머리) 그대로다. 준비 중이면 비워 두고, 준비 실패·그림 읽기 실패·준비가
 *   [AVATAR_STILL_READY_TIMEOUT_MS] 넘게 끝나지 않으면 성별 기본 전신 그림이다.
 * - 다른 러너: 랭킹 응답에 의상·체형·러닝량·얼굴이 없어 표시 성별의 기본 모습(기본 의상·기본 체형·러닝량 0·기본 머리)이다.
 */
@Composable
internal fun AvatarStillSlotContent(store: MyAvatarStore) {
    val subject = LocalSlotAvatarStill.current ?: return
    if (subject.isMine) {
        MyAvatarStill(store)
    } else {
        RunnerAvatarStill(subject.gender)
    }
}

@Composable
private fun MyAvatarStill(store: MyAvatarStore) {
    LaunchedEffect(store) { store.warm() }
    val state by store.state.collectAsStateWithLifecycle()
    val look = (state as? MyAvatarState.Ready)?.look?.rive
    var isFailed by remember(look) { mutableStateOf(false) }
    var isTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(look == null) {
        if (look == null) {
            delay(AVATAR_STILL_READY_TIMEOUT_MS)
            isTimedOut = true
        }
    }
    when {
        state is MyAvatarState.Failed || isFailed || (look == null && isTimedOut) ->
            StillFallback(LocalAvatarCharacter.current)

        look != null -> RiveAvatarStill(look, Modifier.fillMaxSize(), onError = { isFailed = true })

        else -> Unit
    }
}

@Composable
private fun RunnerAvatarStill(gender: Gender?) {
    val avatarGender = if (gender == Gender.FEMALE) AvatarGender.FEMALE else AvatarGender.MALE
    val look = remember(avatarGender) {
        RiveAvatarLook(avatarGender, AvatarOutfit(), AvatarBodyShape.BASE, runningAmount = 0, headLayers = null)
    }
    var isFailed by remember(look) { mutableStateOf(false) }
    if (isFailed) {
        StillFallback(AvatarCharacter.of(gender))
    } else {
        RiveAvatarStill(look, Modifier.fillMaxSize(), onError = { isFailed = true })
    }
}

/** 성별 기본 전신 그림(서기). 발을 칸 바닥에 붙인다. */
@Composable
private fun StillFallback(character: AvatarCharacter) {
    Image(
        painterResource(defaultAvatarOf(AvatarExpression.STAND, character)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize().testTag(AVATAR_STILL_FALLBACK_TAG)
    )
}
