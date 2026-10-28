package com.a207.runkk.app.ui.crew

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog

/**
 * 운영진 지정 확인(FR-204, 와이어프레임 M9): "[닉네임]님을 운영진으로 지정할까요?", 지금 운영진 수("운영진 1/2명"), 취소·지정.
 * 운영진·크루장 권한 목록 설명은 화면 문구 규칙(설명 줄 없음)에 따라 두지 않는다.
 */
@Composable
internal fun CrewStaffConfirmDialog(action: ManageAction.Appoint, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.crew_manage_appoint_title, action.member.displayName),
        message = stringResource(R.string.crew_manage_manager_count, action.managerCount, MANAGER_MAX),
        confirmLabel = stringResource(R.string.crew_manage_appoint_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/** 운영진 상한(FR-204 제안 값 2명, MANAGER_LIMIT). */
private const val MANAGER_MAX = 2
