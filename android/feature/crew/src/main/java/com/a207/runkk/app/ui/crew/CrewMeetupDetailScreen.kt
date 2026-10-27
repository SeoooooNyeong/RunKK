package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.AttendanceAnswer
import com.a207.runkk.app.domain.crew.Meetup
import com.a207.runkk.app.domain.crew.PostList

@Composable
fun CrewMeetupDetailRoute(
    key: Routes.CrewMeetupDetail,
    viewModel: CrewMeetupDetailViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key.crewId, key.postId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) navigator.back() }
    CrewMeetupDetailScreen(
        state = state,
        actions = MeetupDetailActions(
            onBack = navigator::back,
            onAnswer = viewModel::onAnswer,
            onDeleteAsk = viewModel::onDeleteAsk,
            onReport = { state.post?.let(report::onAsk) },
            onRetry = viewModel::onRetry,
            onMessageShown = viewModel::onMessageShown
        ),
        reportMessage = reportState.message,
        onReportMessageShown = report::onMessageShown
    )
    if (state.isConfirmingDelete) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_home_delete_title),
            message = stringResource(R.string.crew_home_delete_message),
            confirmLabel = stringResource(R.string.crew_action_delete),
            onConfirm = viewModel::onDeleteConfirm,
            onDismiss = viewModel::onDeleteDismiss,
            isDestructive = true
        )
    }
    CrewReportDialog(
        reportState,
        CrewReportActions(report::onReason, report::onDetail, report::onSubmit, report::onDismiss)
    )
}

/** 대면 모임 상세(와이어프레임 M7): 꼬리표와 작성자, 제목, 일시·장소·참석/정원 카드와 막대, 내 응답(참석·불참·미정), 응답 수. */
@Composable
fun CrewMeetupDetailScreen(
    state: CrewMeetupDetailUiState,
    actions: MeetupDetailActions,
    modifier: Modifier = Modifier,
    reportMessage: UiText? = null,
    onReportMessageShown: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val message = (state.message ?: reportMessage)?.asString()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
            onReportMessageShown()
        }
    }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.crew_kind_schedule), onBack = actions.onBack) {
                state.post?.let { post ->
                    MoreMenu(
                        stringResource(R.string.crew_meetup_menu_description),
                        listOfNotNull(
                            MenuEntry(stringResource(R.string.crew_action_delete), actions.onDeleteAsk).takeIf {
                                post.canDelete
                            },
                            MenuEntry(stringResource(R.string.crew_report), actions.onReport).takeIf { !post.isMine }
                        )
                    )
                }
            }
            val post = state.post
            val meetup = post?.meetup
            when {
                post != null && meetup != null -> MeetupBody(state, post, meetup, actions, Modifier.weight(1f))
                state.error != null -> RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
                else -> Box(Modifier.fillMaxWidth().weight(1f)) { RunkkLoading() }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

/** 응답 선택지 순서(참석·불참·미정). */
private val ANSWERS = listOf(AttendanceAnswer.GOING, AttendanceAnswer.NOT_GOING, AttendanceAnswer.UNDECIDED)

@Composable
private fun MeetupBody(
    state: CrewMeetupDetailUiState,
    post: PostList.Item,
    meetup: Meetup,
    actions: MeetupDetailActions,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindTag(stringResource(R.string.crew_schedule_kind_meetup), isFilled = true)
                Text(
                    stringResource(
                        R.string.crew_board_author_time,
                        post.authorName,
                        relativeLabel(post.writtenAt, state.now)
                    ),
                    style = RunkkTextStyles.Caption,
                    color = colors.onSurfaceVariant
                )
            }
            Text(
                post.summary,
                style = RunkkTextStyles.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.semantics {
                    heading()
                }
            )
        }
        CrewCard {
            InfoRow(RunkkIcons.Calendar, deadlineLabel(meetup.scheduledAt))
            InfoRow(null, meetup.placeName)
            InfoRow(
                RunkkIcons.Users,
                meetup.capacity?.let { stringResource(R.string.crew_meetup_going_capacity, meetup.goingCount, it) }
                    ?: stringResource(R.string.crew_run_going_count, meetup.goingCount)
            )
            meetup.capacity?.takeIf { it > 0 }?.let { capacity ->
                RunkkProgressBar(
                    meetup.goingCount.toFloat() / capacity,
                    height = 6.dp,
                    color = colors.primary,
                    trackColor = colors.outlineVariant
                )
            }
        }
        RunkkSegmentRow(
            options = listOf(
                stringResource(R.string.crew_attendance_going),
                stringResource(R.string.crew_attendance_not_going),
                stringResource(R.string.crew_attendance_undecided)
            ),
            selectedIndex = ANSWERS.indexOf(meetup.myAnswer),
            onSelect = { actions.onAnswer(ANSWERS[it]) },
            groupLabel = stringResource(R.string.crew_meetup_my_answer)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.crew_meetup_answers),
                style = RunkkTextStyles.BodyStrong,
                color = colors.onSurface
            )
            Text(
                stringResource(
                    R.string.crew_meetup_answer_counts,
                    meetup.goingCount,
                    meetup.notGoingCount,
                    meetup.undecidedCount
                ),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** 카드 한 줄: 아이콘(없으면 같은 너비 자리)과 글. */
@Composable
private fun InfoRow(icon: ImageVector?, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp)) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(text, style = RunkkTextStyles.Body, color = MaterialTheme.colorScheme.onSurface)
    }
}
