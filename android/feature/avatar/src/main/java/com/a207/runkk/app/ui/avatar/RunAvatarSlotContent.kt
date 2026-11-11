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
import com.a207.runkk.app.core.avatar.RiveAvatarStage
import com.a207.runkk.app.core.avatar.rememberRunFacingLeft
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose
import kotlinx.coroutines.delay

/** 러닝 중 칸이 준비에 실패했을 때 그리는 지금 그림의 시험 표지. */
internal const val RUN_AVATAR_FALLBACK_TAG = "run-avatar-fallback"

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
 */
@Composable
internal fun RunAvatarSlotContent(store: MyAvatarStore) {
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
    Box(Modifier.fillMaxSize()) {
        if (state is MyAvatarState.Failed || isRiveFailed || (look == null && isReadyTimedOut)) {
            Box(Modifier.fillMaxSize().testTag(RUN_AVATAR_FALLBACK_TAG)) {
                RunkkRunningFigure(
                    isMoving = pose == AvatarSlotPose.RUN,
                    frameMillis = runFrameMillis(null),
                    isFacingLeft = rawLeft
                )
            }
        } else {
            RiveAvatarStage(
                look = look?.rive,
                darkTheme = LocalRunkkColors.current.isDark,
                modifier = Modifier.fillMaxSize(),
                motion = pose.motion(),
                mirrored = facingLeft,
                loadingColor = RunkkColors.TextSecondary,
                onError = { isRiveFailed = true }
            )
        }
    }
}
