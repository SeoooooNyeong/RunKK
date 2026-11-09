package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PostList
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * 크루런 탭(피그마 F-11 새 디자인, FR-133, FR-145, FR-147): 위에 "+ 크루런 모집하기"와 "지난 크루런 ›", 가장 가까운 크루런 하나를
 * 보라 강조 카드로(남은 시간, 대기실 입장), 나머지는 이번주·다음주·이후 묶음의 날짜 줄(오른쪽 참석 칩), 맨 아래 지난 크루런.
 * 강조 카드 제목이나 줄을 누르면 응답 시트(참석·불참·미정, 대기실, 삭제·신고)가 열리고, 칩을 누르면 참석을 켜고 끈다(끄면 미정).
 * [onPast]는 "지난 크루런 ›"(목록을 지난 크루런 머리글로 내린다)이다.
 */
internal fun LazyListScope.scheduleTabItems(state: CrewHomeUiState, actions: CrewHomeActions, onPast: () -> Unit = {}) {
    val (upcoming, past) = splitSchedules(state)
    val now = state.scheduleNow ?: Instant.now()
    val hero = upcoming.firstOrNull()
    item(key = "schedule:head") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ScheduleTopRow(hasPast = past.isNotEmpty(), actions = actions, onPast = onPast)
            when {
                state.crewRuns == null -> TabLoading(state.isLoading)
                upcoming.isEmpty() && past.isEmpty() -> RunkkEmptyState(stringResource(R.string.crew_schedule_empty))
                else -> Unit
            }
        }
    }
    if (hero != null) {
        item(key = "schedule:hero:${hero.postId}") { HeroRun(hero, state.lobbyEntries[hero.postId], now, actions) }
    }
    scheduleGroups(upcoming.drop(1), now).forEach { (group, posts) ->
        item(key = "schedule:group:$group") { ScheduleGroupSection(group, posts, actions) }
    }
    if (past.isNotEmpty()) {
        item(key = "schedule:past") {
            Text(
                stringResource(R.string.crew_schedule_past),
                style = RunkkTextStyles.CardTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() }
            )
        }
        items(past, key = { "schedule:past:${it.postId}" }) { post -> PastSchedule(post, actions) }
    }
}

/** "지난 크루런" 머리글의 목록 위치(scheduleTabItems 항목 순서와 같게 센다). 지난 크루런이 없으면 null. */
internal fun schedulePastIndex(state: CrewHomeUiState): Int? {
    val (upcoming, past) = splitSchedules(state)
    if (past.isEmpty()) return null
    val now = state.scheduleNow ?: Instant.now()
    val heroes = if (upcoming.isEmpty()) 0 else 1
    return 1 + heroes + scheduleGroups(upcoming.drop(1), now).size
}

/** 크루런을 다가오는 것(가까운 일시순)과 지난 것(최근순)으로 나눈다. 끝났거나 취소된 크루런이 지난 크루런이다. */
internal fun splitSchedules(state: CrewHomeUiState): Pair<List<PostList.Item>, List<PostList.Item>> {
    val all = state.crewRuns?.posts.orEmpty().filter { it.kind == PostList.Kind.CREW_RUN }
    val (past, upcoming) = all.partition { isPast(it) }
    return upcoming.sortedBy { scheduledAt(it) } to past.sortedByDescending { scheduledAt(it) }
}

/** 다가오는 크루런 묶음: 이번주(월~일, 이미 지난 날 포함), 다음주, 그 뒤. */
internal enum class ScheduleGroup(val labelRes: Int) {
    THIS_WEEK(R.string.crew_schedule_this_week),
    NEXT_WEEK(R.string.crew_schedule_next_week),
    LATER(R.string.crew_schedule_later)
}

/** [posts](가까운 일시순)를 [now]가 든 주 기준으로 묶는다. 빈 묶음은 뺀다. */
internal fun scheduleGroups(
    posts: List<PostList.Item>,
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault()
): List<Pair<ScheduleGroup, List<PostList.Item>>> {
    val weekStart = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val nextWeek = weekStart.plusWeeks(1)
    val later = weekStart.plusWeeks(2)
    return posts.groupBy { post ->
        val date = scheduledAt(post).atZone(zone).toLocalDate()
        when {
            date.isBefore(nextWeek) -> ScheduleGroup.THIS_WEEK
            date.isBefore(later) -> ScheduleGroup.NEXT_WEEK
            else -> ScheduleGroup.LATER
        }
    }.toSortedMap().toList()
}

