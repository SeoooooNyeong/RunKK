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
import com.a207.runkk.app.core.avatar.AvatarGender
import com.a207.runkk.app.core.avatar.GeneratedHeadLayers
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.RiveAvatarStill
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.defaultAvatarOf
import com.a207.runkk.app.core.navigation.AvatarStillSubject
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillFallback
import com.a207.runkk.app.domain.avatar.ItemSlot
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
 * - 다른 러너: 칸에 모습([AvatarStillSubject.look], 랭킹 줄의 모습)이 있으면 그 의상·체형·러닝량에 그 사람의 셀카 머리다. 머리는
 *   셀카 머리 캐시([SelfieHeads])로 받고 받는 동안은 비워 두며, 받지 못했거나(기본 머리로 둔다) [AVATAR_STILL_READY_TIMEOUT_MS] 넘게
 *   걸리면 기본 머리로 먼저 그린다. 모습이 없으면(크루 줄·탈퇴 회원·옛 서버·대결 상대) 표시 성별의 기본 모습(기본 의상·기본 체형·
 *   러닝량 0·기본 머리)이다. 체형은 내 아바타와 같은 규칙([bodyShapeOf])이다. 그리지 못하면 성별 기본 전신 그림이다.
 */
@Composable
internal fun AvatarStillSlotContent(store: MyAvatarStore) {
    val subject = LocalSlotAvatarStill.current ?: return
    if (!LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)) {
        LocalSlotAvatarStillFallback.current?.invoke()
        return
    }
    if (subject.isMine) {
        MyAvatarStill(store, subject.isLightSkin)
    } else {
        RunnerAvatarStill(store.heads, subject)
    }
}

@Composable
private fun MyAvatarStill(store: MyAvatarStore, isLightSkin: Boolean) {
    LaunchedEffect(store) { store.warm() }
    val state by store.state.collectAsStateWithLifecycle()
    val look = (state as? MyAvatarState.Ready)?.look?.rive?.let { if (isLightSkin) it.copy(runningAmount = 0) else it }
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
private fun RunnerAvatarStill(heads: SelfieHeads, subject: AvatarStillSubject) {
    val faceKey = subject.look?.faceKey?.takeIf(::isSelfieHead)
    // 셀카 머리를 받는 동안 null(비움). 셀카 머리가 아니면 기다릴 것이 없다
    var face by remember(faceKey) { mutableStateOf(if (faceKey == null) FaceLook.NONE else null) }
    var isTimedOut by remember(faceKey) { mutableStateOf(false) }
    LaunchedEffect(faceKey) {
        if (faceKey != null) face = heads.look(faceKey)
    }
    LaunchedEffect(faceKey, face == null) {
        if (face == null) {
            delay(AVATAR_STILL_READY_TIMEOUT_MS)
            isTimedOut = true
        }
    }
    val loaded = face
    if (loaded == null && !isTimedOut) return
    // 받지 못했거나 오래 걸리면 기본 머리(headLayers = null)로 그린다
    val look = remember(subject, loaded) { runnerLookOf(subject, loaded?.headLayers) }
    var isFailed by remember(look) { mutableStateOf(false) }
    if (isFailed) {
        StillFallback(if (subject.isFemale == true) AvatarCharacter.FEMALE else AvatarCharacter.MALE)
    } else {
        RiveAvatarStill(look, Modifier.fillMaxSize(), onError = { isFailed = true })
    }
}

/**
 * 다른 러너 전신 정지 그림 값(S15P31A207-300): 모습([AvatarStillSubject.look])이 있으면 그 의상([riveOutfitOf])·체형([bodyShapeOf])·
 * 러닝량, 없으면 기본 의상·기본 체형·러닝량 0이다. 몸은 표시 성별(모르면 남자)이고, 밝은 피부 요청이면 러닝량 0이다.
 */
internal fun runnerLookOf(subject: AvatarStillSubject, headLayers: GeneratedHeadLayers?): RiveAvatarLook {
    val look = subject.look
    val keys = listOfNotNull(
        look?.topKey?.let { ItemSlot.TOP to it },
        look?.bottomKey?.let { ItemSlot.BOTTOM to it },
        look?.shoesKey?.let { ItemSlot.SHOES to it }
    ).toMap()
    return RiveAvatarLook(
        gender = if (subject.isFemale == true) AvatarGender.FEMALE else AvatarGender.MALE,
        outfit = riveOutfitOf(keys),
        bodyShape = bodyShapeOf(look?.bodyVariant),
        runningAmount = if (subject.isLightSkin) 0 else look?.runAmount?.coerceIn(0, MAX_RUN_AMOUNT) ?: 0,
        headLayers = headLayers
    )
}

/** 서버 러닝량 상한(/avatar/runner runAmount 0..100). */
private const val MAX_RUN_AMOUNT = 100

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
