package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.MyCrew
import java.time.Duration

/**
 * 크루 검색 화면 상태(피그마 F-01d·F-01e, Routes.CREW_SEARCH).
 *
 * @property keyword 입력한 크루명 검색어
 * @property results 찾은 공개 크루(최근 생성순)
 * @property resultCount 같은 검색어의 공개 크루 전체 수("검색 결과 [n]개", 서버 totalCount)
 * @property hasSearched 한 번이라도 찾았는가(찾기 전에는 결과 수·빈 화면을 보이지 않는다)
 */
data class CrewSearchUiState(
    val keyword: String = "",
    /** 마지막으로 찾은 검색어(다음 쪽은 입력 중인 값이 아니라 이것으로 잇는다). */
    val searchedKeyword: String? = null,
    val results: List<CrewList.Item> = emptyList(),
    val resultCount: Long = 0,
    val hasSearched: Boolean = false,
    val isLoading: Boolean = false,
    val nextCursor: Long? = null,
    val isLoadingMore: Boolean = false,
    val error: UiText? = null,
    /** 상세로 연 크루와 그 정보(크루 찾기와 같은 상세 화면). */
    val detail: CrewList.Item? = null,
    val detailInfo: CrewDetail? = null,
    /** 대기 중인 본인 가입 신청(상세에서 취소). */
    val pending: List<MyCrew.PendingJoin> = emptyList(),
    val confirmCancel: MyCrew.PendingJoin? = null,
    val isJoining: Boolean = false,
    /** 24시간 가입 제한까지 남은 시간(F-02c). */
    val joinRemaining: Duration? = null,
    val message: UiText? = null
)
