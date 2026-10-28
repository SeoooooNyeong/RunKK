package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.RaceStatus

/**
 * 크루원 탭(와이어 E4, FR-135, FR-144): 크루에서 보이는 내 정보(한줄 소개, 편집), 크루원 목록(역할, 티어, 한줄 소개, 이번 주 거리),
 * 맨 아래 크루 탈퇴. 비공개 크루원은 티어가 오지 않는다.
 */
@Composable
internal fun ColumnScope.MembersTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val members = state.members?.members
    if (members == null) {
        TabLoading(state.isLoading)
        return
    }
    MyCrewInfo(members.firstOrNull { it.isMe }?.crewIntro, onEdit = actions.onIntroEdit)
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.crew_members_title, members.size),
            style = RunkkTextStyles.CardTitle,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Text(
            stringResource(R.string.crew_members_weekly),
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant
        )
    }
    members.forEach { member ->
        MemberRow(member, weeklyDistance(state.race, member), onOpen = { actions.onMember(member) })
    }
    RunkkTextLink(
        stringResource(R.string.crew_home_leave),
        onClick = actions.onLeaveAsk,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    )
}

/** 크루에서 보이는 내 정보: 제목, 편집 단추, 내 한줄 소개. */
@Composable
private fun MyCrewInfo(intro: String?, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    CrewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_home_intro_title),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            RunkkPillButton(stringResource(R.string.crew_home_intro_edit), onClick = onEdit)
        }
        Text(
            intro?.takeIf { it.isNotBlank() } ?: stringResource(R.string.crew_members_intro_empty),
            style = RunkkTextStyles.Body,
            color = if (intro.isNullOrBlank()) colors.onSurfaceVariant else colors.onSurface
        )
    }
}

/**
 * 크루원 한 줄: 머리글자, 이름(나), 역할 배지(크루장, 운영진), "티어, 한줄 소개", 이번 주 거리. 다른 크루원 줄을 누르면 그 러너의
 * 프로필(F8)로 간다(내 줄은 누르지 않는다).
 */
@Composable
private fun MemberRow(member: CrewMembers.Member, weekly: Distance, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = if (member.isMe) stringResource(R.string.crew_member_me) else member.displayName
    val openLabel = stringResource(R.string.crew_member_profile_open)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (member.isMe) {
                    Modifier
                } else {
                    Modifier
                        .clip(RunkkShapes.Medium)
                        .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen)
                }
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CrewLogo(member.displayName, size = 40, isAccent = member.isMe, modifier = Modifier.clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = RunkkTextStyles.BodyStrong, color = colors.onSurface, maxLines = 1)
                if (member.role != CrewRole.MEMBER) {
                    RunkkBadge(
                        stringResource(member.role.labelRes()),
                        tone = if (isLeader(member)) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
                    )
                }
            }
            memberCaption(member)?.let {
                Text(
                    it,
                    style = RunkkTextStyles.Caption,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            stringResource(R.string.crew_contribution_km, RunkkFormat.kmShort(weekly)),
            style = RunkkTextStyles.text(14, FontWeight.Bold),
            color = colors.onSurface
        )
    }
}

/** "티어, 한줄 소개"(둘 다 없으면 null). */
internal fun memberCaption(member: CrewMembers.Member): String? =
    listOfNotNull(member.tier?.takeIf { it.isNotBlank() }, member.crewIntro?.takeIf { it.isNotBlank() })
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")

/** 크루원의 이번 주 거리(레이스 기여자에서 memberId로, 본인은 isMe로 찾는다). 없으면 0. */
private fun weeklyDistance(race: RaceStatus?, member: CrewMembers.Member): Distance {
    val contributors = race?.contributors.orEmpty()
    val found = contributors.firstOrNull { it.memberId != null && it.memberId == member.memberId }
        ?: contributors.firstOrNull { member.isMe && it.isMe }
    return found?.distance ?: Distance(0)
}

internal fun isLeader(member: CrewMembers.Member): Boolean = member.role == CrewRole.LEADER