private fun scheduledAt(post: PostList.Item): Instant = post.crewRun?.scheduledAt ?: post.writtenAt

private fun isPast(post: PostList.Item): Boolean {
    val status = post.crewRun?.status
    return status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED
}

/** 위쪽 줄: 왼쪽 "+ 크루런 모집하기", 오른쪽 "지난 크루런 ›"(지난 크루런이 있을 때). */
@Composable
private fun ScheduleTopRow(hasPast: Boolean, actions: CrewHomeActions, onPast: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RunkkPillButton(
            stringResource(R.string.crew_schedule_write),
            onClick = actions.onWriteSchedule,
            leadingIcon = RunkkIcons.Plus
        )
        Box(Modifier.weight(1f))
        if (hasPast) RunkkTextLink(stringResource(R.string.crew_schedule_past_link), onClick = onPast)
    }
}

/**
 * 가장 가까운 크루런(보라 강조 카드): 날짜·시각("오늘 …"), "참석 n명", 제목 ›(응답 시트), 남은 시간 "29:45 후 시작"(하루 넘게 남으면
 * "n일 후 시작"), 대기실 입장 단추. 대기실이 아직 안 열렸거나 닫혔으면 단추를 누를 수 없고 기존 안내 문구를 보인다.
 */
@Composable
private fun HeroRun(post: PostList.Item, lobby: LobbyAvailability?, now: Instant, actions: CrewHomeActions) {
    val run = post.crewRun ?: return
    val entry = CrewRunEntry.of(run.status, lobby)
    val onHero = RunkkColors.Violet
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(onHero)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heroDateLabel(run.scheduledAt, now),
                style = RunkkTextStyles.text(12),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_schedule_going_people, run.goingCount),
                style = RunkkTextStyles.text(12),
                color = Color.White
            )
        }
        RunTitle(
            post.summary.ifBlank { crewRunSummary(run) },
            style = RunkkTextStyles.text(20, FontWeight.Bold),
            color = Color.White,
            chevronColor = Color.White,
            onClick = { actions.onRunOpen(post) }
        )
        StartsIn(Duration.between(now, run.scheduledAt))
        entry.lobby?.let { HeroLobbyButton(post, it, actions) }
    }
}

/** 남은 시간 "29:45 후 시작"(하루 넘게 남으면 "n일 후 시작"). */
@Composable
private fun StartsIn(left: Duration) {
    val clamped = left.coerceAtLeast(Duration.ZERO)
    val days = clamped.toDays()
    val big = if (days >=
        1
    ) {
        stringResource(R.string.crew_schedule_days, days.toInt())
    } else {
        RunkkFormat.duration(clamped)
    }
    val spokenLeft = if (days >= 1) big else RunkkFormat.durationSpoken(clamped)
    val spoken = stringResource(R.string.crew_schedule_starts_in_spoken, spokenLeft)
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
    ) {
        Text(big, style = RunkkTextStyles.text(28, FontWeight.Bold), color = Color.White)
        Text(
            stringResource(R.string.crew_schedule_starts_in),
            style = RunkkTextStyles.text(13),
            color = Color.White,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

/** 강조 카드의 흰 알약 단추(피그마 White 48): 열렸으면 "대기실 입장하기", 아니면 누를 수 없고 기존 안내 문구. */
@Composable
private fun HeroLobbyButton(post: PostList.Item, lobby: LobbyAvailability, actions: CrewHomeActions) {
    val text = lobbyButtonText(lobby)
    val label = post.summary.ifBlank { post.crewRun?.let { crewRunSummary(it) }.orEmpty() }
    val description = stringResource(R.string.crew_home_run_lobby_description, text, label)
    val isOpen = lobby == LobbyAvailability.Open
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Surface)
            .alpha(if (isOpen) 1f else DISABLED_ALPHA)
            .clickable(enabled = isOpen, role = Role.Button, onClick = { actions.onLobby(post) })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = RunkkTextStyles.text(15, FontWeight.SemiBold), color = RunkkColors.TextPrimary)
    }
}

/** "오늘 10.21 (수) 19:30"(오늘이 아니면 날짜·시각만). */
@Composable
private fun heroDateLabel(at: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val label = scheduleLabel(at)
    val isToday = at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    return if (isToday) stringResource(R.string.crew_schedule_today, label) else label
}

