package com.a207.runkk.app.ui.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import com.a207.runkk.app.core.avatar.AvatarMotion
import com.a207.runkk.app.core.avatar.AvatarStageFrame
import com.a207.runkk.app.core.avatar.RiveAvatarStage
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.navigation.AvatarSlotPose

/** 준비 실패·Rive 실패 때 세우는 성별 기본 그림의 시험 표지. */
internal const val AVATAR_STAGE_FALLBACK_TAG = "avatar-stage-fallback"

/**
 * 내 아바타 상태를 무대로 그리는 얇은 감싸개(design-oo 05-30 2절, D-770). 칸 크기는 부르는 쪽이 [modifier]로 고정한다.
 *
 * - Loading·Idle: 배경 자리와 그림자를 먼저 깔고 로딩 표시를 보인다([showLoading], D-738).
 * - Ready: RiveAvatarStage(첫 그림이 보이면 로딩 표시를 거둔다).
 * - Failed이거나 Rive를 그리지 못하면 로딩 표시 없이 같은 바닥선에 성별 기본 그림을 세운다(오류 문구 없음).
 *
 * [backdrop]이 null이면 배경 없는 칸(러닝 중 규칙)이다. [mirrored]는 RUN일 때만 걸린다.
 */
@Composable
internal fun AvatarStage(
    state: MyAvatarState,
    modifier: Modifier = Modifier,
    pose: AvatarSlotPose = AvatarSlotPose.STAND,
    backdrop: Painter? = null,
    mirrored: Boolean = false,
    showLoading: Boolean = true
) {
    val dark = LocalRunkkColors.current.isDark
    val look = (state as? MyAvatarState.Ready)?.look
    var isRiveFailed by remember(look?.rive?.gender, look?.rive?.headLayers) { mutableStateOf(false) }
    if (state is MyAvatarState.Failed || isRiveFailed) {
        AvatarStageFrame(modifier = modifier, backdrop = backdrop, darkTheme = dark) {
            Box(Modifier.fillMaxSize().testTag(AVATAR_STAGE_FALLBACK_TAG)) {
                LegacyStandingAvatar(look?.faceKey, look?.faceLayers.orEmpty())
            }
        }
        return
    }
    RiveAvatarStage(
        look = look?.rive,
        darkTheme = dark,
        modifier = modifier,
        motion = pose.motion(),
        backdrop = backdrop,
        mirrored = mirrored,
        showLoading = showLoading,
        loadingColor = RunkkColors.TextSecondary,
        onError = { isRiveFailed = true }
    )
}

/** 칸 동작 → 렌더러 동작. */
internal fun AvatarSlotPose.motion(): AvatarMotion = when (this) {
    AvatarSlotPose.STAND -> AvatarMotion.STAND
    AvatarSlotPose.RUN -> AvatarMotion.RUN
}
