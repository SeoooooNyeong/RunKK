package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.PendingRequests
import java.util.Locale

/**
 * 크루 정보(피그마 F-18 크루원·F-25 크루장, ⋯ 메뉴): 상단 바(가운데 제목, 오른쪽 "초대 코드"), 크루 기본 이미지·크루명·"개설일 · 인원",
 * 한 줄 소개, 태그(평일 보라·주말 라임 틴트), 누적 거리·누적 크루런 횟수, 「지난 크루런 ›」·「지난 목표 ›」. 크루장은 "수정" 링크, 가입
 * 신청 카드(거절·승인), 아래 "크루 삭제하기"를 더 본다. 맨 아래 "탈퇴하기".
 */
@Composable
internal fun CrewInfoPage(state: CrewHomeUiState, actions: CrewHomeActions) {
    BackHandler { actions.onPage(CrewHomePage.HOME) }
    val detail = state.detail
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_home_menu_info), onBack = {
            actions.onPage(CrewHomePage.HOME)
        }) {
            if (detail?.inviteCode != null) {
                RunkkButton(
                    stringResource(R.string.crew_info_invite),
                    onClick = actions.onInviteOpen,
                    style = RunkkButtonStyle.Outline,
                    size = RunkkButtonSize.Small,
                    modifier = Modifier.padding(end = 7.dp)
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (detail == null) {
                state.error?.let { RunkkErrorState(it.asString(), onRetry = actions.onRetry) }
                return@Column
            }
            CrewHeadline(detail, isLeader = state.canManage, onEdit = { actions.onPage(CrewHomePage.EDIT) })
            detail.intro?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = RunkkTextStyles.text(16, lineHeight = 22),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 5.dp)
                )
            }
            CrewTagTints(detail.tags)
            CrewStats(detail)
            Column {
                CrewNavRow(stringResource(R.string.crew_info_past_runs), actions.onPastRuns)
                CrewNavRow(stringResource(R.string.crew_info_past_goals), actions.onPastGoals)
            }
            if (state.canManage) JoinRequestSection(state.requests, actions, isBusy = state.isBusy)
        }
        InfoFooter(isLeader = state.canManage, actions)
    }
}

/** 로고·크루명·"개설일 · 인원"(MetaRow, 가운뎃점 대신 아이콘), 크루장은 오른쪽 "수정"(F-25). */
@Composable
private fun CrewHeadline(detail: CrewDetail, isLeader: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CrewDefaultImage(74.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                detail.name,
                style = RunkkTextStyles.text(19, FontWeight.Bold, lineHeight = 23),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            MetaRow(
                listOfNotNull(
                    detail.createdAt?.let { MetaItem(RunkkIcons.CalendarToday, dottedDate(it)) },
                    MetaItem(RunkkIcons.Group, stringResource(R.string.crew_info_headcount, detail.headcount))
                )
            )
        }
        if (isLeader) CrewUnderlineLink(stringResource(R.string.crew_info_edit), onClick = onEdit)
    }
}