/** 제목과 ›. 누르면 응답 시트를 연다. */
@Composable
private fun RunTitle(
    title: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    chevronColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openLabel = stringResource(R.string.crew_schedule_open_label)
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            title,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = chevronColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 묶음 하나: "이번주 2" 머리글과 날짜 줄들(줄 아래 구분선). */
@Composable
private fun ScheduleGroupSection(group: ScheduleGroup, posts: List<PostList.Item>, actions: CrewHomeActions) {
    val label = stringResource(group.labelRes)
    val spoken = stringResource(R.string.crew_schedule_group_spoken, label, posts.size)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).semantics(mergeDescendants = true) {
                heading()
                contentDescription = spoken
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.TextPrimary)
            Text(posts.size.toString(), style = RunkkTextStyles.text(13), color = RunkkColors.TextSecondary)
        }
        posts.forEach { post ->
            ScheduleRow(post, actions)
            HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

/** 날짜 줄: 일·요일, 제목 ›(응답 시트), "07:00, 5km", 오른쪽 참석 칩(내가 참석이면 채운 칩). */
@Composable
private fun ScheduleRow(post: PostList.Item, actions: CrewHomeActions) {
    val run = post.crewRun ?: return
    val zone = ZoneId.systemDefault()
    val at = run.scheduledAt.atZone(zone)
    val day = at.format(DateTimeFormatter.ofPattern(stringResource(R.string.crew_schedule_day_pattern), Locale.KOREAN))
    val weekday =
        at.format(DateTimeFormatter.ofPattern(stringResource(R.string.crew_schedule_weekday_pattern), Locale.KOREAN))
    val time = at.format(
        DateTimeFormatter.ofPattern(stringResource(R.string.crew_schedule_time_pattern), Locale.KOREAN)
    )
    val dateSpoken = scheduleLabel(run.scheduledAt)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.width(40.dp).clearAndSetSemantics { contentDescription = dateSpoken },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(day, style = RunkkTextStyles.text(20, FontWeight.Bold), color = RunkkColors.TextPrimary)
            Text(weekday, style = RunkkTextStyles.text(12), color = RunkkColors.TextSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            RunTitle(
                post.summary.ifBlank { crewRunSummary(run) },
                style = RunkkTextStyles.text(15, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                chevronColor = RunkkColors.TextSecondary,
                onClick = { actions.onRunOpen(post) }
            )
            Text(
                stringResource(R.string.crew_schedule_row_caption, time, RunkkFormat.targetLabel(run.distance)),
                style = RunkkTextStyles.text(12),
                color = RunkkColors.TextSecondary
            )
        }
        GoingChip(post, run.goingCount, isGoing = run.myAnswer == AttendanceAnswer.GOING, actions)
    }
}

/**
 * 오른쪽 참석 칩 "참석 n": 내가 참석이면 채운 칩, 아니면 외곽선. 응답할 수 있을 때(투표 중) 누르면 참석을 켜고, 이미 참석이면 미정으로
 * 돌린다. 불참은 응답 시트에서 고른다.
 */
@Composable
private fun GoingChip(post: PostList.Item, going: Int, isGoing: Boolean, actions: CrewHomeActions) {
    val canVote = CrewRunEntry.of(post.crewRun?.status, null).canVote
    val label = stringResource(R.string.crew_schedule_going, going)
    val chipLabel = stringResource(R.string.crew_schedule_chip_label)
    val shape = RoundedCornerShape(if (isGoing) 16.dp else 8.dp)
    val base = Modifier
        .size(width = 74.dp, height = 34.dp)
        .clip(shape)
        .then(
            if (isGoing) {
                Modifier.background(RunkkColors.TextSecondary)
            } else {
                Modifier.border(1.dp, RunkkColors.Border, shape)
            }
        )
    val toggle = if (canVote) {
        Modifier.toggleable(value = isGoing, role = Role.Switch) { on ->
            actions.onAttendance(post, if (on) AttendanceAnswer.GOING else AttendanceAnswer.UNDECIDED)
        }
    } else {
        Modifier
    }
    Box(
        modifier = base.then(toggle).semantics { contentDescription = "$chipLabel, $label" },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = RunkkTextStyles.text(13, if (isGoing) FontWeight.Bold else FontWeight.Medium),
            color = if (isGoing) RunkkColors.Background else RunkkColors.TextSecondary
        )
    }
}

