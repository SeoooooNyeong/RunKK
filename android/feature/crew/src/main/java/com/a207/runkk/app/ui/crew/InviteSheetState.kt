package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.CrewPreview

/**
 * 초대 코드 바텀시트의 단계(피그마 F-03·F-03b·F-03c, contract-W4 4절).
 * [ENTERING] 코드 입력 중, [NOT_FOUND] 맞는 크루 없음(칸 오류 표시), [FOUND] 크루 미리보기를 보이고 "가입하기"를 기다림.
 */
enum class InviteStep { ENTERING, NOT_FOUND, FOUND }

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
