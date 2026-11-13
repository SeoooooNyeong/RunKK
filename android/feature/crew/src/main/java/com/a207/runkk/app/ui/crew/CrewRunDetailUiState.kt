package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunStatus
import java.time.Instant

/**
 * 크루런 일정 상세(피그마 F-13·F-13b·F-13c·F-11d) 상태. [detail]이 null이면 읽는 중이거나 읽지 못했다([loadError]). [isMenuOpen]은 ⋯
 * 드롭다운, [isDeleteAsked]는 삭제 확인 대화상자다. [now]는 응답 가능 여부와 "2시간 전"을 정하는 시각이다.
 */
data class CrewRunDetailUiState(
    val postId: Long = 0,
    val detail: CrewRunDetail? = null,
    val now: Instant = Instant.EPOCH,
    val isLoading: Boolean = false,
    val loadError: UiText? = null,
    val isAnswering: Boolean = false,
    val isMenuOpen: Boolean = false,
    val isDeleteAsked: Boolean = false,
    val isDeleting: Boolean = false,
    val message: UiText? = null
) {
    /** 참석하기·참석 취소하기를 보인다: 투표 중이거나 대기실이 열렸고 아직 시작 전이다(지난 일정 F-11d는 버튼 없음). */
    val canAnswer: Boolean
        get() = detail?.let {
            (it.status == CrewRunStatus.VOTING || it.status == CrewRunStatus.LOBBY_OPEN) && now.isBefore(it.scheduledAt)
        } ?: false

    /** ⋯ 메뉴에 둘 항목이 있다(수정·삭제는 권한이 있을 때, 남의 글은 신고). */
    val hasMenu: Boolean get() = detail?.let { it.canEdit || it.canDelete || !it.isMine } ?: false
}
