package com.a207.runkk.app.ui.crew

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PendingRequests
import kotlinx.coroutines.launch

/** 크루 관리(크루장·운영진, FR-113~FR-116·FR-123·FR-127·FR-136·FR-204). 보이는 기능은 내 역할(3.0.3)로 정한다. */
@Composable
fun CrewManageRoute(key: Routes.CrewManage, viewModel: CrewManageViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewManageEvent.Deleted -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    CrewManageScreen(
        state = state,
        actions = CrewManageActions(
            onBack = navigator::back,
            onApprove = viewModel::onApprove,
            onReject = viewModel::onReject,
            onReissue = viewModel::onReissue,
            onForm = viewModel::onForm,
            onSaveInfo = viewModel::onSaveInfo,
            onAsk = viewModel::onAsk,
            onDismissManager = viewModel::onDismissManager,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDismiss,
            onMenu = viewModel::onMenu,
            onMenuDismiss = viewModel::onMenuDismiss,
            onInfoOpen = viewModel::onInfoOpen,
            onInfoClose = viewModel::onInfoClose,
            onGoalEdit = { navigator.navigate(Routes.CrewGoalSet(key.crewId)) },
            onProfile = { member ->
                viewModel.onMenuDismiss()
                navigator.navigate(Routes.MemberProfile(member.memberId))
            },
            onCopied = viewModel::onCopied,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

/**
 * 크루 관리(와이어 I3): 초대 코드(복사, 사용 기한, 새로 발급), 가입 신청(승인, 거절), 크루원(⋯ 메뉴, I10), 주간 공동 목표 설정,
 * 크루 정보 수정(I9), 크루 삭제(I11). 운영진은 가입 신청과 공동 목표만 본다(3.0.3).
 */
@Composable
fun CrewManageScreen(state: CrewManageUiState, actions: CrewManageActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.isEditingInfo) {
            CrewEditPage(state, actions)
        } else {
            ManageMain(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    state.menuMember?.let { member ->
        val managerCount = state.members?.members.orEmpty().count { it.role == CrewRole.MANAGER }
        MemberMenuSheet(member, managerCount, actions)
    }
    state.confirming?.let { action -> ManageConfirmDialog(action, actions) }
}

@Composable
private fun ManageMain(state: CrewManageUiState, actions: CrewManageActions) {
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_manage_title), onBack = actions.onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.isLoading && state.members == null) {
                Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
            }
            state.error?.let { RunkkErrorState(it.asString(), onRetry = actions.onRetry) }
            if (state.isLeader) ManageInviteCode(state, actions)
            ManageRequests(state.requests?.requests.orEmpty(), actions)
            if (state.isLeader) ManageMembers(state.members?.members.orEmpty(), actions)
            if (state.canSetGoal) {
                ManageLinkRow(
                    stringResource(R.string.crew_manage_goal),
                    value = state.weeklyGoal?.let(RunkkFormat::targetLabel),
                    onClick = actions.onGoalEdit
                )
            }
            if (state.isLeader) {
                ManageLinkRow(
                    stringResource(R.string.crew_manage_section_info),
                    value = null,
                    onClick = actions.onInfoOpen
                )
                ManageDelete(onClick = { actions.onAsk(ManageAction.Delete) })
            }
        }
    }
}

/** 초대 코드(와이어 I3): 코드, 복사, "M.d까지 사용할 수 있어요", 새로 발급. */
@Composable
private fun ManageInviteCode(state: CrewManageUiState, actions: CrewManageActions) {
    val code = state.inviteCode
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val codeDescription =
        stringResource(R.string.crew_manage_invite_code_description, code.orEmpty().toList().joinToString(" "))
    val label = stringResource(R.string.crew_manage_section_invite)
    CrewCard {
        Text(
            label,
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                code ?: RunkkFormat.EMPTY,
                style = RunkkTextStyles.display(32),
                color = colors.primary,
                // 코드는 한 글자씩 읽어 전하기 쉽게, 새로 만들면 알린다.
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = codeDescription
                    liveRegion = LiveRegionMode.Polite
                }
            )
            if (code != null) {
                RunkkPillButton(
                    stringResource(R.string.crew_manage_invite_copy),
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, code)))
                            actions.onCopied()
                        }
                    }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                state.detail?.inviteCodeExpiresAt?.let {
                    stringResource(R.string.crew_manage_invite_until, shortDateLabel(it))
                }.orEmpty(),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            RunkkPillButton(stringResource(R.string.crew_manage_invite_reissue), onClick = actions.onReissue)
        }
    }
}

