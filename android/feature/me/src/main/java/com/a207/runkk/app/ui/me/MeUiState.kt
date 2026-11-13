package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.account.Profile

data class MeUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    /** 프로필 칸 안의 셀카 얼굴 부위 그림. 비어 있으면 기본 얼굴·자리 그림. */
    val faceLayers: List<FaceLayer> = emptyList(),
    /** 얼굴 층을 받는 중이다. 기본 머리를 먼저 보였다가 내 얼굴로 바꾸지 않고 중립 자리를 둔다([임시:D-성-190]). */
    val isFaceLoading: Boolean = false,
    val error: UiText? = null,
    /**
     * 본인 티어 배지(작게, FR-282·D-494): 배치 진행으로 본인 표시(배치 중이면 임시 티어). 진행을 못 읽으면 프로필의 공개 티어.
     */
    val tierDisplay: TierDisplay? = null
)
