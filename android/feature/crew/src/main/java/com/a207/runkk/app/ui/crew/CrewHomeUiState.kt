package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PendingRequests
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
    /** 당겨서 새로고침 중(설계 05-32). 지금 값은 그대로 보인다. */
    val isRefreshing: Boolean = false,
    val race: RaceStatus? = null,
    /** 레이스 탭에 새로 반영된 거리 강조(2초). 없으면 null. */
    val raceHighlight: RaceHighlight? = null,
    val period: CrewRanking.Period = CrewRanking.Period.WEEKLY,
    val ranking: CrewRanking? = null,
    /** 크루런 탭의 크루런 일정(피그마 F-11). */
    val crewRuns: PostList? = null,
    /** 크루런 탭의 지금 서버 시각 추정(1초마다 갱신, 강조 카드 남은 시간·이번주 묶음). 모르면 null. */
    val scheduleNow: Instant? = null,
    /** 축하 카드를 공유하는 중(FR-141). */
    val isSharing: Boolean = false,
    /** 크루런 모집글별 대기실 단추 상태(postId → LobbyAvailability). 서버 시각 추정으로 1초마다 다시 판단한다. */
    val lobbyEntries: Map<Long, LobbyAvailability> = emptyMap(),
    /** 진행 중 강조 카드의 크루런 결과(postId → 결과): 출발 시각과 내 거리. 아직 못 읽었으면 없다. */
    val heroResults: Map<Long, CrewRunResult> = emptyMap(),
    val members: CrewMembers? = null,
    /** 크루원 셀카 얼굴 부위 그림(faceKey → 층, [임시:D-성-236]). 없는 키는 기본 얼굴로 그린다. */
    val memberFaces: Map<String, List<FaceLayer>> = emptyMap(),
    /** 크루런 다음 쪽을 읽는 중. */
    val isLoadingMore: Boolean = false,
    val isConfirmingLeave: Boolean = false,
    /** 크루원이 남은 크루장이 탈퇴를 누름: 크루장을 넘기라는 안내(와이어 E7, FR-126). */
    val isLeaveBlocked: Boolean = false,
    /** 지울지 확인 중인 내 글(FR-130·FR-134). */
    val confirmDelete: PostList.Item? = null,
    val isEditingIntro: Boolean = false,
    val introDraft: String = "",
    /** 초대 창([임시:FR-성-37], CrewInviteSheet)을 연 상태. 코드와 만료일은 [detail]에서 읽는다. */
    val isInviteOpen: Boolean = false,
    /** ⋯ 메뉴(피그마 F-17·F-24: 딤 + 카드)를 연 상태. */
    val isMenuOpen: Boolean = false,
    /** 가입 신청(피그마 F-25 크루 정보 안 "가입 신청 N건"). 크루장만 읽는다. */
    val requests: PendingRequests? = null,
    /** 크루원 정보 팝업(피그마 F-22b·F-29b) 대상. 목록 값을 그대로 쓴다(따로 읽지 않는다). */
    val openedMember: CrewMembers.Member? = null,
    /** 크루장 넘기기 확인(F-29c) 대상. */
    val confirmTransfer: CrewMembers.Member? = null,
    /** 내보내기 확인(F-29d) 대상. */
    val confirmKick: CrewMembers.Member? = null,
    /** 크루 삭제 확인(F-27). */
    val isConfirmingCrewDelete: Boolean = false,
    /** 내 정보 시트(F-23)의 나이 공개 토글. 시트를 열 때 내 줄의 공개 설정으로 채운다. */
    val isAgePublic: Boolean = false,
    /** 내 정보 시트(F-23)의 성별 공개 토글. */
    val isGenderPublic: Boolean = false,
    /** 크루 정보 수정(F-26) 입력. 수정 쪽을 열 때 크루 정보로 채운다. */
    val editForm: CrewFormState = CrewFormState(),
    /** 정보 수정에서 새로 고른 로고(JPEG, 미리보기, DV-6). 올린 주소는 [editForm]의 logoUrl이다. 고르지 않았으면 null. */
    val editLogo: ByteArray? = null,
    /** 정보 수정에서 고른 로고를 올리는 중(저장을 막는다). */
    val isUploadingLogo: Boolean = false,
    val isBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null,
    /** 띄운 공동 목표 축하 팝업(피그마 F-08b). 그 주 첫 진입 때 한 번 자동으로, 달성 띠의 「자세히 보기」로 다시 연다. 닫혀 있으면 null. */
    val celebration: RaceStatus? = null
) {
    /** 이번 주 공동 목표를 달성해 목표 카드 위에 달성 띠를 보인다(피그마 F-08). */
    val isCelebrationBannerShown: Boolean get() = race?.achievedAt != null && race.celebration != null

    val crewName: String get() = detail?.name.orEmpty()

    /** 내 크루 역할(크루 밖·읽기 전이면 null). 버튼 노출을 정한다(3.0.3, FR-128). */
    val myRole: CrewRole? get() = detail?.myRole

    val isLeader: Boolean get() = myRole == CrewRole.LEADER

    /**
     * 크루장만: 가입 신청 결정·크루 정보 수정·크루 삭제·크루장 넘기기·내보내기(피그마 F-25·F-29b). 운영진 화면은 지웠다(W7 결정 C5,
     * 서버 권한은 그대로).
     */
    val canManage: Boolean get() = myRole == CrewRole.LEADER

    /** 주간 공동 목표 설정(FR-138): 크루장·운영진. */
    val canSetGoal: Boolean get() = myRole == CrewRole.LEADER || myRole == CrewRole.MANAGER

    /** 크루원 목록의 내 줄(내 정보 카드, F-22·F-29). */
    val me: CrewMembers.Member? get() = members?.members?.firstOrNull { it.isMe }

    /** 크루 인원(크루 정보, 없으면 크루원 목록 수). */
    val headcount: Int? get() = detail?.headcount ?: members?.members?.size

    companion object {
        const val INTRO_MAX = 40
    }
}