/**
 * 응답 시트(와이어 I4 일정 카드 내용): 종류·작성자·삭제·신고, 제목, 일시와 거리, 응답 수와 응답 마감, 참석·불참·미정, 대기실 단추.
 * 강조 카드 제목이나 날짜 줄을 누르면 열린다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrewRunSheet(post: PostList.Item, lobby: LobbyAvailability?, actions: CrewHomeActions) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = actions.onRunClose, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CrewRunPostContent(post, lobby, actions)
        }
    }
}

/** 크루런 일정 내용(와이어 I4): 종류, 참석 수, 제목, 일시와 거리, 응답 수와 응답 마감, 응답 단추, 대기실 단추. */
@Composable
private fun CrewRunPostContent(post: PostList.Item, lobby: LobbyAvailability?, actions: CrewHomeActions) {
    val run = post.crewRun
    val entry = CrewRunEntry.of(run?.status, lobby)
    val colors = MaterialTheme.colorScheme
    ScheduleHeader(
        post,
        kind = stringResource(R.string.crew_schedule_kind_crew_run_remote),
        trailing = run?.let { stringResource(R.string.crew_schedule_going, it.goingCount) },
        actions = actions
    )
    if (post.summary.isNotBlank()) {
        Text(post.summary, style = RunkkTextStyles.BodyStrong, color = colors.onSurface)
    }
    if (run != null) {
        Text(
            listOf(scheduleLabel(run.scheduledAt), RunkkFormat.targetLabel(run.distance)).joinToString(", "),
            style = RunkkTextStyles.Body,
            color = colors.onSurface
        )
        AnswerCounts(run.goingCount, run.notGoingCount, run.undecidedCount, deadline = run.voteDeadline)
    }
    AttendanceButtons(post, run?.myAnswer, entry.canVote, actions)
    entry.lobby?.let { LobbyButton(post, it, actions) }
}

/** 일정 윗줄: 종류 배지, 작성자(내 글은 "나"), 참석 수나 정원, 삭제(지울 수 있으면), 신고(남의 글). */
@Composable
private fun ScheduleHeader(post: PostList.Item, kind: String, trailing: String?, actions: CrewHomeActions) {
    val colors = MaterialTheme.colorScheme
    val snippet = post.summary.take(SNIPPET_CHARS)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkBadge(kind, tone = RunkkBadgeTone.Lime)
        Text(
            if (post.isMine) stringResource(R.string.crew_member_me) else post.authorName,
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailing?.let { Text(it, style = RunkkTextStyles.Caption, color = colors.onSurface) }
        if (post.canDelete) {
            RunkkTextLink(
                stringResource(R.string.crew_action_delete),
                onClick = { actions.onDeleteAsk(post) },
                contentDescription = stringResource(R.string.crew_delete_description, post.authorName, snippet)
            )
        }
        if (!post.isMine) {
            RunkkTextLink(
                stringResource(R.string.crew_report),
                onClick = { actions.onReport(post) },
                contentDescription = stringResource(R.string.crew_report_description, post.authorName, snippet)
            )
        }
    }
}

