package com.a207.runkk.app.ui.me

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.domain.account.Profile

/** 다른 러너 프로필(F8). 공개 수준에 따라 서버가 걸러 보낸 항목만 있다(D-52). */
data class OtherProfileUiState(
    val memberId: Long? = null,
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val faceLayers: List<FaceLayer> = emptyList(),
    /** 얼굴 층을 받는 중이다(기본 머리 대신 중립 자리를 둔다, [임시:D-성-190]). */
    val isFaceLoading: Boolean = false,
    val error: UiText? = null
) {
    /** 다른 러너의 티어 배지: 공개 티어만(확정 전이면 「배치 중」, 임시 티어 없음, D-494). */
    val tierDisplay: TierDisplay?
        get() = TierDisplay.ofPublic(profile?.tier)
}
