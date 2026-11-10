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
    /** 크루 정보(GET /crews/{id}): 이름·인원·내 역할·태그·초대 코드. 읽기 전이면 null. */
    val detail: CrewDetail? = null,
    val tab: CrewTab = CrewTab.RACE,
    /** ⋯ 메뉴로 연 쪽(피그마 F-17·F-24). HOME이면 탭 화면이다. */
    val page: CrewHomePage = CrewHomePage.HOME,
    val isLoading: Boolean = false,
    val race: RaceStatus? = null,
    val period: CrewRanking.Period = CrewRanking.Period.WEEKLY,
    val ranking: CrewRanking? = null,
    /** 크루런 탭의 크루런 일정(피그마 F-11). */
    val crewRuns: PostList? = null,
    /** 크루런 탭의 지금 서버 시각 추정(1초마다 갱신, 강조 카드 남은 시간·이번주 묶음). 모르면 null. */
    val scheduleNow: Instant? = null,
    /** 축하 카드를 공유하는 중(FR-141). */
    val isSharing: Boolean = false,
    /** 응답 시트를 연 크루런 모집글(피그마 F-11: 강조 카드 제목·날짜 줄을 누름). 닫혀 있으면 null. */
    val openedRunId: Long? = null,
    /** 크루런 모집글별 대기실 단추 상태(postId → LobbyAvailability). 서버 시각 추정으로 1초마다 다시 판단한다. */
    val lobbyEntries: Map<Long, LobbyAvailability> = emptyMap(),
    val members: CrewMembers? = null,
    /** 크루런 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val isConfirmingLeave: Boolean = false,
    /** 크루원이 남은 크루장이 탈퇴를 누름: 크루장을 넘기라는 안내(와이어 E7, FR-126). */
    val isLeaveBlocked: Boolean = false,
    /** 지울지 확인 중인 내 글(FR-130·FR-134). */
    val confirmDelete: PostList.Item? = null,
    val isEditingIntro: Boolean = false,
    val introDraft: String = "",
    /** 초대 코드 팝업(피그마 F-18b·F-25b)을 연 상태. */
    val isInviteOpen: Boolean = false,
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
    /** 띄운 공동 목표 축하 팝업(피그마 F-08b). 그 주 첫 진입 때 한 번 자동으로, 달성 띠의 「자세히 보기」로 다시 연다. 닫혀 있으면 null. */
    val celebration: RaceStatus? = null
) {
    /** 이번 주 공동 목표를 달성해 목표 카드 위에 달성 띠를 보인다(피그마 F-08). */
    val isCelebrationBannerShown: Boolean get() = race?.achievedAt != null && race.celebration != null

    val crewName: String get() = detail?.name.orEmpty()

    /** 응답 시트를 연 크루런 모집글(목록을 다시 읽어도 새 값). 목록에서 사라졌으면 null. */
    val openedRun: PostList.Item? get() = openedRunId?.let { id -> crewRuns?.posts?.firstOrNull { it.postId == id } }

    /** 내 크루 역할(크루 밖·읽기 전이면 null). 버튼 노출을 정한다(3.0.3, FR-128). */
    val myRole: CrewRole? get() = detail?.myRole

    val isLeader: Boolean get() = myRole == CrewRole.LEADER

    /** 크루장·운영진: ⋯ 메뉴의 크루 관리(가입 결정 등)·공동 목표(3.0.3). */
    val canManage: Boolean get() = myRole == CrewRole.LEADER || myRole == CrewRole.MANAGER

    /** 주간 공동 목표 설정(FR-138): 크루장·운영진. */
    val canSetGoal: Boolean get() = canManage

    /** 크루 인원(크루 정보, 없으면 크루원 목록 수). */
    val headcount: Int? get() = detail?.headcount ?: members?.members?.size

    companion object {
        const val INTRO_MAX = 40
    }
}
