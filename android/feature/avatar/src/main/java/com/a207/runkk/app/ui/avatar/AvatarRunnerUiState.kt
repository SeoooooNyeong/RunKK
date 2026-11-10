package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRunner

/** 내 러너(피그마 H-2) 상태. [avatar]·[runner]가 null이면 아직 읽지 못했다. */
data class AvatarRunnerUiState(
    val isLoading: Boolean = true,
    val avatar: Avatar? = null,
    val runner: AvatarRunner? = null,
    val look: AvatarLook = AvatarLook(),
    /** Rive를 그리지 못할 때 쓰는 앞 단계 얼굴 그림. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /** 띄울 배경 해금 팝업(H-6). */
    val unlock: BackgroundUnlock? = null,
    val error: UiText? = null
)
