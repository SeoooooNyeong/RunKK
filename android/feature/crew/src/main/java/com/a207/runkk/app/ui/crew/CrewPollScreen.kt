package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.PollResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CrewPollRoute(
    key: Routes.CrewPoll,
    viewModel: CrewPollDetailViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key.crewId, key.postId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) navigator.back() }
    CrewPollScreen(
        state = state,
        actions = PollDetailActions(
            onBack = navigator::back,
            onRespond = viewModel::onRespond,
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

/**
 * 투표 화면(와이어프레임 M3): 위 바(투표, ⋯ 삭제·신고), 작성자와 시각, 질문, 마감 일시와 마감 임박, 응답자 수, 선택지 카드(라디오,
 * 수, 막대, 응답자 이름), 내 선택. 마감 전이면 카드를 눌러 응답하거나 바꾼다(FR-207).
 */
@Composable
fun CrewPollScreen(
    state: CrewPollDetailUiState,
    actions: PollDetailActions,
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
            RunkkTopBar(title = stringResource(R.string.crew_kind_poll), onBack = actions.onBack) {
                state.post?.let { post ->
                    MoreMenu(
                        stringResource(R.string.crew_poll_menu_description),
                        listOfNotNull(
                            MenuEntry(stringResource(R.string.crew_action_delete), actions.onDeleteAsk).takeIf {
                                post.canDelete
                            },
                            MenuEntry(stringResource(R.string.crew_report), actions.onReport).takeIf { !post.isMine }
                        )
                    )
                }
            }
            val result = state.result
            when {
                result != null -> PollBody(state, result, actions, Modifier.weight(1f))
                state.error != null -> RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
                else -> Box(Modifier.fillMaxWidth().weight(1f)) { RunkkLoading() }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
    }
}

@Composable
private fun PollBody(state: CrewPollDetailUiState, result: PollResult, actions: PollDetailActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.post?.let { post ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorAvatar(post.authorName)
                Text(post.authorName, style = RunkkTextStyles.BodyStrong, color = colors.onSurface)
                Text(
                    relativeLabel(post.writtenAt, state.now),
                    style = RunkkTextStyles.Caption,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Text(
            result.question,
            style = RunkkTextStyles.CardTitle,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.crew_poll_until, pollDeadline(result.closesAt)),
                style = RunkkTextStyles.Body,
                color = colors.onSurfaceVariant
            )
            when {
                result.isClosed -> RunkkBadge(
                    stringResource(R.string.crew_poll_closed_badge),
                    tone = RunkkBadgeTone.Muted
                )

                state.isClosingSoon -> RunkkBadge(
                    stringResource(R.string.crew_poll_closing_soon),
                    tone = RunkkBadgeTone.Lime
                )
            }
        }
        Text(
            stringResource(R.string.crew_poll_respondent_count, state.respondentCount),
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.options.forEach { option -> OptionCard(option, result, state, actions) }
        }
        result.options.firstOrNull { it.optionNo == result.myOptionNo }?.let { mine ->
            Text(
                stringResource(R.string.crew_poll_my_choice_line, mine.label),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Small)
                    .background(colors.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

/** 선택지 카드: 라디오, 이름, 수, 막대, 응답자. 마감 전이면 카드 전체가 라디오 단추로 눌린다. */
@Composable
private fun OptionCard(
    option: PollResult.Option,
    result: PollResult,
    state: CrewPollDetailUiState,
    actions: PollDetailActions
) {
    val colors = MaterialTheme.colorScheme
    val isMine = result.myOptionNo == option.optionNo
    val canRespond = !result.isClosed && !state.isResponding
    val names = option.respondents.joinToString(", ") { it.displayName }
    val description = listOfNotNull(
        stringResource(R.string.crew_poll_option_description, option.label, option.count),
        stringResource(R.string.crew_poll_my_choice).takeIf { isMine },
        names.takeIf { it.isNotBlank() }
    ).joinToString(". ")
    val closedState = stringResource(R.string.crew_poll_closed_badge).takeIf { result.isClosed }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Small)
            .background(if (isMine) colors.primaryContainer else colors.surface)
            .border(
                if (isMine) 2.dp else 1.dp,
                if (isMine) colors.primary else colors.outlineVariant,
                RunkkShapes.Small
            )
            // 마감된 투표도 결과를 읽을 수 있게 켜 두고 누름만 막는다("비활성"으로 읽히지 않게).
            .selectable(selected = isMine, role = Role.RadioButton, onClick = {
                if (canRespond) actions.onRespond(option.optionNo)
            })
            .heightIn(min = 48.dp)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                closedState?.let { stateDescription = it }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(
                selected = isMine,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = colors.primary, unselectedColor = colors.outline)
            )
            Text(
                option.label,
                style = if (isMine) RunkkTextStyles.BodyStrong else RunkkTextStyles.Body,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_poll_option_count, option.count),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
        val total = state.respondentCount
        RunkkProgressBar(
            if (total == 0) 0f else option.count.toFloat() / total,
            height = 6.dp,
            color = colors.primary,
            trackColor = colors.outlineVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            option.respondents.take(AVATARS_MAX).forEach { AuthorAvatar(it.displayName, size = 24) }
            Text(
                names.ifBlank { stringResource(R.string.crew_poll_no_respondent) },
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** "10.25 21:00". 틀은 문자열 리소스(crew_poll_deadline_pattern)다. */
@Composable
private fun pollDeadline(at: Instant): String {
    val pattern = LocalContext.current.resources.getString(R.string.crew_poll_deadline_pattern)
    return at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
}

/** 선택지마다 보이는 응답자 아바타 수. */
private const val AVATARS_MAX = 3
