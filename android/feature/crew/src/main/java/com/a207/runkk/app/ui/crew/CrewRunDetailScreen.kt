package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunDetail
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 크루런 일정 상세(피그마 F-13·F-13b·F-13c·F-11d, S15P31A207-297) ----------

/** 일정 상세 경로: 수정 화면에서 돌아오면 다시 읽고, 삭제하면 앞 화면으로 돌아간다. 남의 글 신고는 크루 신고 대화상자를 쓴다. */
@Composable
fun CrewRunDetailRoute(
    key: Routes.CrewRunDetail,
    viewModel: CrewRunDetailViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key.postId) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewRunDetailEvent.Deleted -> navigator.back()
            }
        }
    }
    CrewRunDetailScreen(
        state = state,
        actions = CrewRunDetailActions(
            onBack = navigator::back,
            onRetry = viewModel::refresh,
            onAttend = viewModel::onAttend,
            onCancelAttend = viewModel::onCancelAttend,
            onMenu = viewModel::onMenu,
            onEdit = {
                viewModel.onMenu(false)
                navigator.navigate(Routes.CrewRunEdit(key.postId))
            },
            onDeleteAsk = viewModel::onDeleteAsk,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onReport = {
                viewModel.onMenu(false)
                state.detail?.let { report.onAsk(it.asPostItem()) }
            },
            onMessageShown = viewModel::onMessageShown
        ),
        reportMessage = reportState.message?.asString(),
        onReportMessageShown = report::onMessageShown
    )
    CrewReportDialog(
        reportState,
        CrewReportActions(report::onReason, report::onDetail, report::onSubmit, report::onDismiss)
    )
}

/** 일정 상세 입력 묶음(시험·미리보기에서 상태 없는 화면을 그대로 쓴다). */
data class CrewRunDetailActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAttend: () -> Unit = {},
    val onCancelAttend: () -> Unit = {},
    val onMenu: (Boolean) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDeleteAsk: () -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onReport: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

/**
 * 크루런 상세(F-13): 공통 헤더("크루런 상세" + ⋯), 작성 정보("나"·"2시간 전", 지난 일정은 "종료된 일정"), 제목, 정보 카드(일시 + "매주"
 * 배지, 거리, 참석 수), 내용 상자, 참석자 목록, 하단 "참석하기"(Lime Large)·"참석 취소하기"(White Medium). 지난 일정(F-11d)은 하단 버튼이
 * 없다. ⋯는 작성자에게 수정하기·삭제하기(F-13b), 남에게 신고하기를 보인다.
 */
@Composable
fun CrewRunDetailScreen(
    state: CrewRunDetailUiState,
    actions: CrewRunDetailActions,
    modifier: Modifier = Modifier,
    reportMessage: String? = null,
    onReportMessageShown: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    LaunchedEffect(reportMessage) {
        reportMessage?.let {
            snackbar.showSnackbar(it)
            onReportMessageShown()
        }
    }
    Box(modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.crew_run_detail_title),
                onBack = actions.onBack,
                actions = { if (state.hasMenu) DetailMenu(state, actions) }
            )
            val detail = state.detail
            when {
                detail != null -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DetailBody(detail, state)
                    }
                    if (state.canAnswer) AnswerButton(detail, state.isAnswering, actions)
                }

                state.loadError != null -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    RunkkErrorState(state.loadError.asString(), onRetry = actions.onRetry)
                }

                else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { RunkkLoading() }
            }
        }
        RunkkSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    if (state.isDeleteAsked) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_run_delete_title),
            message = stringResource(R.string.crew_run_delete_message),
            confirmLabel = stringResource(R.string.crew_run_delete_confirm),
            onConfirm = actions.onDeleteConfirm,
            onDismiss = actions.onDeleteDismiss,
            isDestructive = true
        )
    }
}

