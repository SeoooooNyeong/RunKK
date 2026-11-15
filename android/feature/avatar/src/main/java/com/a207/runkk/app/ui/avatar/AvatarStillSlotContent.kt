package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.avatar.RiveAvatarStill
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.defaultAvatarOf
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStill
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillFallback
import com.a207.runkk.app.core.navigation.LocalSlotAvatarStillOnReady
import com.a207.runkk.app.core.navigation.SlotRunnerLook
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
 * - 다른 러너: 랭킹 응답에 의상·체형·러닝량·얼굴이 없어 표시 성별의 기본 모습(기본 의상·기본 체형·러닝량 0·기본 머리)이다. 받은 모습
 *   (AvatarStillSubject.look, 기록런 고르기의 그날의 나)이 있으면 그 모습이다([임시:D-성-272]).
 */
@Composable
internal fun AvatarStillSlotContent(store: MyAvatarStore, resolver: RunnerLookResolver) {
    val subject = LocalSlotAvatarStill.current ?: return
    var hasShown by remember { mutableStateOf(false) }
    val currentOnReady by rememberUpdatedState(LocalSlotAvatarStillOnReady.current)
    val onReady = remember {
        {
            hasShown = true
            currentOnReady()
        }
    }
    if (!LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)) {
        LocalSlotAvatarStillFallback.current?.invoke()
        // 부르는 쪽 그림은 바로 보인다([임시:D-성-292])
        LaunchedEffect(Unit) { onReady() }
        return
    }
    CompositionLocalProvider(LocalSlotAvatarStillOnReady provides onReady) {
        if (subject.isMine) {
            MyAvatarStill(store, subject.isLightSkin, hasShown)
        } else {
            RunnerAvatarStill(
                subject.isFemale?.let {
                    if (it) Gender.FEMALE else Gender.MALE
                },
                subject.look,
                resolver,
                hasShown
            )
        }
    }
}

@Composable
private fun MyAvatarStill(store: MyAvatarStore, isLightSkin: Boolean, hasShown: Boolean) {
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
        state is MyAvatarState.Failed || isFailed || (look == null && (isTimedOut || hasShown)) ->
            StillFallback(LocalAvatarCharacter.current)

        look != null -> RiveAvatarStill(
            look,
            Modifier.fillMaxSize(),
            onError = { isFailed = true },
            onReady = LocalSlotAvatarStillOnReady.current,
            pending = { if (hasShown) StillFallback(LocalAvatarCharacter.current) }
        )

        else -> Unit
    }
}

/**
 * 다른 러너의 전신 정지 그림. 받은 모습 [received]가 있으면 [RunnerLookResolver]로 만든 모습(의상·체형·러닝량·얼굴, [임시:D-성-272])이고,
 * 없으면 표시 성별의 기본 모습이다. 만드는 동안에는 칸을 비워 두고, [AVATAR_STILL_READY_TIMEOUT_MS]를 넘거나 그림을 읽지 못하면 성별
 * 기본 전신 그림이다.
 */
@Composable
private fun RunnerAvatarStill(
    gender: Gender?,
    received: SlotRunnerLook?,
    resolver: RunnerLookResolver,
    hasShown: Boolean
) {
    val isFemale = received?.isFemale ?: (gender == Gender.FEMALE)
    var look by remember(isFemale, received) {
        mutableStateOf(
            if (received ==
                null
            ) {
                defaultRiveLook(isFemale)
            } else {
                null
            }
        )
    }
    var isFailed by remember(look) { mutableStateOf(false) }
    var isTimedOut by remember(received) { mutableStateOf(false) }
    LaunchedEffect(received) {
        if (received != null) look = resolver.resolve(received)
    }
    LaunchedEffect(received, look == null) {
        if (look == null) {
            delay(AVATAR_STILL_READY_TIMEOUT_MS)
            isTimedOut = true
        }
    }
    val shown = look
    when {
        isFailed || (shown == null && (isTimedOut || hasShown)) ->
            StillFallback(if (isFemale) AvatarCharacter.FEMALE else AvatarCharacter.MALE)

        shown != null -> RiveAvatarStill(
            shown,
            Modifier.fillMaxSize(),
            onError = { isFailed = true },
            onReady = LocalSlotAvatarStillOnReady.current,
            pending = { if (hasShown) StillFallback(if (isFemale) AvatarCharacter.FEMALE else AvatarCharacter.MALE) }
        )

        else -> Unit
    }
}

/** 성별 기본 전신 그림(서기). 발을 칸 바닥에 붙인다. 바로 보이므로 준비 신호를 보낸다([임시:D-성-292]). */
@Composable
private fun StillFallback(character: AvatarCharacter) {
    val onReady = LocalSlotAvatarStillOnReady.current
    LaunchedEffect(Unit) { onReady() }
    Image(
        painterResource(defaultAvatarOf(AvatarExpression.STAND, character)),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        alignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize().testTag(AVATAR_STILL_FALLBACK_TAG)
    )
}
