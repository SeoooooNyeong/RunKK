package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole

/** 크루원(피그마 F-22 크루원·F-29 크루장, ⋯ 메뉴): 가운데 제목·뒤로, "내 정보" 카드, "크루원 n"과 누적 거리 목록. */
@Composable
internal fun CrewMembersPage(state: CrewHomeUiState, actions: CrewHomeActions) {
    BackHandler { actions.onPage(CrewHomePage.HOME) }
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_home_tab_members), onBack = {
            actions.onPage(CrewHomePage.HOME)
        })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val error = state.error
            val members = state.members?.members
            when {
                error != null && !state.isLoading -> RunkkErrorState(error.asString(), onRetry = actions.onRetry)
                members == null -> TabLoading(state.isLoading)
                else -> MemberList(members, actions)
            }
        }
    }
}

/** 내 정보 카드와 크루원 목록(서버 순서 = 누적 거리 내림차순). 줄을 누르면 크루원 정보 팝업(F-22b·F-29b)을 연다. */
@Composable
private fun MemberList(members: List<CrewMembers.Member>, actions: CrewHomeActions) {
    members.firstOrNull { it.isMe }?.let { MyInfoCard(it, onEdit = actions.onIntroEdit) }
    SectionTitle(
        stringResource(R.string.crew_home_tab_members),
        members.size.toString(),
        modifier = Modifier.padding(top = 4.dp),
        trailing = stringResource(R.string.crew_members_accumulated)
    )
    Column {
        members.forEach { MemberRow(it, onOpen = { actions.onMemberOpen(it) }) }
    }
}

/** 내 정보 카드(피그마 F-22·F-29): "내 정보" 14 Bold + "편집"(높이 30), 아래 "티어 · 레벨 · 나이대 · 성별"(공개 설정과 무관하게 내 값). */
@Composable
private fun MyInfoCard(me: CrewMembers.Member, onEdit: () -> Unit) {
    CrewOutlinedCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.crew_members_my_info),
                    style = RunkkTextStyles.text(14, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                RunkkButton(
                    stringResource(R.string.crew_home_intro_edit),
                    onClick = onEdit,
                    style = RunkkButtonStyle.Outline,
                    size = RunkkButtonSize.Small
                )
            }
            ProfileMeta(me.tier, me.level, me.ageBand, me.gender?.let { genderLabel(it) })
        }
    }
}

/** 크루원 한 줄(피그마 F-22): 36 원, 이름 14(나는 "나" Bold), 크루장만 배지, 오른쪽 누적 거리 14 Bold, 아래 divider. */
@Composable
private fun MemberRow(member: CrewMembers.Member, onOpen: () -> Unit) {
    val name = if (member.isMe) stringResource(R.string.crew_member_me) else member.displayName
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 53.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.crew_member_open, member.displayName),
                    onClick = onOpen
                )
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MemberAvatar(36.dp)
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    name,
                    style = RunkkTextStyles.text(14, if (member.isMe) FontWeight.Bold else FontWeight.Normal),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (member.role == CrewRole.LEADER) LeaderBadge()
            }
            Text(
                stringResource(R.string.crew_contribution_km, groupedKm(member.accumulated)),
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
        }
        HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
    }
}

internal fun isLeader(member: CrewMembers.Member): Boolean = member.role == CrewRole.LEADER