/** ⋯ 단추와 드롭다운(피그마 RunKK/Menu/모집글 드롭다운: 너비 90, 모서리 16, 테두리 dropdown stroke, 항목 사이 구분선). */
@Composable
private fun DetailMenu(state: CrewRunDetailUiState, actions: CrewRunDetailActions) {
    val detail = state.detail ?: return
    Box {
        CrewMoreButton(stringResource(R.string.crew_run_detail_menu), onClick = { actions.onMenu(true) })
        DropdownMenu(
            expanded = state.isMenuOpen,
            onDismissRequest = { actions.onMenu(false) },
            shape = RoundedCornerShape(16.dp),
            containerColor = RunkkColors.Surface,
            border = BorderStroke(1.dp, RunkkColors.Outline),
            modifier = Modifier.widthIn(min = 90.dp)
        ) {
            val items = buildList {
                if (detail.canEdit) add(R.string.crew_run_detail_edit to actions.onEdit)
                if (detail.canDelete) add(R.string.crew_run_detail_delete to actions.onDeleteAsk)
                if (!detail.isMine) add(R.string.crew_run_detail_report to actions.onReport)
            }
            items.forEachIndexed { index, (label, onClick) ->
                if (index > 0) HorizontalDivider(color = RunkkColors.MenuDivider)
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(label),
                            style = RunkkTextStyles.text(15, lineHeight = 18),
                            color = RunkkColors.TextPrimary
                        )
                    },
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
private fun DetailBody(detail: CrewRunDetail, state: CrewRunDetailUiState) {
    val resources = LocalContext.current.resources
    val author = if (detail.isMine) stringResource(R.string.crew_member_me) else detail.authorName
    val whenText = if (detail.isPast) {
        stringResource(R.string.crew_run_detail_ended)
    } else {
        agoLabel(resources, detail.writtenAt, state.now)
    }
    MetaRow(listOf(MetaItem(RunkkIcons.Person, author), MetaItem(RunkkIcons.Schedule, whenText)))
    Text(
        detail.title,
        style = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 28),
        color = RunkkColors.TextPrimary,
        modifier = Modifier.semantics { heading() }
    )
    InfoCard(detail)
    if (detail.body.isNotBlank()) {
        Text(
            detail.body,
            style = RunkkTextStyles.text(14, lineHeight = 21),
            color = RunkkColors.TextPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(RunkkColors.SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
    Text(
        stringResource(R.string.crew_run_detail_attendees),
        style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18),
        color = RunkkColors.TextPrimary,
        modifier = Modifier.padding(top = 2.dp).semantics { heading() }
    )
    Column {
        detail.attendees.forEach { AttendeeRow(it) }
    }
}

/** 정보 카드(피그마: 테두리 1 divider, 모서리 28, 안쪽 14, 줄 간격 10): 일시(+ "매주" 배지), 거리, 참석 수를 메타 항목으로. */
@Composable
private fun InfoCard(detail: CrewRunDetail) {
    val shape = RoundedCornerShape(28.dp)
    val at = detail.scheduledAt.atZone(ZoneId.systemDefault())
    val date = at.format(DateTimeFormatter.ofPattern(stringResource(R.string.crew_schedule_pattern), Locale.KOREAN))
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, RunkkColors.Border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaRow(listOf(MetaItem(RunkkIcons.CalendarToday, date)))
            if (detail.repeat == CrewRunRepeat.WEEKLY && !detail.isPast) WeeklyBadge()
        }
        MetaRow(
            listOf(
                MetaItem(
                    RunkkIcons.Route,
                    crewRunDistanceLabel(detail.distance),
                    crewRunDistanceSpoken(detail.distance)
                )
            )
        )
        MetaRow(
            listOf(MetaItem(RunkkIcons.Group, stringResource(R.string.crew_schedule_going_people, detail.goingCount)))
        )
    }
}

/** "매주" 배지(피그마: 바탕 surfaceHigh, 모서리 10, 좌우 8·위아래 2, 12 Medium). */
@Composable
private fun WeeklyBadge() {
    Text(
        stringResource(R.string.crew_run_detail_weekly),
        style = RunkkTextStyles.text(12, FontWeight.Medium, lineHeight = 14),
        color = RunkkColors.TextPrimary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 참석자 줄(피그마: 높이 45, 간격 12, 아래 구분선): 아바타 32, 이름(나는 "나" Bold), 작성자면 뒤에 "작성자" 12. */
@Composable
private fun AttendeeRow(attendee: CrewRunDetail.Attendee) {
    val name = if (attendee.isMe) stringResource(R.string.crew_member_me) else attendee.displayName
    val authorTag = stringResource(R.string.crew_run_detail_author)
    val spoken = if (attendee.isAuthor) stringResource(R.string.crew_run_detail_attendee_author, name) else name
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 45.dp)
                .clearAndSetSemantics { contentDescription = spoken },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(RunkkColors.SurfaceHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    RunkkIcons.Person,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                name,
                style = RunkkTextStyles.text(
                    15,
                    if (attendee.isMe) FontWeight.Bold else FontWeight.Normal,
                    lineHeight = 18
                ),
                color = RunkkColors.TextPrimary
            )
            if (attendee.isAuthor) {
                Text(authorTag, style = RunkkTextStyles.text(12, lineHeight = 14), color = RunkkColors.TextSecondary)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
    }
}

/** 하단 버튼(pt12 px20 pb32): 참석 중이면 "참석 취소하기"(White Medium 48), 아니면 "참석하기"(Lime Large 52). */
@Composable
private fun AnswerButton(detail: CrewRunDetail, isBusy: Boolean, actions: CrewRunDetailActions) {
    val modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
    if (detail.isGoing) {
        RunkkButton(
            stringResource(R.string.crew_run_detail_cancel),
            onClick = actions.onCancelAttend,
            style = RunkkButtonStyle.White,
            size = RunkkButtonSize.Medium,
            isLoading = isBusy,
            modifier = modifier
        )
    } else {
        RunkkButton(
            stringResource(R.string.crew_run_detail_attend),
            onClick = actions.onAttend,
            isLoading = isBusy,
            modifier = modifier
        )
    }
}
