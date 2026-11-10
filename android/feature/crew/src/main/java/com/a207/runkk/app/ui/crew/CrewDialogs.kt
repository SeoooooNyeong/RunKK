package com.a207.runkk.app.ui.crew

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkDialogButtons
import com.a207.runkk.app.core.designsystem.RunkkDialogMessage
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole
import kotlinx.coroutines.launch

/**
 * 오른쪽 위 닫기 ✕가 있는 카드 대화상자(피그마 F-18b 초대 코드·F-22b 크루원 정보): 흰 바탕, 모서리 [corner], 양옆 [side], 안쪽
 * [contentPadding]. ✕(36 터치, 20 아이콘)는 카드 기준 오른쪽 위다. 딤은 45%(RunkkDialog와 같다). 바깥 누름·뒤로가기는 닫는다.
 */
@Composable
private fun CrewCloseableDialog(
    paneTitle: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    side: Dp,
    corner: Dp,
    top: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(DIALOG_DIM) }
        val shape = RoundedCornerShape(corner)
        Box(
            Modifier
                .padding(horizontal = side)
                .widthIn(max = 390.dp - side * 2)
                .fillMaxWidth()
                .clip(shape)
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.Border, shape)
                .semantics { this.paneTitle = paneTitle }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = top, bottom = 20.dp),
                content = content
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    RunkkIcons.Close,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private const val DIALOG_DIM = 0.45f

/**
 * 초대 코드 팝업(피그마 F-18b·F-25b, 크루장·크루원 같음): 제목 "초대 코드", surfaceHigh 코드 칸(높이 56, 모서리 14, 24 Bold 자간 4),
 * 라임 "복사" 하나(폭 가득), 오른쪽 위 ✕. 재발급·사용 기한은 없다(서버가 만료된 코드를 읽을 때 새로 발급한다, contract-W7).
 */
@Composable
internal fun InviteCodeDialog(code: String, actions: CrewHomeActions) {
    val title = stringResource(R.string.crew_info_invite)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val spoken = stringResource(R.string.crew_manage_invite_code_description, code.toList().joinToString(" "))
    CrewCloseableDialog(
        paneTitle = title,
        closeLabel = stringResource(R.string.crew_info_invite_close_description),
        onDismiss = actions.onInviteClose,
        side = 28.dp,
        corner = 28.dp,
        top = 24.dp
    ) {
        Text(
            title,
            style = RunkkTextStyles.DialogTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RunkkColors.SurfaceHigh)
                .semantics { contentDescription = spoken },
            contentAlignment = Alignment.Center
        ) {
            Text(
                code,
                style = RunkkTextStyles.text(24, FontWeight.Bold).copy(letterSpacing = 4.sp),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        RunkkButton(
            stringResource(R.string.crew_manage_invite_copy),
            onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, code)))
                    actions.onInviteCopied()
                }
            },
            size = RunkkButtonSize.Medium,
            modifier = Modifier.padding(top = 20.dp).fillMaxWidth()
        )
    }
}

/**
 * 크루원 정보 팝업(피그마 F-22b·F-29b): 폭 310 카드(양옆 40, 모서리 24, 위 28), 가운데 80 원 사진, 닉네임 18 Bold, 크루장이면 배지,
 * 정보 다섯 줄(티어·레벨·나이·성별·가입일, 비공개는 "비공개"), 한 줄 소개 칸. 크루장이 다른 크루원을 열면 "크루장 넘기기"(외곽선)·
 * "내보내기"(보라)를 더 본다.
 */
