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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkChevronLink
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkStatusChip
import com.a207.runkk.app.core.designsystem.RunkkStatusChipTone
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
 * 크루런 탭(피그마 F-11 새 디자인, FR-133, FR-145, FR-147): 위에 "+ 크루런 모집하기" 칩과 "지난 크루런 ›"(지난 크루런 화면 F-11b), 가장
 * 가까운 크루런 하나를 보라 강조 카드로(남은 시간, 대기실 입장), 나머지는 이번주·다음주·이후 묶음의 날짜 줄(오른쪽 참석 칩). 끝났거나
 * 취소된 크루런은 이 탭에 두지 않고 지난 크루런 화면이 보인다. 제목이나 줄을 누르면 일정 상세(F-13)로 가고, 칩을 누르면 참석하기·참석
 * 취소(응답을 거둔다)를 한다.
 */
internal fun LazyListScope.scheduleTabItems(state: CrewHomeUiState, actions: CrewHomeActions) {
    val upcoming = upcomingSchedules(state)
    val now = state.scheduleNow ?: Instant.now()
    val hero = upcoming.firstOrNull()
    item(key = "schedule:head") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScheduleTopRow(actions)
            when {
                state.crewRuns == null -> TabLoading(state.isLoading)
                upcoming.isEmpty() -> RunkkEmptyState(stringResource(R.string.crew_schedule_empty))
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
}

/**
 * 다가오는 크루런(가까운 일시순, 같은 일시는 글 ID순, 설계 05-32 [임시:FR-성-365]). 끝났거나 취소된 크루런은 지난 크루런 화면이 보인다.
 * 새 크루런은 이 순서의 제자리에 들어가고 「새로」 표식은 없다(U-35).
 */
internal fun upcomingSchedules(state: CrewHomeUiState): List<PostList.Item> = state.crewRuns?.posts.orEmpty()
    .filter { it.kind == PostList.Kind.CREW_RUN && !isPast(it) }
    .distinctBy { it.postId }
    .sortedWith(compareBy<PostList.Item>({ scheduledAt(it) }, { it.postId }))

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

/** 크루런 제목(W6 서버 title, 옛 서버면 목록 본문). */
private fun runTitle(post: PostList.Item): String = post.crewRun?.title?.takeIf { it.isNotBlank() } ?: post.summary

/**
 * 위쪽 줄(피그마 F-11): 왼쪽 "+ 크루런 모집하기" 칩(바탕 surface, 테두리 없음, 높이 28, 모서리 18, plus 12, 13 Regular), 오른쪽 늘 보이는
 * "지난 크루런 ›"(13 Medium + › 16 textSecondary).
 */
@Composable
private fun ScheduleTopRow(actions: CrewHomeActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = actions.onWriteSchedule),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RunkkColors.Surface)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    RunkkIcons.Plus,
                    contentDescription = null,
                    tint = RunkkColors.TextPrimary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    stringResource(R.string.crew_schedule_write),
                    style = RunkkTextStyles.text(13, lineHeight = 16),
                    color = RunkkColors.TextPrimary
                )
            }
        }
        Box(Modifier.weight(1f))
        RunkkChevronLink(
            stringResource(R.string.crew_schedule_past),
            onClick = actions.onPastRuns,
            modifier = Modifier.padding(end = 6.dp),
            style = RunkkTextStyles.text(13, FontWeight.Medium, lineHeight = 16),
            chevronColor = RunkkColors.TextSecondary
        )
    }
}

/**
 * 가장 가까운 크루런(보라 강조 카드): 날짜·시각("오늘 …"), "참석 n명", 제목 ›(일정 상세), 남은 시간 "29:45 후 시작"(하루 넘게 남으면
 * "n일 후 시작"), 대기실 입장 단추. 대기실이 아직 안 열렸거나 닫혔으면 단추를 누를 수 없고 기존 안내 문구를 보인다.
 */
@Composable
private fun HeroRun(post: PostList.Item, lobby: LobbyAvailability?, now: Instant, actions: CrewHomeActions) {
    val run = post.crewRun ?: return
    val entry = CrewRunEntry.of(run.status, lobby)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RunkkColors.Violet)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heroDateLabel(run.scheduledAt, now),
                style = RunkkTextStyles.text(12),
                color = RunkkColors.OnSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_schedule_going_people, run.goingCount),
                style = RunkkTextStyles.text(12),
                color = RunkkColors.OnSecondary
            )
        }
        RunTitle(
            runTitle(post).ifBlank { crewRunSummary(run) },
            style = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 24),
            color = RunkkColors.OnSecondary,
            chevronColor = RunkkColors.OnSecondary,
            onClick = { actions.onRunOpen(post) }
        )
        if (run.status == CrewRunStatus.RUNNING) {
            // 달리는 크루런: 남은 시간 대신 「달리는 중」, 대기실 대신 「중간 참여하기」(설계 05-32 흐름 2, [임시:D-성-884]).
            RunkkStatusChip(stringResource(R.string.crew_home_run_running), RunkkStatusChipTone.Lime)
            HeroJoinButton(post, actions)
        } else {
            StartsIn(Duration.between(now, run.scheduledAt))
            entry.lobby?.let { HeroLobbyButton(post, it, actions) }
        }
    }
}

