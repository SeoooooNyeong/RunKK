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
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant

/** 일정 쓰기 화면의 종류 선택지(FR-132): 크루런·대면 모임. 크루 홈 크루런 탭은 크루런만 보인다. */
internal val SCHEDULE_KINDS: List<PostList.Kind> = listOf(PostList.Kind.CREW_RUN, PostList.Kind.MEETUP)

/**
 * 크루런 탭(피그마 F-11, FR-133, FR-145, FR-147): "+ 크루런 모집하기", 다가오는 크루런(가까운 일시순), 아래에 "지난 크루런".
 * 크루런마다 참석·불참·미정 응답과 대기실 단추를 보인다.
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
                state.crewRuns == null -> TabLoading(state.isLoading)
                upcoming.isEmpty() && past.isEmpty() -> RunkkEmptyState(stringResource(R.string.crew_schedule_empty))
                else -> Unit
            }
        }
    }
    items(upcoming, key = { "schedule:${it.postId}" }) { post ->
        CrewRunPost(post, state.lobbyEntries[post.postId], actions)
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

/** 크루런을 다가오는 것(가까운 일시순)과 지난 것(최근순)으로 나눈다. 끝났거나 취소된 크루런이 지난 크루런이다. */
internal fun splitSchedules(state: CrewHomeUiState): Pair<List<PostList.Item>, List<PostList.Item>> {
    val all = state.crewRuns?.posts.orEmpty().filter { it.kind == PostList.Kind.CREW_RUN }
    val (past, upcoming) = all.partition { isPast(it) }
    return upcoming.sortedBy { scheduledAt(it) } to past.sortedByDescending { scheduledAt(it) }
}

private fun scheduledAt(post: PostList.Item): Instant = post.crewRun?.scheduledAt ?: post.writtenAt

private fun isPast(post: PostList.Item): Boolean {
    val status = post.crewRun?.status
    return status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED
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

/** 지난 크루런 한 줄(와이어 I4): 제목, "크루런, 10.14, 5km, 6명" ›. 끝난 크루런은 눌러 결과로 간다. */
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
