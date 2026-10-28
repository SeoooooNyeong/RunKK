package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PendingRequests

data class CrewManageUiState(
    val crewId: Long = 0,
    val crewName: String = "",
    /** 크루 정보(GET /crews/{id}). 정보 수정 칸과 초대 코드를 채운다. */
    val detail: CrewDetail? = null,
    val isLoading: Boolean = true,
    val requests: PendingRequests? = null,
    val members: CrewMembers? = null,
    val inviteCode: String? = null,
    val form: CrewFormState = CrewFormState(),
    val confirming: ManageAction? = null,
    /** ⋯로 메뉴를 연 크루원(와이어 I10 바텀 시트). */
    val menuMember: CrewMembers.Member? = null,
    /** 크루 정보 수정 화면을 연 상태(와이어 I9). */
    val isEditingInfo: Boolean = false,
    /** 이번 주 공동 목표(레이스 응답). 없으면 null. */
    val weeklyGoal: Distance? = null,
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 크루장만 보는 기능: 정보 수정·초대 코드·양도·강제 퇴장·운영진 지정·삭제(3.0.3, FR-204). 운영진은 가입 결정만 본다. */
    val isLeader: Boolean get() = detail?.myRole == CrewRole.LEADER

    /** 공동 목표 설정(FR-138): 크루장, 운영진. */
    val canSetGoal: Boolean get() = detail?.myRole == CrewRole.LEADER || detail?.myRole == CrewRole.MANAGER
}
