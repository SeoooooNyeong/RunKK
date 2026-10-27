package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.PollResult
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant

/**
 * 투표 화면(FR-206·FR-207, 와이어프레임 M3) 상태. [result]는 선택지별 수와 응답자(익명 아님), [post]는 작성자·시각·삭제 가능 여부를
 * 위해 투표 목록에서 찾은 글이다(못 찾으면 null). [now]는 상대 시각과 "마감 임박"의 기준이다.
 */
data class CrewPollDetailUiState(
    val crewId: Long = 0,
    val postId: Long = 0,
    val post: PostList.Item? = null,
    val result: PollResult? = null,
    val now: Instant = Instant.EPOCH,
    val isLoading: Boolean = true,
    val isResponding: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    /** 지웠다: 화면을 닫는다. */
    val isDeleted: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    /** 응답자 수(선택지별 수의 합). */
    val respondentCount: Int get() = result?.options?.sumOf { it.count } ?: 0

    /** 마감 전이고 마감까지 하루가 안 남았다. */
    val isClosingSoon: Boolean
        get() = result?.let {
            !it.isClosed && it.closesAt.isAfter(now) &&
                !it.closesAt.isAfter(now.plusSeconds(DAY_SECONDS))
        } ==
            true

    private companion object {
        const val DAY_SECONDS = 86_400L
    }
}
