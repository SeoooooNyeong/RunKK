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
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalSlotAvatarFacingLeft
import com.a207.runkk.app.core.navigation.LocalSlotAvatarPose

/** 러닝 중 칸이 아직 Rive를 보이지 못할 때 그리는 지금 그림의 시험 표지. */
internal const val RUN_AVATAR_FALLBACK_TAG = "run-avatar-fallback"

/**
 * 러닝 중 아바타 칸 내용(SlotKey.RUN_AVATAR, design-oo 05-30 7절): 88dp 마커 칸을 채워 배경 없이 그림자와 Rive 아바타를 그린다.
 *
 * - 동작은 [LocalSlotAvatarPose]다(측정 중 RUN, 그 밖 STAND).
 * - 진행 방향 원값([LocalSlotAvatarFacingLeft])은 같은 방향이 2초 이어질 때만 바꾸고(rememberRunFacingLeft, [임시:D-성-133]) RUN일 때만
 *   좌우로 뒤집는다. 그림자·기준점(칸 아래 가운데)은 그대로다.
 * - 로딩 표시는 없다. 준비 전·실패·첫 그림 전에는 지금 그림(RunkkRunningFigure)을 보여 내 위치 마커가 비지 않게 한다.
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
    var isFailed by remember(look?.rive?.gender, look?.rive?.headLayers) { mutableStateOf(false) }
    var isShown by remember(look?.rive?.gender, look?.rive?.headLayers, pose) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        if (look != null && !isFailed) {
            RiveAvatarStage(
                look = look.rive,
                darkTheme = LocalRunkkColors.current.isDark,
                modifier = Modifier.fillMaxSize(),
                motion = pose.motion(),
                mirrored = facingLeft,
                showLoading = false,
                onReady = { isShown = true },
                onError = { isFailed = true }
            )
        }
        if (look == null || isFailed || !isShown) {
            Box(Modifier.fillMaxSize().testTag(RUN_AVATAR_FALLBACK_TAG)) {
                RunkkRunningFigure(
                    isMoving = pose == AvatarSlotPose.RUN,
                    frameMillis = runFrameMillis(null),
                    isFacingLeft = rawLeft
                )
            }
        }
    }
}
