package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.CrewPreview

/**
 * 초대 코드 바텀시트 상태(크루 찾기 [CrewFindUiState.invite], 시트를 닫으면 null). "확인"은 코드로 크루를 먼저 조회하고
 * (CrewRepository.previewByInviteCode) "가입하기"에서 가입한다(applyWithInviteCode). 코드를 고치면 [InviteStep.ENTERING]으로 돌아간다.
 *
 * @property code 입력한 코드(영문 대문자·숫자, 6자까지)
 * @property preview 찾은 크루([InviteStep.FOUND]일 때만)
 * @property isBusy 조회나 가입을 기다리는 중
 */
data class InviteSheetState(
    val code: String = "",
    val step: InviteStep = InviteStep.ENTERING,
    val preview: CrewPreview? = null,
    val isBusy: Boolean = false
) {
    /** 여섯 자를 다 넣어 "확인"을 누를 수 있는가. */
    val isCodeComplete: Boolean get() = code.length == INVITE_CODE_LENGTH
}

internal const val INVITE_CODE_LENGTH = 6