/** "참석 6, 불참 1, 미정 2, 10.24 (금)까지 응답". */
@Composable
private fun AnswerCounts(going: Int, notGoing: Int, undecided: Int, deadline: Instant?) {
    val counts = stringResource(R.string.crew_meetup_counts, going, notGoing, undecided)
    Text(
        deadline?.let { stringResource(R.string.crew_schedule_deadline, counts, scheduleLabel(it)) } ?: counts,
        style = RunkkTextStyles.Caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 일정 응답 단추 셋(참석·불참·미정, FR-145, D-99). 응답할 수 없으면 내 응답 문구만 보인다. */
@Composable
private fun AttendanceButtons(
    post: PostList.Item,
    mine: AttendanceAnswer?,
    canAnswer: Boolean,
    actions: CrewHomeActions
) {
    if (!canAnswer) {
        mine?.let {
            Text(
                stringResource(R.string.crew_attendance_mine, stringResource(it.labelRes())),
                style = RunkkTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        AttendanceAnswer.entries.forEach { answer ->
            AnswerButton(
                stringResource(answer.labelRes()),
                isSelected = mine == answer,
                onClick = { actions.onAttendance(post, answer) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

internal fun AttendanceAnswer.labelRes(): Int = when (this) {
    AttendanceAnswer.GOING -> R.string.crew_attendance_going
    AttendanceAnswer.NOT_GOING -> R.string.crew_attendance_not_going
    AttendanceAnswer.UNDECIDED -> R.string.crew_attendance_undecided
}

/** 하나만 고르는 응답 단추. 선택 상태를 표준 의미로 알린다. */
@Composable
private fun AnswerButton(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val stateText =
        stringResource(if (isSelected) R.string.crew_home_vote_selected else R.string.crew_home_vote_unselected)
    val state = modifier.semantics {
        this.selected = isSelected
        stateDescription = stateText
    }
    if (isSelected) {
        RunkkPrimaryButton(label, onClick = onClick, modifier = state)
    } else {
        RunkkSecondaryButton(label, onClick = onClick, modifier = state)
    }
}

/**
 * 대기실 단추: 열렸을 때만 누를 수 있고, 열리기 전에는 남은 분(1시간 넘게 남으면 "30분 전에 열려요"), 모집 일시가 지났으면 닫혔다고
 * 보인다(서버가 LOBBY_NOT_OPEN·ALREADY_STARTED로 거부하는 때를 미리 막는다).
 */
@Composable
private fun LobbyButton(post: PostList.Item, lobby: LobbyAvailability, actions: CrewHomeActions) {
    val text = lobbyButtonText(lobby)
    val label = post.summary.ifBlank { post.crewRun?.let { crewRunSummary(it) }.orEmpty() }
    val description = stringResource(R.string.crew_home_run_lobby_description, text, label)
    val modifier = Modifier.fillMaxWidth().semantics { contentDescription = description }
    if (lobby == LobbyAvailability.Open) {
        RunkkPrimaryButton(text, onClick = { actions.onLobby(post) }, modifier = modifier)
    } else {
        RunkkSecondaryButton(
            text,
            onClick = { actions.onLobby(post) },
            isEnabled = false,
            modifier = modifier,
            height = 48.dp
        )
    }
}

@Composable
private fun lobbyButtonText(lobby: LobbyAvailability): String = when (lobby) {
    LobbyAvailability.Open -> stringResource(R.string.crew_home_run_lobby)

    LobbyAvailability.Closed -> stringResource(R.string.crew_home_run_lobby_closed)

    is LobbyAvailability.NotYet -> if (lobby.minutesLeft <= LOBBY_COUNTDOWN_MINUTES) {
        stringResource(R.string.crew_home_run_lobby_opens_in, lobby.minutesLeft.toInt())
    } else {
        stringResource(R.string.crew_home_run_lobby_opens_before)
    }
}

/** 지난 크루런 한 줄(와이어 I4): 제목, "크루런, 10.14, 5km, 6명" ›. 인원은 함께 달린(진행 참가) 인원이다. 끝난 크루런은 눌러 결과로 간다. */
@Composable
private fun PastSchedule(post: PostList.Item, actions: CrewHomeActions) {
    val colors = MaterialTheme.colorScheme
    val run = post.crewRun
    val isResult = run?.status == CrewRunStatus.FINISHED
    val caption = when {
        run != null -> listOf(
            stringResource(R.string.crew_schedule_kind_crew_run),
            shortDateLabel(run.scheduledAt),
            RunkkFormat.targetLabel(run.distance),
            if (run.status == CrewRunStatus.CANCELLED) {
                stringResource(R.string.crew_run_status_cancelled)
            } else {
                stringResource(R.string.crew_celebration_people_value, run.finisherCount)
            }
        )

        else -> emptyList()
    }.joinToString(", ")
    val resultDescription = stringResource(R.string.crew_home_run_result_description, post.summary)
    val open: Pair<String, () -> Unit>? = if (isResult) resultDescription to { actions.onResult(post) } else null
    CrewCard(
        modifier = open?.let { (description, onOpen) ->
            Modifier
                .clip(RunkkShapes.Large)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = description }
        } ?: Modifier
    ) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(post.summary, style = RunkkTextStyles.BodyStrong, color = colors.onSurface)
                Text(caption, style = RunkkTextStyles.Caption, color = colors.onSurfaceVariant)
            }
            if (open != null) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        RunkkIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** 이만큼(분) 남았을 때부터 "N분 뒤 열려요"로 센다. */
private const val LOBBY_COUNTDOWN_MINUTES = 60L

/** 삭제·신고 설명에 넣는 글 앞부분 길이. */
private const val SNIPPET_CHARS = 20

/** 누를 수 없는 강조 카드 단추의 진하기. */
private const val DISABLED_ALPHA = 0.6f