/** 강조 카드의 흰 알약 「중간 참여하기」: 끝나기 전까지 크루원 누구나 참여한다(종료 뒤면 서버가 거부한다). */
@Composable
private fun HeroJoinButton(post: PostList.Item, actions: CrewHomeActions) {
    val text = stringResource(R.string.crew_home_run_join)
    val label = runTitle(post).ifBlank { post.crewRun?.let { crewRunSummary(it) }.orEmpty() }
    val description = stringResource(R.string.crew_home_run_join_description, text, label)
    RunkkButton(
        text,
        onClick = { actions.onJoinRun(post) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        style = RunkkButtonStyle.White,
        size = RunkkButtonSize.Medium
    )
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
        Text(big, style = RunkkTextStyles.text(28, FontWeight.Bold), color = RunkkColors.OnSecondary)
        Text(
            stringResource(R.string.crew_schedule_starts_in),
            style = RunkkTextStyles.Label,
            color = RunkkColors.OnSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}

/** 강조 카드의 흰 알약 단추(피그마 White 48): 열렸으면 "대기실 입장하기", 아니면 누를 수 없고 기존 안내 문구. */
@Composable
private fun HeroLobbyButton(post: PostList.Item, lobby: LobbyAvailability, actions: CrewHomeActions) {
    val text = lobbyButtonText(lobby)
    val label = runTitle(post).ifBlank { post.crewRun?.let { crewRunSummary(it) }.orEmpty() }
    val description = stringResource(R.string.crew_home_run_lobby_description, text, label)
    RunkkButton(
        text,
        onClick = { actions.onLobby(post) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        style = RunkkButtonStyle.White,
        size = RunkkButtonSize.Medium,
        isEnabled = lobby == LobbyAvailability.Open
    )
}

/** "오늘 10.21 (수) 19:30"(오늘이 아니면 날짜·시각만). */
@Composable
private fun heroDateLabel(at: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val label = scheduleLabel(at)
    val isToday = at.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()
    return if (isToday) stringResource(R.string.crew_schedule_today, label) else label
}

/** 제목과 ›. 누르면 일정 상세(F-13)로 간다. */
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
        Icon(RunkkIcons.ChevronRight, contentDescription = null, tint = chevronColor, modifier = Modifier.size(16.dp))
    }
}

/** 묶음 하나: "이번주 2" 머리글과 날짜 줄들(줄 아래 구분선 divider). */
@Composable
private fun ScheduleGroupSection(group: ScheduleGroup, posts: List<PostList.Item>, actions: CrewHomeActions) {
    val label = stringResource(group.labelRes)
    val spoken = stringResource(R.string.crew_schedule_group_spoken, label, posts.size)
    Column(Modifier.fillMaxWidth()) {
        RunkkSectionHeader(
            label,
            modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).clearAndSetSemantics {
                heading()
                contentDescription = spoken
            },
            count = posts.size.toString()
        )
        posts.forEach { post ->
            ScheduleRow(post, actions)
            HorizontalDivider(color = RunkkColors.Border)
        }
    }
}

/** 날짜 줄: 일·요일, 제목 ›(일정 상세), 메타 줄(시각, 거리), 오른쪽 참석 칩(내가 참석이면 채운 칩). */
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
                runTitle(post).ifBlank { crewRunSummary(run) },
                style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18),
                color = RunkkColors.TextPrimary,
                chevronColor = RunkkColors.TextSecondary,
                onClick = { actions.onRunOpen(post) }
            )
            MetaRow(
                listOf(
                    MetaItem(RunkkIcons.Schedule, time),
                    MetaItem(RunkkIcons.Route, crewRunDistanceLabel(run.distance), crewRunDistanceSpoken(run.distance))
                )
            )
        }
        GoingChip(post, run.goingCount, isGoing = run.myAnswer == AttendanceAnswer.GOING, actions)
    }
}

/**
 * 오른쪽 참석 칩 "참석 n"(피그마 RunKK chip 74×34): 내가 참석이면 채운 칩(respchip 바탕, 13 Bold 흰 글자), 아니면 외곽선(모서리 8, 14 Medium).
 * 응답할 수 있을 때(투표 중) 누르면 참석하기, 이미 참석이면 참석 취소(응답을 거둔다)다.
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
                Modifier.background(RunkkColors.ChipSelected)
            } else {
                Modifier.border(1.dp, RunkkColors.Border, shape)
            }
        )
    val toggle = if (canVote) {
        Modifier.toggleable(value = isGoing, role = Role.Switch) { on ->
            if (on) actions.onAttendance(post, AttendanceAnswer.GOING) else actions.onAttendanceWithdraw(post)
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
            style = if (isGoing) {
                RunkkTextStyles.text(
                    13,
                    FontWeight.Bold
                )
            } else {
                RunkkTextStyles.text(14, FontWeight.Medium)
            },
            color = if (isGoing) RunkkColors.OnChipSelected else RunkkColors.TextSecondary
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

/** 이만큼(분) 남았을 때부터 "N분 뒤 열려요"로 센다. */
private const val LOBBY_COUNTDOWN_MINUTES = 60L
