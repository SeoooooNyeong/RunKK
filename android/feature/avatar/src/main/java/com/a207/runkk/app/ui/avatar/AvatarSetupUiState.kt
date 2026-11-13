package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer

/**
 * 얼굴 만들기(피그마 AV-7 1안) 상태. 단계는 [phase]이고, 보낸 사진([photo], 이미 줄인 JPEG)은 사진 확인·만드는 중·실패 화면에 보인다
 * (design-oo 05-30 [임시:D-성-119]).
 */
data class AvatarSetupUiState(
    val isLoading: Boolean = true,
    val phase: SetupPhase = SetupPhase.CHOOSE,
    /** 오늘 셀카 아바타를 만들 수 있는가(하루 1회, FR-157). 모르면 참으로 두고 서버가 거부한다. */
    val canCreateSelfie: Boolean = true,
    /** 「기본 얼굴」 칸을 골랐는가([임시:D-성-187]). 지금 얼굴이 기본 캐릭터면 고른 채 연다. */
    val isDefaultSelected: Boolean = false,
    /** 지금 얼굴 키(미리보기, 「기본 얼굴」을 고르지 않았을 때). */
    val faceKey: String? = null,
    /** 고르거나 보낸 사진(JPEG). 없으면 null. */
    val photo: ByteArray? = null,
    /** 셀카를 줄이거나 기본 캐릭터를 저장하는 중. */
    val isWorking: Boolean = false,
    /** 얼굴 변환 서비스 전송 동의(FACE_TRANSFER). 사진 확인 화면의 체크다. 모르면 거짓이다. */
    val hasFaceConsent: Boolean = false,
    /** 동의를 저장하는 중. */
    val isSavingConsent: Boolean = false,
    /** 셀카·동의 문구(고르기·사진 확인 화면). 실패 화면의 사유는 [failure]다. */
    val error: UiText? = null,
    /** 실패 화면의 사유 문구. */
    val failure: UiText? = null,
    /** 기본 얼굴 저장 실패 문구. 저장 단추 위에 보인다. */
    val saveError: UiText? = null,
    /** 새 얼굴 화면 무대의 모습(바뀐 얼굴 + 지금 착용). 읽기 전이면 null. */
    val look: AvatarLook? = null,
    /** 지금 얼굴이 셀카면 그 부위 그림(지금 얼굴 카드·새 얼굴 화면). 기본 얼굴이면 비어 있다. */
    val faceLayers: List<FaceLayer> = emptyList(),
    val backgroundKey: String? = null
) {
    /** 서버가 얼굴을 만드는 중이다(보냄·만드는 중). 셀카 버튼을 막는다(기본 얼굴 저장은 된다). */
    val isMakingFace: Boolean get() = phase == SetupPhase.SENDING || phase == SetupPhase.WORKING
}
