package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList

/** 크루 홈 화면의 입력 묶음(상태 없는 CrewHomeScreen을 시험·미리보기에서 그대로 쓴다). */
data class CrewHomeActions(
    val onBack: () -> Unit = {},
    val onTab: (CrewTab) -> Unit = {},
    /** ⋯ 메뉴의 크루 정보·크루원, 그 쪽의 뒤로(HOME). */
    val onPage: (CrewHomePage) -> Unit = {},
    val onPeriod: (CrewRanking.Period) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onDeleteAsk: (PostList.Item) -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    /** 신고 대화상자를 연다(남의 글, FR-214). */
    val onReport: (PostList.Item) -> Unit = {},
    val onAttendance: (PostList.Item, AttendanceAnswer) -> Unit = { _, _ -> },
    /** 참석 취소(참석 칩을 끔, W6): 응답을 거둔다. */
    val onAttendanceWithdraw: (PostList.Item) -> Unit = {},
    val onWriteSchedule: () -> Unit = {},
    val onLobby: (PostList.Item) -> Unit = {},
    /** 크루런 일정 상세 열기(강조 카드 제목·날짜 줄, 피그마 F-11 → F-13). */
    val onRunOpen: (PostList.Item) -> Unit = {},
    /** "지난 크루런 ›"(피그마 F-11b, Routes.CrewPastRuns). */
    val onPastRuns: () -> Unit = {},
    val onResult: (PostList.Item) -> Unit = {},
    /** ⋯ 메뉴(F-17·F-24) 열기·닫기. */
    val onMenuOpen: () -> Unit = {},
    val onMenuClose: () -> Unit = {},
    /** 크루 정보의 「지난 목표 ›」(Routes.CrewPastGoals). 「지난 크루런 ›」은 위 onPastRuns를 같이 쓴다. */
    val onPastGoals: () -> Unit = {},
    /** 가입 신청 승인·거절(F-25, 크루장). */
    val onApprove: (PendingRequests.Request) -> Unit = {},
    val onReject: (PendingRequests.Request) -> Unit = {},
    /** 크루원 정보 팝업(F-22b·F-29b) 열기·닫기. */
    val onMemberOpen: (CrewMembers.Member) -> Unit = {},
    val onMemberClose: () -> Unit = {},
    /** 크루장 넘기기(F-29c)·내보내기(F-29d) 확인 열기·닫기·확정. */
    val onTransferAsk: (CrewMembers.Member) -> Unit = {},
    val onTransferDismiss: () -> Unit = {},
    val onTransferConfirm: () -> Unit = {},
    val onKickAsk: (CrewMembers.Member) -> Unit = {},
    val onKickDismiss: () -> Unit = {},
    val onKickConfirm: () -> Unit = {},
    /** 크루 삭제(F-27) 확인 열기·닫기·확정. */
    val onCrewDeleteAsk: () -> Unit = {},
    val onCrewDeleteDismiss: () -> Unit = {},
    val onCrewDeleteConfirm: () -> Unit = {},
    /** 크루 정보 수정(F-26) 입력·저장. 여는 것은 onPage(EDIT). */
    val onEditForm: (CrewFormState) -> Unit = {},
    val onEditSave: () -> Unit = {},
    /** 정보 수정 로고 연필 덮개(F-26, DV-성-6): 사진 고르기를 연다(Route가 넣는다). */
    val onEditLogoPick: () -> Unit = {},
    /** 내 정보 시트(F-23)의 나이·성별 공개 토글. */
    val onAgePublic: (Boolean) -> Unit = {},
    val onGenderPublic: (Boolean) -> Unit = {},
    /** 주간 공동 목표 화면(와이어 M8, Routes.CrewGoalSet). */
    val onGoalEdit: () -> Unit = {},
    val onShareCelebration: (CelebrationCard) -> Unit = {},
    /** 달성 띠의 「자세히 보기」로 축하 팝업을 열고(피그마 F-08), 팝업의 「확인」으로 닫는다(F-08b). */
    val onCelebrationOpen: () -> Unit = {},
    val onCelebrationClose: () -> Unit = {},
    val onLeaveAsk: () -> Unit = {},
    val onLeaveDismiss: () -> Unit = {},
    val onLeaveConfirm: () -> Unit = {},
    /** 크루장 탈퇴 불가 안내 닫기(와이어 E7). */
    val onLeaveBlockedDismiss: () -> Unit = {},
    val onIntroEdit: () -> Unit = {},
    val onIntroDraft: (String) -> Unit = {},
    val onIntroDismiss: () -> Unit = {},
    val onIntroSave: () -> Unit = {},
    /** 크루 정보의 초대 창([임시:FR-성-37]) 열기·닫기, 코드를 복사한 뒤 안내. */
    val onInviteOpen: () -> Unit = {},
    val onInviteClose: () -> Unit = {},
    val onInviteCopied: () -> Unit = {},
    /** 초대 창의 「카카오톡으로 보내기」(Route가 카카오톡을 연다). */
    val onInviteKakao: () -> Unit = {},
    /** 초대 창의 「초대 링크 복사」: 창이 링크를 클립보드에 넣은 뒤 부른다. */
    val onInviteLinkCopy: () -> Unit = {},
    /** 초대 창의 「다른 앱으로 공유」(Route가 공유 화면을 연다). */
    val onInviteSystemShare: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLoadMore: () -> Unit = {}
)
