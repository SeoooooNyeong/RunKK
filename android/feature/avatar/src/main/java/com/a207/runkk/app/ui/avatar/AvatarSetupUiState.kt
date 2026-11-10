package com.a207.runkk.app.ui.avatar

import com.a207.runkk.app.core.designsystem.UiText

data class AvatarSetupUiState(
    val isLoading: Boolean = true,
    /** 오늘 셀카 아바타를 만들 수 있는가(하루 1회, FR-157). 모르면 참으로 두고 서버가 거부한다. */
    val canCreateSelfie: Boolean = true,
    val selected: DefaultCharacter? = null,
    /** 지금 얼굴 키(미리보기, 고른 캐릭터가 없을 때). */
    val faceKey: String? = null,
    /** 셀카를 줄여 보내거나 기본 캐릭터를 저장하는 중. */
    val isWorking: Boolean = false,
    /** 서버가 셀카 얼굴을 만드는 중(접수 뒤 끝나기 전, S15P31A207-248). 셀카 버튼을 막는다(기본 캐릭터 저장은 된다). */
    val isMakingFace: Boolean = false,
    /** 얼굴 변환 서비스 전송 동의(FACE_TRANSFER). 셀카 카드의 체크다. 모르면 거짓이다. */
    val hasFaceConsent: Boolean = false,
    /** 동의를 저장하는 중. */
    val isSavingConsent: Boolean = false,
    /** 셀카·동의 문구. 셀카 카드 안에 보인다(DV-성-10). */
    val error: UiText? = null,
    /** 마지막 셀카 작업이 실패했다. 셀카 카드에 「다시 시도」를 보인다(나갔다 와도 기기에 남긴 실패로 다시 켠다, DV-성-10). */
    val canRetrySelfie: Boolean = false,
    /** 기본 캐릭터 저장 실패 문구. 저장 단추 위에 보인다. */
    val saveError: UiText? = null
)
