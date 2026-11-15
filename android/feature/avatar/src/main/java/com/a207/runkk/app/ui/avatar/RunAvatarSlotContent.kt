package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.RiveAvatarStage
import com.a207.runkk.app.core.avatar.RiveAvatarStill
import com.a207.runkk.app.core.avatar.rememberRunFacingLeft
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarViewport
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSpeed
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSubject
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import kotlinx.coroutines.delay

/** 러닝 중 칸이 준비에 실패했을 때 그리는 지금 그림의 시험 표지. */
internal const val RUN_AVATAR_FALLBACK_TAG = "run-avatar-fallback"

/** 움직임 줄이기일 때 그리는 멈춘 그림의 시험 표지([임시:D-성-262]). */
internal const val RUN_AVATAR_STILL_TAG = "run-avatar-still"

/** 러닝 중 칸이 준비를 기다리는 최대 시간. 넘으면 지금 그림을 보인다(리뷰 U-11, [임시:D-성-181]). */
internal const val RUN_AVATAR_READY_TIMEOUT_MS = 8_000L

/**
 * 러닝 중 아바타 칸 내용(SlotKey.RUN_AVATAR, design-oo 05-30 7절): 88dp 마커 칸을 채워 배경 없이 그림자와 Rive 아바타를 그린다.
 *
 * - 동작은 [LocalSlotAvatarPose]다(측정 중 RUN, 그 밖 STAND).
 * - 진행 방향 원값([LocalSlotAvatarFacingLeft])은 같은 방향이 2초 이어질 때만 바꾸고(rememberRunFacingLeft, [임시:D-성-133]) RUN일 때만
 *   좌우로 뒤집는다. 그림자·기준점(칸 아래 가운데)은 그대로다.
 * - 준비 전·첫 그림 전에는 칸 가운데 로딩 표시(AvatarLoadingPlaceholder, RiveAvatarStage가 그린다)를 보인다. 지금 그림
 *   (RunkkRunningFigure)은 준비 실패(MyAvatarState.Failed, Rive onError)일 때만 보여 기존 그림이 먼저 보였다가 바뀌지 않게 한다
 *   ([임시:D-성-181]). 준비가 [RUN_AVATAR_READY_TIMEOUT_MS] 넘게 끝나지 않아도 지금 그림을 보인다. 달리기 동작은
 *   MyAvatarStore.warm이 미리 합성해 두어 대개 바로 보인다.
 * - 측정 화면은 준비를 기다리지 않는다. 칸에 들어올 때 앞 준비가 실패했으면 한 번 다시 시도한다.
 * - 대상([LocalSlotAvatarRunSubject])이 다른 러너면 그 러너의 모습으로 그린다([임시:D-성-259]). 기본은 내 아바타다.
 * - 움직임 줄이기면 모습이 준비된 뒤 멈춘 그림 한 장을 그린다([임시:D-성-262]).
 */
@Composable
internal fun RunAvatarSlotContent(store: MyAvatarStore, resolver: RunnerLookResolver) {
    val subject = LocalSlotAvatarRunSubject.current
    if (subject.isMine) MyRunAvatar(store) else RunnerRunAvatar(subject, resolver)
}

@Composable
private fun MyRunAvatar(store: MyAvatarStore) {
    LaunchedEffect(store) {
        store.warm()
        store.retryIfFailed()
    }
    val state by store.state.collectAsStateWithLifecycle()
    val pose = LocalSlotAvatarPose.current
    val rawLeft = LocalSlotAvatarFacingLeft.current
    val facingLeft = rememberRunFacingLeft(rawLeft)
    val look = (state as? MyAvatarState.Ready)?.look
    // 새 Ready(착용·체형이 바뀐 모습)가 오면 Rive 실패 표시를 풀고 다시 그려 본다.
    var isRiveFailed by remember(look) { mutableStateOf(false) }
    // 준비가 끝나지 않는 경우(성별을 끝내 모름 등)에도 마커가 끝없는 로딩 표시로 남지 않게 지금 그림으로 바꾼다.
    var isReadyTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(look == null) {
        if (look == null) {
            delay(RUN_AVATAR_READY_TIMEOUT_MS)
            isReadyTimedOut = true
        }
    }
    val figure = runFigureState(
        look?.rive,
        state is MyAvatarState.Failed || isRiveFailed || (look == null && isReadyTimedOut),
        LocalAvatarCharacter.current
    )
    RunAvatarFigure(
        state = figure,
        pose = pose,
        facing = if (figure is RunFigureState.Failed) rawLeft else facingLeft,
        onError = { isRiveFailed = true }
    )
}

