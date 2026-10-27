package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant

/**
 * 대면 모임 상세(FR-145·FR-207, 와이어프레임 M7) 상태. [post]는 대면 모임 목록에서 찾은 글(모임 정보 [PostList.Item.meetup])이고,
 * 못 찾았으면 null이다. [now]는 "2시간 전" 같은 상대 시각의 기준이다.
 */
data class CrewMeetupDetailUiState(
    val crewId: Long = 0,
    val postId: Long = 0,
    val post: PostList.Item? = null,
    val now: Instant = Instant.EPOCH,
    val isLoading: Boolean = true,
    val isAnswering: Boolean = false,
    val isConfirmingDelete: Boolean = false,
    /** 지웠다: 화면을 닫는다. */
    val isDeleted: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
)