@Composable
internal fun CrewMemberDialog(member: CrewMembers.Member, canManage: Boolean, actions: CrewHomeActions) {
    val name = member.displayName
    CrewCloseableDialog(
        paneTitle = name,
        closeLabel = stringResource(R.string.crew_member_close),
        onDismiss = actions.onMemberClose,
        side = 40.dp,
        corner = 24.dp,
        top = 28.dp
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MemberAvatar(80.dp, isBordered = true)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    name,
                    style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 22),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                if (member.role == CrewRole.LEADER) LeaderBadge()
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoPair(stringResource(R.string.crew_member_tier), orPrivate(tierName(member.tier)))
                InfoPair(stringResource(R.string.crew_member_level), orPrivate(member.level?.toString()))
                InfoPair(stringResource(R.string.crew_member_age), orPrivate(member.ageBand))
                InfoPair(stringResource(R.string.crew_member_gender), genderLabel(member.gender))
                member.joinedAt?.let { InfoPair(stringResource(R.string.crew_member_joined), dottedDate(it)) }
            }
            IntroBox(member.crewIntro)
            if (canManage && !member.isMe) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunkkButton(
                        stringResource(R.string.crew_manage_transfer),
                        onClick = { actions.onTransferAsk(member) },
                        style = RunkkButtonStyle.Outline,
                        size = RunkkButtonSize.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    RunkkButton(
                        stringResource(R.string.crew_manage_kick),
                        onClick = { actions.onKickAsk(member) },
                        style = RunkkButtonStyle.Purple,
                        size = RunkkButtonSize.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** 한 줄 소개 칸(surfaceHigh, 모서리 14, 좌우 14·위아래 12, 간격 6). 비면 "비공개"가 아니라 빈 칸 대신 "-". */
@Composable
private fun IntroBox(intro: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.crew_member_intro),
            style = RunkkTextStyles.text(12),
            color = RunkkColors.TextSecondary
        )
        Text(
            intro?.takeIf { it.isNotBlank() } ?: stringResource(R.string.crew_members_intro_empty),
            style = RunkkTextStyles.text(15, lineHeight = 21),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 신청 카드의 작은 알약(높이 36, 모서리 16, 13 글자). 채움은 피그마 respChip(#67705B), 외곽선은 divider 테두리 흰 바탕. */
@Composable
internal fun CrewPill(text: String, description: String, isFilled: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .heightIn(min = 36.dp)
                .clip(shape)
                .background(if (isFilled) RunkkColors.ChipSelected else RunkkColors.Surface)
                .then(if (isFilled) Modifier else Modifier.border(1.dp, RunkkColors.Border, shape))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                style = RunkkTextStyles.text(13, if (isFilled) FontWeight.Bold else FontWeight.Normal),
                color = if (isFilled) RunkkColors.OnChipSelected else RunkkColors.TextPrimary
            )
        }
    }
}

/**
 * 크루 정보·크루원 쪽 대화상자: 탈퇴 확인(F-19), 크루장 탈퇴 불가 안내(F-28, "닫기" 하나), 크루원 정보 팝업(F-22b·F-29b), 크루장
 * 넘기기(F-29c)·내보내기(F-29d)·크루 삭제(F-27) 확인, 초대 코드(F-18b). 확인은 W1 확인 대화상자(외곽선 취소 + 보라 확인)다.
 */
@Composable
internal fun CrewInfoDialogs(state: CrewHomeUiState, actions: CrewHomeActions) {
    if (state.isConfirmingLeave) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_home_leave_title),
            message = stringResource(R.string.crew_home_leave_message),
            confirmLabel = stringResource(R.string.crew_home_leave),
            onConfirm = actions.onLeaveConfirm,
            onDismiss = actions.onLeaveDismiss
        )
    }
    if (state.isLeaveBlocked) {
        RunkkDialog(
            title = stringResource(R.string.crew_home_leave_blocked_title),
            onDismissRequest = actions.onLeaveBlockedDismiss
        ) {
            RunkkDialogMessage(stringResource(R.string.crew_home_leave_blocked_message))
            RunkkDialogButtons(
                confirmLabel = stringResource(R.string.crew_home_leave_blocked_close),
                onConfirm = actions.onLeaveBlockedDismiss,
                confirmStyle = RunkkButtonStyle.Lime
            )
        }
    }
    state.openedMember?.let { CrewMemberDialog(it, state.canManage, actions) }
    state.confirmTransfer?.let {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_manage_transfer_title, it.displayName),
            message = stringResource(R.string.crew_manage_transfer_message),
            confirmLabel = stringResource(R.string.crew_manage_transfer_confirm),
            onConfirm = actions.onTransferConfirm,
            onDismiss = actions.onTransferDismiss
        )
    }
    state.confirmKick?.let {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_manage_kick_title, it.displayName),
            message = stringResource(R.string.crew_manage_kick_message),
            confirmLabel = stringResource(R.string.crew_manage_kick),
            onConfirm = actions.onKickConfirm,
            onDismiss = actions.onKickDismiss
        )
    }
    if (state.isConfirmingCrewDelete) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_manage_delete_title),
            message = stringResource(R.string.crew_manage_delete_message),
            confirmLabel = stringResource(R.string.crew_action_delete),
            onConfirm = actions.onCrewDeleteConfirm,
            onDismiss = actions.onCrewDeleteDismiss
        )
    }
    val code = state.detail?.inviteCode
    if (state.isInviteOpen && code != null) InviteCodeDialog(code, actions)
}
