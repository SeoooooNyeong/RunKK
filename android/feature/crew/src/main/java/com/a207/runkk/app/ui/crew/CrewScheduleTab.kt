package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
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
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant

/** 일정 종류 선택지(FR-132): 크루런·대면 모임. */
internal val SCHEDULE_KINDS: List<PostList.Kind> = listOf(PostList.Kind.CREW_RUN, PostList.Kind.MEETUP)

/**
 * 일정 탭(와이어 I4, FR-132, FR-133, FR-145, FR-147): "+ 일정 만들기", 크루런과 대면 모임을 한 목록(가까운 일시순)으로, 아래에
 * "지난 일정". 크루런은 대기실 단추, 대면 모임은 정원과 장소를 보이고 둘 다 참석·불참·미정으로 응답한다.
 */
internal fun LazyListScope.scheduleTabItems(state: CrewHomeUiState, actions: CrewHomeActions) {
    val (upcoming, past) = splitSchedules(state)
    item(key = "schedule:head") {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RunkkPillButton(
                    stringResource(R.string.crew_schedule_write),
                    onClick = actions.onWriteSchedule,
                    leadingIcon = RunkkIcons.Plus
                )
            }
            when {
                state.crewRuns == null && state.meetups == null -> TabLoading(state.isLoading)
                upcoming.isEmpty() && past.isEmpty() -> RunkkEmptyState(stringResource(R.string.crew_schedule_empty))
                else -> Unit
            }
        }
    }
    items(upcoming, key = { "schedule:${it.postId}" }) { post ->
        if (post.kind == PostList.Kind.MEETUP) {
            MeetupPost(post, actions)
        } else {
            CrewRunPost(post, state.lobbyEntries[post.postId], actions)
        }
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

/**
 * 크루런과 대면 모임을 다가오는 일정(가까운 일시순)과 지난 일정(최근순)으로 나눈다. 끝났거나 취소된 크루런, [CrewHomeUiState.scheduleNow]가
 * 지난 대면 모임이 지난 일정이다.
 */
internal fun splitSchedules(state: CrewHomeUiState): Pair<List<PostList.Item>, List<PostList.Item>> {
    val all = state.crewRuns?.posts.orEmpty() + state.meetups?.posts.orEmpty()
    val (past, upcoming) = all.partition { isPast(it, state.scheduleNow) }
    return upcoming.sortedBy { scheduledAt(it) } to past.sortedByDescending { scheduledAt(it) }
}

private fun scheduledAt(post: PostList.Item): Instant =
    post.crewRun?.scheduledAt ?: post.meetup?.scheduledAt ?: post.writtenAt

private fun isPast(post: PostList.Item, now: Instant?): Boolean {
    val status = post.crewRun?.status
    if (status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED) return true
    return post.kind == PostList.Kind.MEETUP && now != null && post.meetup?.scheduledAt?.isBefore(now) == true
}

/** 크루런 일정 카드(와이어 I4): 종류, 참석 수, 제목, 일시와 거리, 응답 수와 응답 마감, 응답 단추, 대기실 단추. */
@Composable
private fun CrewRunPost(post: PostList.Item, lobby: LobbyAvailability?, actions: CrewHomeActions) {
    val run = post.crewRun
    val entry = CrewRunEntry.of(run?.status, lobby)
    val colors = MaterialTheme.colorScheme
    CrewCard {
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
}

/**
 * 대면 모임 일정 카드(와이어 I4): 종류, 정원, 제목 ›(대면 모임 상세 M7로), 일시와 장소, 응답 수, 응답 단추. 정원이 차면 서버가 새 참석을
 * 거부한다.
 */
@Composable
private fun MeetupPost(post: PostList.Item, actions: CrewHomeActions) {
    val meetup = post.meetup
    val colors = MaterialTheme.colorScheme
    CrewCard {
        ScheduleHeader(
            post,
            kind = stringResource(R.string.crew_schedule_kind_meetup),
            trailing = meetup?.let { capacityLabel(it) },
            actions = actions
        )
        MeetupTitle(post, onClick = { actions.onMeetup(post) })
        if (meetup != null) {
            Text(
                listOf(scheduleLabel(meetup.scheduledAt), meetup.placeName).joinToString(", "),
                style = RunkkTextStyles.Body,
                color = colors.onSurface
            )
            AnswerCounts(meetup.goingCount, meetup.notGoingCount, meetup.undecidedCount, deadline = null)
        }
        AttendanceButtons(post, meetup?.myAnswer, canAnswer = true, actions = actions)
    }
}

/** 대면 모임 제목과 ›. 누르면 대면 모임 상세(M7)로 간다. */
@Composable
private fun MeetupTitle(post: PostList.Item, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = post.summary.ifBlank { stringResource(R.string.crew_schedule_kind_meetup) }
    val description = stringResource(R.string.crew_meetup_open_description, title)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = RunkkTextStyles.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** "정원 6/8명"(정원이 없으면 크루 정원). */
@Composable
private fun capacityLabel(meetup: Meetup): String =
    stringResource(R.string.crew_meetup_capacity_of, meetup.goingCount, meetup.capacity ?: CREW_MAX)

/** 일정 카드 윗줄: 종류 배지, 작성자(내 글은 "나"), 참석 수나 정원, 삭제(지울 수 있으면), 신고(남의 글). */
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

/** 지난 일정 한 줄(와이어 I4): 제목, "크루런, 10.14, 5km, 6명" ›. 끝난 크루런은 눌러 결과로, 대면 모임은 상세(M7)로 간다. */
@Composable
private fun PastSchedule(post: PostList.Item, actions: CrewHomeActions) {
    val colors = MaterialTheme.colorScheme
    val run = post.crewRun
    val meetup = post.meetup
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

        meetup != null -> listOf(
            stringResource(R.string.crew_schedule_kind_meetup),
            shortDateLabel(meetup.scheduledAt),
            meetup.placeName
        )

        else -> emptyList()
    }.joinToString(", ")
    val resultDescription = stringResource(R.string.crew_home_run_result_description, post.summary)
    val meetupDescription = stringResource(R.string.crew_meetup_open_description, post.summary)
    val open: Pair<String, () -> Unit>? = when {
        isResult -> resultDescription to { actions.onResult(post) }
        meetup != null -> meetupDescription to { actions.onMeetup(post) }
        else -> null
    }
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
