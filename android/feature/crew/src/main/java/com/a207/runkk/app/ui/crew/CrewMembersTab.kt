package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPersonRow
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.FaceLayer
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
                else -> MemberList(members, state.memberFaces, actions)
            }
        }
    }
}

/** 내 정보 카드와 크루원 목록(서버 순서 = 누적 거리 내림차순). 줄을 누르면 크루원 정보 팝업(F-22b·F-29b)을 연다. */
@Composable
private fun MemberList(
    members: List<CrewMembers.Member>,
    faces: Map<String, List<FaceLayer>>,
    actions: CrewHomeActions
) {
    members.firstOrNull { it.isMe }?.let { MyInfoCard(it, onEdit = actions.onIntroEdit) }
    RunkkSectionHeader(
        stringResource(R.string.crew_home_tab_members),
        modifier = Modifier.padding(top = 4.dp),
        count = members.size.toString(),
        countColor = RunkkColors.TextPrimary
    ) {
        Text(
            stringResource(R.string.crew_members_accumulated),
            style = RunkkTextStyles.text(12),
            color = RunkkColors.TextPrimary
        )
    }
    Column {
        members.forEach { member ->
            MemberRow(member, member.faceKey?.let(faces::get).orEmpty(), onOpen = { actions.onMemberOpen(member) })
        }
    }
}

/** 내 정보 카드(피그마 F-22·F-29): "내 정보" 14 Bold + "편집"(높이 30), 아래 "티어 · 레벨 · 나이대 · 성별"(공개 설정과 무관하게 내 값). */
@Composable
private fun MyInfoCard(me: CrewMembers.Member, onEdit: () -> Unit) {
    RunkkCard(
        Modifier.fillMaxWidth(),
        shape = RunkkShapes.ExtraLarge,
        contentPadding = PaddingValues(14.dp),
        borderColor = RunkkColors.Border,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        RunkkSectionHeader(stringResource(R.string.crew_members_my_info), size = RunkkSectionHeaderSize.Compact) {
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

/**
 * 크루원 한 줄(피그마 F-22, 실기기 수정 ①): 36 원에 그 크루원의 아바타 얼굴([임시:FR-성-24]: 얼굴 키가 있으면 그 얼굴, 없으면 표시
 * 성별의 기본 얼굴), 이름 14(나는 "나" Bold), 크루장만 배지, 이름 아래 아이콘 메타 줄(러니멀 유형·레벨, 가운뎃점 없이), 오른쪽 누적
 * 거리 14 Bold, 아래 divider.
 */
@Composable
private fun MemberRow(member: CrewMembers.Member, faceLayers: List<FaceLayer>, onOpen: () -> Unit) {
    val name = if (member.isMe) stringResource(R.string.crew_member_me) else member.displayName
    val meta = memberMeta(member)
    RunkkPersonRow(
        name = name,
        avatar = {
            RunkkAvatarCircle(
                faceKey = member.faceKey,
                size = MEMBER_FACE,
                faceLayers = faceLayers,
                character = AvatarCharacter.of(member.shownGender)
            )
        },
        isMe = member.isMe,
        supportingContent = if (meta.isEmpty()) {
            null
        } else {
            { MetaRow(meta) }
        },
        badge = if (member.role == CrewRole.LEADER) {
            { LeaderBadge() }
        } else {
            null
        },
        trailing = {
            Text(
                stringResource(R.string.crew_contribution_km, groupedKm(member.accumulated)),
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary
            )
        },
        onClick = onOpen,
        onClickLabel = stringResource(R.string.crew_member_open, member.displayName),
        minHeight = 53.dp
    )
}

/** 크루원 줄의 메타 항목: 러니멀 유형(확정된 유형만)과 레벨. 없는 값은 뺀다. */
@Composable
private fun memberMeta(member: CrewMembers.Member): List<MetaItem> {
    val art = RunimalArt.of(member.runimalType)
    return listOfNotNull(
        art.takeIf { it != RunimalArt.UNKNOWN }?.let { MetaItem(RunkkIcons.DirectionsRun, stringResource(it.nameRes)) },
        member.level?.let { MetaItem(RunkkIcons.TrendingUp, stringResource(R.string.crew_member_level_short, it)) }
    )
}

/** 크루원 줄의 얼굴 원 지름(피그마 F-22). */
private val MEMBER_FACE = 36.dp

internal fun isLeader(member: CrewMembers.Member): Boolean = member.role == CrewRole.LEADER
