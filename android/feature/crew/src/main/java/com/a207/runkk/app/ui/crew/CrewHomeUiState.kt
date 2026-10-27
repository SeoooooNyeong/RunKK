package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.Instant

data class CrewHomeUiState(
    val crewId: Long = 0,
    /** 크루 정보(GET /crews/{id}): 이름·인원·내 역할. 읽기 전이면 null. */
    val detail: CrewDetail? = null,
    val tab: CrewTab = CrewTab.RACE,
    val isLoading: Boolean = false,
    val race: RaceStatus? = null,
    val period: CrewRanking.Period = CrewRanking.Period.WEEKLY,
    val ranking: CrewRanking? = null,
    val crewRuns: PostList? = null,
    /** 일정 탭에서 보는 종류: 크루런(CREW_RUN) 또는 대면 모임(MEETUP, FR-132). */
    val scheduleKind: PostList.Kind = PostList.Kind.CREW_RUN,
    /** 대면 모임 일정(FR-132·FR-145). 처음 고를 때 읽는다. */
    val meetups: PostList? = null,
    /** 일정 탭을 읽은 서버 시각 추정. 지난 대면 모임을 가른다(와이어 I4). 모르면 null(모두 다가오는 일정). */
    val scheduleNow: Instant? = null,
    /** 축하 카드를 공유하는 중(FR-141). */
    val isSharing: Boolean = false,
    /** 크루런 모집글별 대기실 단추 상태(postId → LobbyAvailability). 서버 시각 추정으로 1초마다 다시 판단한다. */
    val lobbyEntries: Map<Long, LobbyAvailability> = emptyMap(),
    /** 레이스 탭 위 고정 공지(최대 3, FR-205, 와이어 E2). 공지 첫 쪽에서 고른다. */
    val pinnedNotices: List<PostList.Item> = emptyList(),
    val members: CrewMembers? = null,
    /** 게시글 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val isConfirmingLeave: Boolean = false,
    /** 크루원이 남은 크루장이 탈퇴를 누름: 크루장을 넘기라는 안내(와이어 E7, FR-126). */
    val isLeaveBlocked: Boolean = false,
    /** 지울지 확인 중인 내 글(FR-130·FR-134). */
    val confirmDelete: PostList.Item? = null,
    val isEditingIntro: Boolean = false,
    val introDraft: String = "",
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val crewName: String get() = detail?.name.orEmpty()

    /** 내 크루 역할(크루 밖·읽기 전이면 null). 버튼 노출을 정한다(3.0.3, FR-128). */
    val myRole: CrewRole? get() = detail?.myRole

    val isLeader: Boolean get() = myRole == CrewRole.LEADER

    /** 크루장·운영진: 크루 관리(가입 결정 등)·공지 작성·고정·공동 목표(3.0.3). */
    val canManage: Boolean get() = myRole == CrewRole.LEADER || myRole == CrewRole.MANAGER

    /** 주간 공동 목표 설정(FR-138): 크루장·운영진. */
    val canSetGoal: Boolean get() = canManage

    /** 크루 인원(크루 정보, 없으면 크루원 목록 수). */
    val headcount: Int? get() = detail?.headcount ?: members?.members?.size

    /** 지금 일정 종류의 목록. */
    val schedules: PostList? get() = if (scheduleKind == PostList.Kind.MEETUP) meetups else crewRuns

    companion object {
        const val INTRO_MAX = 40
        const val PINNED_MAX = 3
    }
}
