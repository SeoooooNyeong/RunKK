package com.a207.runkk.app.ui.crew

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkActionDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import kotlinx.coroutines.launch

/**
 * 크루 정보(피그마 F-18·F-25, ⋯ 메뉴): 뒤로, 제목, 초대 코드(코드를 받은 역할이면), 로고와 크루명·인원, 한줄 소개, 태그, 이번 주와
 * 지난 주 달린 거리, 맨 아래 탈퇴하기. 가입 신청·정보 수정·크루 삭제는 ⋯ 메뉴의 크루 관리에 있다.
 */
@Composable
internal fun CrewInfoPage(state: CrewHomeUiState, actions: CrewHomeActions) {
    BackHandler { actions.onPage(CrewHomePage.HOME) }
    val colors = MaterialTheme.colorScheme
    val detail = state.detail
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(
            title = stringResource(R.string.crew_home_menu_info),
            onBack = { actions.onPage(CrewHomePage.HOME) }
        ) {
            Box(Modifier.weight(1f))
            if (detail?.inviteCode != null) {
                RunkkPillButton(stringResource(R.string.crew_info_invite), onClick = actions.onInviteOpen)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (detail == null) {
                state.error?.let { RunkkErrorState(it.asString(), onRetry = actions.onRetry) }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CrewLogo(detail.name, size = 72, isAccent = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        detail.name,
                        style = RunkkTextStyles.TopBarTitle,
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        stringResource(R.string.crew_meta_headcount, detail.headcount, CREW_MAX),
                        style = RunkkTextStyles.Caption,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            detail.intro?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = RunkkTextStyles.Body, color = colors.onSurface)
            }
            CrewTagBadges(detail.tags)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CrewStatCard(
                    stringResource(R.string.crew_info_this_week),
                    kmLabel(detail.weeklyDistance),
                    Modifier.weight(1f)
                )
                CrewStatCard(
                    stringResource(R.string.crew_detail_last_week),
                    kmLabel(detail.lastWeekDistance),
                    Modifier.weight(1f)
                )
            }
        }
        RunkkTextLink(
            stringResource(R.string.crew_home_leave),
            onClick = actions.onLeaveAsk,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)
        )
    }
}

/** 크루원(피그마 F-22·F-29, ⋯ 메뉴): 뒤로, 제목, 내 정보와 크루원 목록. */
@Composable
internal fun CrewMembersPage(state: CrewHomeUiState, actions: CrewHomeActions) {
    BackHandler { actions.onPage(CrewHomePage.HOME) }
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(
            title = stringResource(R.string.crew_home_tab_members),
            onBack = { actions.onPage(CrewHomePage.HOME) }
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val error = state.error
            if (error != null && !state.isLoading) {
                RunkkErrorState(error.asString(), onRetry = actions.onRetry)
            } else {
                MembersTab(state, actions)
            }
        }
    }
}

/** 초대 코드 팝업(피그마 F-18b·F-25b): 제목, 코드, 복사. 코드는 한 글자씩 읽힌다. */
@Composable
internal fun InviteCodeDialog(code: String, actions: CrewHomeActions) {
    val label = stringResource(R.string.crew_info_invite)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val spoken = stringResource(R.string.crew_manage_invite_code_description, code.toList().joinToString(" "))
    RunkkActionDialog(
        title = label,
        confirmLabel = stringResource(R.string.crew_manage_invite_copy),
        onConfirm = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, code)))
                actions.onInviteCopied()
            }
        },
        onDismissRequest = actions.onInviteClose,
        dismissLabel = stringResource(R.string.crew_info_invite_close)
    ) {
        Text(
            code.toList().joinToString(" "),
            style = RunkkTextStyles.display(28),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Medium)
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp)
                .semantics { contentDescription = spoken }
        )
    }
}