/**
 * 다른 러너(트랙 보기의 상대, 크루런의 가장 가까운 크루원)를 그린다([임시:D-성-259], [임시:D-성-261]). 받은 모습이 있으면
 * [RunnerLookResolver]로 렌더러 값을 만들고(만드는 동안 로딩 표시), 없으면 표시 성별의 기본 모습이다. 만들기가
 * [RUN_AVATAR_READY_TIMEOUT_MS]를 넘거나 Rive가 실패하면 그 성별의 지금 달리기 그림(RunkkRunningFigure)이다.
 */
@Composable
private fun RunnerRunAvatar(subject: RunAvatarSubject, resolver: RunnerLookResolver) {
    val pose = LocalSlotAvatarPose.current
    val rawLeft = LocalSlotAvatarFacingLeft.current
    val facingLeft = rememberRunFacingLeft(rawLeft)
    val received = subject.look
    var look by remember(subject) { mutableStateOf(if (received == null) defaultRiveLook(subject.isFemale) else null) }
    var isRiveFailed by remember(look) { mutableStateOf(false) }
    var isReadyTimedOut by remember(subject) { mutableStateOf(false) }
    LaunchedEffect(subject) {
        if (received != null) look = resolver.resolve(received)
    }
    LaunchedEffect(subject, look == null) {
        if (look == null) {
            delay(RUN_AVATAR_READY_TIMEOUT_MS)
            isReadyTimedOut = true
        }
    }
    val figure = runFigureState(
        look,
        isRiveFailed || (look == null && isReadyTimedOut),
        if (subject.isFemale == true) AvatarCharacter.FEMALE else AvatarCharacter.MALE
    )
    RunAvatarFigure(
        state = figure,
        pose = pose,
        facing = if (figure is RunFigureState.Failed) rawLeft else facingLeft,
        onError = { isRiveFailed = true }
    )
}

/**
 * 달리는 아바타 한 명. 실패면 해당 성별의 지금 달리기 그림, 움직임 줄이기([LocalReduceMotion])면 멈춘 그림([RiveAvatarStill],
 * [임시:D-성-262]: 렌더러는 줄이기를 보지 않아 칸 내용이 가른다), 아니면 Rive 무대다(준비 전에는 로딩 표시).
 */
@Composable
private fun RunAvatarFigure(state: RunFigureState, pose: AvatarSlotPose, facing: Boolean, onError: () -> Unit) {
    if (LocalTrackAvatarViewport.current) {
        TrackRunAvatarFigure(
            look = (state as? RunFigureState.Ready)?.look,
            isFailed = state is RunFigureState.Failed,
            character = (state as? RunFigureState.Failed)?.character ?: LocalAvatarCharacter.current,
            pose = pose,
            facingLeft = facing,
            onError = onError
        )
        return
    }
    Box(Modifier.fillMaxSize()) {
        when {
            state is RunFigureState.Failed -> Box(Modifier.fillMaxSize().testTag(RUN_AVATAR_FALLBACK_TAG)) {
                RunkkRunningFigure(
                    isMoving = pose == AvatarSlotPose.RUN,
                    frameMillis = runFrameMillis(null),
                    character = state.character,
                    isFacingLeft = facing
                )
            }

            state is RunFigureState.Ready && LocalReduceMotion.current -> RiveAvatarStill(
                state.look,
                Modifier.fillMaxSize().testTag(RUN_AVATAR_STILL_TAG),
                onError = { onError() }
            )

            else -> RiveAvatarStage(
                look = (state as? RunFigureState.Ready)?.look,
                darkTheme = LocalRunkkColors.current.isDark,
                modifier = Modifier.fillMaxSize(),
                motion = pose.motion(),
                mirrored = facing,
                runSpeed = LocalSlotAvatarRunSpeed.current,
                loadingColor = RunkkColors.TextSecondary,
                onError = { onError() }
            )
        }
    }
}

private sealed interface RunFigureState {
    data object Loading : RunFigureState
    data class Ready(val look: RiveAvatarLook) : RunFigureState
    data class Failed(val character: AvatarCharacter) : RunFigureState
}

private fun runFigureState(look: RiveAvatarLook?, isFailed: Boolean, character: AvatarCharacter): RunFigureState =
    when {
        isFailed -> RunFigureState.Failed(character)
        look != null -> RunFigureState.Ready(look)
        else -> RunFigureState.Loading
    }
