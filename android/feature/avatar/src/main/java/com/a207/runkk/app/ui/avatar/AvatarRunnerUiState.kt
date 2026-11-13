package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.avatar.Avatar
import com.a207.runkk.app.domain.avatar.AvatarRunner
import com.a207.runkk.app.domain.avatar.SelfieProgress

/**
 * 내 러너(피그마 제안 B안 62323:1642, design-oo 05-30 [임시:D-성-131]) 상태. [avatar]·[runner]가 null이면 아직 읽지 못했다.
 * 체형·피부톤 이름과 근거 문장은 두지 않는다([임시:D-성-128]). 숫자 타일은 [stats](이번 주 횟수·거리, 연속 러닝 일수)다.
 *
 * @property level 본인 레벨(카드 왼쪽 위 칩). 모르면 칩을 숨긴다
 * @property unlockedBackgrounds 연 배경 수와 [totalBackgrounds] 전체 수(목록 「배경 3/12」). 모르면 null
 * @property selfie 셀카 얼굴 작업 진행(목록 「얼굴」 줄의 진행 표시, 피그마 AV-7 4)
 */
data class AvatarRunnerUiState(
    val isLoading: Boolean = true,
    val avatar: Avatar? = null,
    val runner: AvatarRunner? = null,
    val look: AvatarLook = AvatarLook(),
    /** Rive를 그리지 못할 때 쓰는 앞 단계 얼굴 그림. */
    val faceLayers: List<FaceLayer> = emptyList(),
    val level: Int? = null,
    val stats: RunnerStats? = null,
    val unlockedBackgrounds: Int? = null,
    val totalBackgrounds: Int? = null,
    val selfie: SelfieProgress = SelfieProgress.None,
    /** 띄울 배경 해금 팝업(H-6). */
    val unlock: BackgroundUnlock? = null,
    val error: UiText? = null
)
