package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.PendingRequests

data class CrewManageActions(
    val onBack: () -> Unit = {},
    val onApprove: (PendingRequests.Request) -> Unit = {},
    val onReject: (PendingRequests.Request) -> Unit = {},
    val onReissue: () -> Unit = {},
    val onForm: (CrewFormState) -> Unit = {},
    val onSaveInfo: () -> Unit = {},
    val onAsk: (ManageAction) -> Unit = {},
    val onDismissManager: (CrewMembers.Member) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMenu: (CrewMembers.Member) -> Unit = {},
    val onMenuDismiss: () -> Unit = {},
    val onInfoOpen: () -> Unit = {},
    val onInfoClose: () -> Unit = {},
    /** 주간 공동 목표 화면(와이어 M8, Routes.CrewGoalSet). */
    val onGoalEdit: () -> Unit = {},
    /** 다른 러너 프로필(와이어 F8, Routes.MemberProfile): 크루원 메뉴의 "프로필 보기". */
    val onProfile: (CrewMembers.Member) -> Unit = {},
    /** 초대 코드를 클립보드에 복사한 뒤 안내. */
    val onCopied: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
