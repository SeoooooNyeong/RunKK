package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
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
    val onWriteSchedule: () -> Unit = {},
    val onLobby: (PostList.Item) -> Unit = {},
    val onResult: (PostList.Item) -> Unit = {},
    /** ⋯ 메뉴의 크루 관리(크루장·운영진, Routes.CrewManage). */
    val onManage: () -> Unit = {},
    val onChat: () -> Unit = {},
    /** 주간 공동 목표 화면(와이어 M8, Routes.CrewGoalSet). */
    val onGoalEdit: () -> Unit = {},
    /** 다른 러너 프로필(와이어 F8, Routes.MemberProfile): 크루원 줄. */
    val onMember: (CrewMembers.Member) -> Unit = {},
    val onShareCelebration: (CelebrationCard) -> Unit = {},
    val onLeaveAsk: () -> Unit = {},
    val onLeaveDismiss: () -> Unit = {},
    val onLeaveConfirm: () -> Unit = {},
    /** 크루장 탈퇴 불가 안내 닫기(와이어 E7). */
    val onLeaveBlockedDismiss: () -> Unit = {},
    val onIntroEdit: () -> Unit = {},
    val onIntroDraft: (String) -> Unit = {},
    val onIntroDismiss: () -> Unit = {},
    val onIntroSave: () -> Unit = {},
    /** 크루 정보의 초대 코드 팝업(피그마 F-18b·F-25b) 열기·닫기, 복사한 뒤 안내. */
    val onInviteOpen: () -> Unit = {},
    val onInviteClose: () -> Unit = {},
    val onInviteCopied: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLoadMore: () -> Unit = {}
)
