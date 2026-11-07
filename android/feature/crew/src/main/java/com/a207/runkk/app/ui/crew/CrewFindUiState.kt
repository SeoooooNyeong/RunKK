package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.MyCrew

data class CrewFindUiState(
    /** 크루 탭에 들어와 내 크루(GET /crews/me)를 확인하는 중. */
    val isCheckingMine: Boolean = true,
    /** 소속 크루. 있으면 크루 탭이 내 크루 홈을 보인다. */
    val mine: CrewDetail? = null,
    /** 내 크루를 확인하지 못했다(다시 시도). 소속을 모르는 채 가입을 권하지 않도록 찾기 화면을 보이지 않는다. */
    val mineError: UiText? = null,
    /** 대기 중인 본인 가입 신청(FR-143 취소). */
    val pending: List<MyCrew.PendingJoin> = emptyList(),
    /** 취소를 확인 중인 신청. */
    val confirmCancel: MyCrew.PendingJoin? = null,
    val isLoading: Boolean = true,
    val keyword: String = "",
    val tags: Set<String> = emptySet(),
    val crews: List<CrewList.Item> = emptyList(),
    /** 다음 쪽 커서. null이면 끝까지 읽었다. */
    val nextCursor: Long? = null,
    val isLoadingMore: Boolean = false,
    /** 상세(바텀 시트)로 연 크루. */
    val detail: CrewList.Item? = null,
    /** 상세로 연 크루의 정보(GET /crews/{id}): 지난 주 달린 거리(피그마 F-02). 읽기 전이면 null. */
    val detailInfo: CrewDetail? = null,
    val isJoining: Boolean = false,
    val isInviteOpen: Boolean = false,
    val inviteCode: String = "",
    val message: UiText? = null,
    val error: UiText? = null
)