/** 가입 신청 n(와이어 I3): 줄마다 머리글자, 닉네임, 승인, 거절. */
@Composable
private fun ManageRequests(requests: List<PendingRequests.Request>, actions: CrewManageActions) {
    SectionTitle(stringResource(R.string.crew_manage_section_requests), count = requests.size)
    if (requests.isEmpty()) {
        Text(
            stringResource(R.string.crew_manage_requests_empty),
            style = RunkkTextStyles.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    requests.forEach { request ->
        val rejectDescription = stringResource(R.string.crew_manage_reject_description, request.displayName)
        val approveDescription = stringResource(R.string.crew_manage_approve_description, request.displayName)
        PersonRow(request.displayName, caption = null) {
            RunkkPillButton(
                stringResource(R.string.crew_manage_approve),
                onClick = { actions.onApprove(request) },
                modifier = Modifier.semantics { contentDescription = approveDescription }
            )
            RunkkPillButton(
                stringResource(R.string.crew_manage_reject),
                onClick = { actions.onReject(request) },
                modifier = Modifier.semantics { contentDescription = rejectDescription }
            )
        }
    }
}

/** 크루원 n(와이어 I3): 나(크루장)와 크루원, 크루원마다 ⋯ 메뉴(I10). */
@Composable
private fun ManageMembers(members: List<CrewMembers.Member>, actions: CrewManageActions) {
    SectionTitle(stringResource(R.string.crew_manage_section_members), count = members.size)
    members.forEach { member ->
        val name = if (member.isMe) {
            stringResource(R.string.crew_manage_me_role, stringResource(member.role.labelRes()))
        } else {
            member.displayName
        }
        val menuDescription = stringResource(R.string.crew_manage_member_menu, member.displayName)
        PersonRow(name, caption = null, badge = member.takeIf { !it.isMe && it.role == CrewRole.MANAGER }) {
            if (!member.isMe && !isLeader(member)) {
                CrewMoreButton(contentDescription = menuDescription, onClick = { actions.onMenu(member) })
            }
        }
    }
}

/** 사람 한 줄: 머리글자 동그라미, 이름(운영진 배지), 보조 줄, 오른쪽 단추들. */
@Composable
private fun PersonRow(
    name: String,
    caption: String?,
    badge: CrewMembers.Member? = null,
    trailing: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CrewLogo(name, size = 40, modifier = Modifier.clip(CircleShape))
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name,
                    style = RunkkTextStyles.BodyStrong,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                badge?.let { RunkkBadge(stringResource(it.role.labelRes()), tone = RunkkBadgeTone.Muted) }
            }
            caption?.let { Text(it, style = RunkkTextStyles.Caption, color = colors.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            trailing()
        }
    }
}

/** 다른 화면으로 가는 줄: 이름, 값(있으면), ›. */
@Composable
private fun ManageLinkRow(label: String, value: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RunkkShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = RunkkTextStyles.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
        value?.let { Text(it, style = RunkkTextStyles.Body, color = colors.onSurfaceVariant) }
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 맨 아래 크루 삭제(와이어 I3). 누르면 확인(I11). */
@Composable
private fun ManageDelete(onClick: () -> Unit) {
    Text(
        stringResource(R.string.crew_manage_delete),
        style = RunkkTextStyles.BodyStrong,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp, vertical = 14.dp)
    )
}

/** 크루원 메뉴 시트(와이어 I10): 닉네임과 "티어, 역할", 운영진 지정(해제), 크루장 넘기기, 크루에서 내보내기, 닫기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberMenuSheet(member: CrewMembers.Member, managerCount: Int, actions: CrewManageActions) {
    val colors = MaterialTheme.colorScheme
    val isManager = member.role == CrewRole.MANAGER
    ModalBottomSheet(onDismissRequest = actions.onMenuDismiss, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                Modifier.padding(bottom = 8.dp).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CrewLogo(member.displayName, size = 40, modifier = Modifier.clip(CircleShape))
                Column {
                    Text(
                        member.displayName,
                        style = RunkkTextStyles.CardTitle,
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        listOfNotNull(member.tier?.takeIf { it.isNotBlank() }, stringResource(member.role.labelRes()))
                            .joinToString(", "),
                        style = RunkkTextStyles.Caption,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            MenuItem(stringResource(R.string.crew_manage_profile), onClick = { actions.onProfile(member) })
            MenuItem(
                stringResource(if (isManager) R.string.crew_manage_dismiss else R.string.crew_manage_appoint),
                onClick = {
                    if (isManager) {
                        actions.onDismissManager(member)
                    } else {
                        actions.onAsk(ManageAction.Appoint(member, managerCount))
                    }
                }
            )
            MenuItem(
                stringResource(R.string.crew_manage_transfer),
                onClick = { actions.onAsk(ManageAction.Transfer(member)) }
            )
            MenuItem(
                stringResource(R.string.crew_manage_kick_menu),
                onClick = { actions.onAsk(ManageAction.Kick(member)) },
                color = colors.secondary
            )
            RunkkSecondaryButton(
                stringResource(R.string.crew_manage_menu_close),
                onClick = actions.onMenuDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                height = 48.dp
            )
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = RunkkTextStyles.BodyStrong, color = color, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 크루 정보 수정(와이어 I9): 뒤로, 제목, 로고 자리, 크루명, 한줄 소개, 태그, 공개 여부, 가입 방식, 저장. */
@Composable
private fun CrewEditPage(state: CrewManageUiState, actions: CrewManageActions) {
    BackHandler(onBack = actions.onInfoClose)
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_manage_section_info), onBack = actions.onInfoClose)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CrewLogo(state.form.name.ifBlank { state.crewName }, size = 72, isAccent = true)
            CrewFormFields(state.form, actions.onForm)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RunkkPrimaryButton(
            stringResource(R.string.crew_manage_info_save),
            onClick = actions.onSaveInfo,
            isEnabled = state.form.isValid,
            isLoading = state.isBusy,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

/** 내보내기·크루장 넘기기·크루 삭제(I11) 확인 대화상자와 운영진 지정 확인(M9). */
@Composable
private fun ManageConfirmDialog(action: ManageAction, actions: CrewManageActions) {
    if (action is ManageAction.Appoint) {
        CrewStaffConfirmDialog(action, onConfirm = actions.onConfirm, onDismiss = actions.onDismiss)
        return
    }
    val (title, message, label) = when (action) {
        is ManageAction.Kick -> Triple(
            stringResource(R.string.crew_manage_kick_title, action.member.displayName),
            stringResource(R.string.crew_manage_kick_message),
            stringResource(R.string.crew_manage_kick)
        )

        is ManageAction.Transfer -> Triple(
            stringResource(R.string.crew_manage_transfer_title, action.member.displayName),
            stringResource(R.string.crew_manage_transfer_message),
            stringResource(R.string.crew_manage_transfer_confirm)
        )

        ManageAction.Delete -> Triple(
            stringResource(R.string.crew_manage_delete_title),
            stringResource(R.string.crew_manage_delete_message),
            stringResource(R.string.crew_action_delete)
        )

        is ManageAction.Appoint -> return
    }
    RunkkConfirmDialog(
        title,
        message,
        label,
        onConfirm = actions.onConfirm,
        onDismiss = actions.onDismiss,
        isDestructive = true
    )
}

/** 구역 제목과 개수("가입 신청 2"). */
@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = RunkkTextStyles.CardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Text(count.toString(), style = RunkkTextStyles.CardTitle, color = MaterialTheme.colorScheme.primary)
    }
}