/** 태그(피그마 `RunKK / Tag / 태그` Medium): 높이 28, 좌우 14, 모서리 14, 14 Medium. 평일 보라 틴트, 주말 라임 틴트. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CrewTagTints(tags: Collection<String>) {
    if (tags.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkTags.sorted(tags).forEach { id ->
            val isWeekend = RunkkTags.of(id)?.isWeekend == true
            Box(
                Modifier
                    .heightIn(min = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isWeekend) RunkkColors.TagLime else RunkkColors.TagPurple)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    RunkkTags.label(id),
                    style = RunkkTextStyles.text(14, FontWeight.Medium),
                    color = if (isWeekend) RunkkColors.OnTagLime else RunkkColors.OnTagPurple
                )
            }
        }
    }
}

/** 통계 두 칸(피그마 F-18): "누적 거리"(1,284.67km)·"누적 크루런 횟수"(n회), W4 크루 상세 응답 값. */
@Composable
private fun CrewStats(detail: CrewDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox(
            stringResource(R.string.crew_info_total_distance),
            stringResource(R.string.crew_contribution_km, groupedKm(detail.totalDistance)),
            Modifier.weight(1f)
        )
        StatBox(
            stringResource(R.string.crew_info_run_count),
            stringResource(R.string.crew_info_run_count_value, detail.crewRunCount),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(label: String, value: String?, modifier: Modifier = Modifier) {
    CrewOutlinedCard(modifier.semantics(mergeDescendants = true) {}) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = RunkkTextStyles.text(12), color = RunkkColors.TextPrimary)
            Text(
                value ?: RunkkFormat.EMPTY,
                style = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 24),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** "1,284.67"(천 단위 쉼표, 소수 둘째 자리). */
internal fun groupedKm(distance: Distance): String = String.format(Locale.US, "%,.2f", distance.meters / METERS_PER_KM)

private const val METERS_PER_KM = 1000.0

/** 가입 신청 구역(피그마 F-25): "가입 신청 N건"과 신청 카드(간격 8). 없으면 안내 한 줄. */
@Composable
private fun JoinRequestSection(requests: PendingRequests?, actions: CrewHomeActions, isBusy: Boolean) {
    val list = requests?.requests ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(
            stringResource(R.string.crew_info_requests),
            stringResource(R.string.crew_info_requests_count, list.size)
        )
        if (list.isEmpty()) {
            Text(
                stringResource(R.string.crew_manage_requests_empty),
                style = RunkkTextStyles.text(13),
                color = RunkkColors.TextSecondary
            )
        }
        list.forEach { JoinRequestCard(it, actions, isBusy) }
    }
}

/** 신청 카드: 38 이미지, 이름 14, 보조 줄(티어·레벨·나이대·성별, 공개한 값만), "거절"(외곽선) 다음 "승인"(#67705B 채움). */
@Composable
private fun JoinRequestCard(request: PendingRequests.Request, actions: CrewHomeActions, isBusy: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RunkkColors.SurfaceHigh)
                .border(1.dp, RunkkColors.Border, RoundedCornerShape(8.dp))
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                request.displayName,
                style = RunkkTextStyles.text(14, lineHeight = 21),
                color = RunkkColors.TextPrimary,
                maxLines = 1
            )
            ProfileMeta(request.tier, request.level, request.ageBand, request.gender?.let { genderLabel(it) })
        }
        CrewPill(
            stringResource(R.string.crew_manage_reject),
            stringResource(R.string.crew_manage_reject_description, request.displayName),
            isFilled = false,
            isEnabled = !isBusy
        ) { actions.onReject(request) }
        CrewPill(
            stringResource(R.string.crew_manage_approve),
            stringResource(R.string.crew_manage_approve_description, request.displayName),
            isFilled = true,
            isEnabled = !isBusy
        ) { actions.onApprove(request) }
    }
}

/** 보조 줄(가운뎃점 대신 MetaRow): 티어, 레벨, 나이대, 성별. 비공개·없는 값은 뺀다. */
@Composable
internal fun ProfileMeta(tier: String?, level: Int?, ageBand: String?, gender: String?) {
    val items = listOfNotNull(
        tierName(tier)?.let { MetaItem(RunkkIcons.EmojiEvents, it) },
        level?.let { MetaItem(RunkkIcons.TrendingUp, stringResource(R.string.crew_member_level_short, it)) },
        ageBand?.let { MetaItem(RunkkIcons.Person, it) },
        gender?.let { MetaItem(RunkkIcons.Person, it) }
    )
    if (items.isNotEmpty()) MetaRow(items)
}

/** 맨 아래 링크: 크루장은 "크루 삭제하기"·"탈퇴하기"(사이 22), 크루원은 "탈퇴하기". */
@Composable
private fun InfoFooter(isLeader: Boolean, actions: CrewHomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally)
    ) {
        if (isLeader) CrewUnderlineLink(stringResource(R.string.crew_info_delete), actions.onCrewDeleteAsk)
        CrewUnderlineLink(stringResource(R.string.crew_home_leave), actions.onLeaveAsk)
    }
}
