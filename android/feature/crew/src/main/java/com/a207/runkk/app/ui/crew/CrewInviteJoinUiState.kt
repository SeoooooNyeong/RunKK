package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewPreview

/**
 * 초대 링크로 연 크루 가입 확인 화면의 상태([임시:FR-성-38], [임시:D-성-244]). 네 결과는 [Found](정상: 미리보기와 「가입하기」),
 * [Expired](만료·재발급으로 맞지 않는 코드), [AlreadyMember](이미 그 크루의 구성원), [Limited](시도 횟수 초과)이고, 로그인 전은 앱이
 * 로그인 뒤에 이 화면을 연다. [Failed]는 연결 실패처럼 다시 시도할 수 있는 실패다.
 */
sealed interface CrewInviteJoinUiState {
    data object Loading : CrewInviteJoinUiState

    /** 정상: 그 코드의 크루. [isJoining]은 가입 요청 중, [message]는 가입이 거부된 사유(다른 크루 소속, 정원 초과 등). */
    data class Found(val preview: CrewPreview, val isJoining: Boolean = false, val message: UiText? = null) :
        CrewInviteJoinUiState

    /** 만료된 초대: 안내 뒤 크루 찾기로 간다. */
    data object Expired : CrewInviteJoinUiState

    /** 이미 그 크루의 구성원: 그 크루 홈으로 간다. */
    data class AlreadyMember(val crewId: Long) : CrewInviteJoinUiState

    /** 틀린 코드를 너무 많이 넣었다(10분에 5번, 기존 문구). */
    data object Limited : CrewInviteJoinUiState

    /** 크루를 읽지 못했다(다시 시도). */
    data class Failed(val message: UiText) : CrewInviteJoinUiState
}
