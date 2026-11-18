package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import com.a207.runkk.app.core.avatar.AvatarStageFrame
import com.a207.runkk.app.core.avatar.RiveAvatarLook
import com.a207.runkk.app.core.avatar.RiveAvatarStill
import com.a207.runkk.app.core.avatar.RunkkRiveAvatar
import com.a207.runkk.app.core.avatar.holdTrackRiveAvatar
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarLoading
import com.a207.runkk.app.core.designsystem.LocalTrackAvatarWarmup
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.TrackAvatarViewport
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarRunSpeed
import kotlin.math.roundToInt

internal const val TRACK_RUN_AVATAR_PLACEHOLDER_TAG = "track-run-avatar-placeholder"

/** 데우기에서 모습을 미리 준비해 붙잡는 함수(시험이 바꾼다). null이면 [holdTrackRiveAvatar]다. */
internal val LocalTrackAvatarHold = staticCompositionLocalOf<(suspend (RiveAvatarLook) -> Unit)?> { null }

/**
 * 트랙 주자 칸의 아바타. 준비 중에는 아무것도 그리지 않고 카드에 알려([LocalTrackAvatarLoading]) 주자를 통째로 숨기게 한다.
 * 실패했을 때만 같은 키의 성별 기본 정지 그림이다. 그림자는 트랙 카드가 한 번만 그린다.
 *
 * 출발 전 데우기([LocalTrackAvatarWarmup]) 안에서는 Rive 칸을 만들지 않고 [holdTrackRiveAvatar]로 그 모습의 Rive
 * 파일과 서기·달리기 텍스처만 미리 준비해 붙잡는다(S15P31A207-335 6회차: 칸 4개를 주 스레드에서 만들던 데우기가 카운트다운을 멈추게
 * 했고, 그 플레이어는 달리기 화면이 이어 쓰지도 못했다). 움직임 줄이기는 정지 그림이라 그대로 구성한다.
 */
@Composable
internal fun TrackRunAvatarFigure(
    state: RunFigureState,
    pose: AvatarSlotPose,
    facingLeft: Boolean,
    onError: () -> Unit
) {
    val look = (state as? RunFigureState.Ready)?.look
    val isFailed = state is RunFigureState.Failed
    val character = (state as? RunFigureState.Failed)?.character ?: LocalAvatarCharacter.current
    val reduceMotion = LocalReduceMotion.current
    if (LocalTrackAvatarWarmup.current && !reduceMotion) {
        if (look != null && !isFailed) {
            val context = LocalContext.current
            val hold = LocalTrackAvatarHold.current ?: { held -> holdTrackRiveAvatar(context, held) }
            LaunchedEffect(look) { hold(look) }
        }
        return
    }
    // 자세가 바뀌어도 같은 그림이라 준비 상태를 지킨다.
    TrackRunAvatarFrame(Triple(look, reduceMotion, isFailed), isFailed, character) { onReady ->
        if (look != null && !isFailed) {
            if (reduceMotion) {
                RiveAvatarStill(look, Modifier.fillMaxSize(), onError = { onError() }, onReady = onReady)
            } else {
                AvatarStageFrame(modifier = Modifier.trackRiveViewport(), showShadow = false) {
                    RunkkRiveAvatar(
                        gender = look.gender,
                        motion = pose.motion(),
                        outfit = look.outfit,
                        bodyShape = look.bodyShape,
                        runningAmount = look.runningAmount,
                        headLayers = look.headLayers,
                        modifier = Modifier.fillMaxSize(),
                        mirrored = facingLeft,
                        runSpeed = LocalSlotAvatarRunSpeed.current,
                        onReady = onReady,
                        onError = { onError() },
                        // 서기·달리기를 처음부터 함께 준비해 자세가 바뀔 때 몸이 비거나 깜빡이지 않게 한다(S15P31A207-335).
                        warmAllMotions = true
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackRunAvatarFrame(
    readyKey: Any?,
    isFailed: Boolean,
    character: AvatarCharacter,
    content: @Composable (onReady: () -> Unit) -> Unit
) {
    var isReady by remember(readyKey) { mutableStateOf(false) }
    val report = LocalTrackAvatarLoading.current
    val isLoading = !isFailed && !isReady
    DisposableEffect(isLoading, report) {
        report(isLoading)
        onDispose { report(false) }
    }
    Box(Modifier.fillMaxSize()) {
        content { isReady = true }
        if (isFailed) {
            RunkkRunningFigure(
                isMoving = false,
                frameMillis = 0,
                character = character,
                modifier = Modifier.fillMaxSize().testTag(TRACK_RUN_AVATAR_PLACEHOLDER_TAG)
            )
        }
    }
}

/** 보이는 몸 높이 칸에서 Rive 무대의 투명 여백만 밖으로 빼고 발끝을 칸 바닥에 맞춘다. */
private fun Modifier.trackRiveViewport(): Modifier = layout { measurable, constraints ->
    val viewport = TrackAvatarViewport.of(constraints.maxHeight.toFloat())
    val slot = viewport.slotSize.roundToInt()
    val placeable = measurable.measure(Constraints.fixed(slot, slot))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((constraints.maxWidth - slot) / 2, viewport.top.roundToInt())
    }
}
