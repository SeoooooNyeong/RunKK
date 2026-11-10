package com.a207.runkk.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.ReportDialogState
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkReportDialog
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.reportDialogTexts
import com.a207.runkk.app.domain.chat.ChatMessage
import com.a207.runkk.app.domain.report.ReportReason
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 크루 홈의 채팅 탭(피그마 F-09, 6.1.14, [임시:D-성-21]). 상단 바가 없다(크루 머리글과 탭 줄은 크루 홈이 그린다). [isVisible](채팅 탭
 * 선택)이 참이고 화면이 보이는 동안(onStart~onStop)만 방을 열어 둔다: 보이면 ChatViewModel.onShown, 사라지면 onHidden(D-103, D-130).
 * 목록 위 끝에 닿거나 「이전 메시지 더 보기」를 누르면 이전 쪽을 읽는다.
 */
@Composable
fun ChatTabContent(
    crewId: Long,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    if (isVisible) {
        LifecycleStartEffect(crewId, viewModel) {
            viewModel.onShown(crewId)
            onStopOrDispose { viewModel.onHidden() }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatRoomScreen(
        state = state,
        actions = ChatActions(
            onDraft = viewModel::onDraft,
            onSend = viewModel::onSend,
            onRetry = viewModel::onRetry,
            onReaction = viewModel::onReaction,
            onLoadOlder = viewModel::onLoadOlder,
            onDeleteAsk = viewModel::onDeleteAsk,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onReportAsk = viewModel::onReportAsk,
            onReportReason = viewModel::onReportReason,
            onReportDetail = viewModel::onReportDetail,
            onReportDismiss = viewModel::onReportDismiss,
            onReportSubmit = viewModel::onReportSubmit,
            onMessageShown = viewModel::onMessageShown,
            onOpenReactors = viewModel::onOpenReactors,
            onReactorTab = viewModel::onReactorTab,
            onCloseReactors = viewModel::onCloseReactors
        ),
        modifier = modifier
    )
}

/** 상태 없는 채팅 탭 화면(피그마 F-09): 메시지 목록(새 메시지가 아래, 날짜 알약), 입력 줄. 위 바·구분선이 없다. */
@Composable
fun ChatRoomScreen(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier.fillMaxSize().background(RunkkColors.Background).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            ChatMessages(state, actions, Modifier.weight(1f))
            ChatComposer(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
    ChatDialogs(state, actions)
}

/**
 * 메시지 목록. 거꾸로 쌓아(reverseLayout) 새 메시지가 아래에 붙고 처음부터 맨 아래가 보인다. 날이 바뀌는 곳에 날짜 줄을 둔다.
 * 위 끝(오래된 쪽)에 닿으면 이전 쪽을 읽고, 보조 기술·키보드 사용자를 위해 같은 일을 하는 단추도 둔다.
 */
@Composable
private fun ChatMessages(state: ChatUiState, actions: ChatActions, modifier: Modifier = Modifier) {
    if (state.messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            RunkkEmptyState(stringResource(R.string.chat_empty_title))
        }
        return
    }
    val listState = rememberLazyListState()
    val newestFirst = remember(state.messages) { state.messages.asReversed() }
    val zone = remember { ZoneId.systemDefault() }
    val atTop by remember {
        derivedStateOf {
            // 첫 측정 전(보이는 항목 없음)에는 위 끝으로 보지 않는다(들어오자마자 이전 쪽을 읽지 않게).
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index
            last != null && info.totalItemsCount > 0 && last >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(listState, state.hasMore) {
        snapshotFlow { atTop }.collect { if (it && state.hasMore) actions.onLoadOlder() }
    }
    // 새 메시지가 오면(맨 아래를 보고 있을 때) 맨 아래를 계속 보인다.
    LaunchedEffect(newestFirst.firstOrNull()?.let { it.messageId ?: it.clientMessageId }) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    LatestIncomingAnnouncement(state.messages)
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        newestFirst.forEachIndexed { index, message ->
            item(key = message.listKey()) { ChatMessageRow(message, actions) }
            // 거꾸로 쌓으므로 그날 첫 메시지 "뒤"에 둔 날짜 줄이 화면에서는 그 위에 보인다.
            val day = message.sentAt.atZone(zone).toLocalDate()
            val olderDay = newestFirst.getOrNull(index + 1)?.sentAt?.atZone(zone)?.toLocalDate()
            if (olderDay != day) item(key = "day:${message.listKey()}") { DaySeparator(day) }
        }
        if (state.hasMore) {
            item(key = "older") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RunkkPillButton(stringResource(R.string.chat_load_older), onClick = actions.onLoadOlder)
                }
            }
        }
    }
}

/** 날짜 알약(피그마 F-09): 가운데 surfaceHigh 알약(r12, 좌우 10, 위아래 4), 11 Regular. 오늘·어제는 말로, 그 밖은 「10월 22일 (수)」. */
@Composable
private fun DaySeparator(day: LocalDate) {
    val pattern = stringResource(R.string.chat_day_pattern)
    val today = LocalDate.now(ZoneId.systemDefault())
    val label = when (day) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = RunkkTextStyles.text(11, lineHeight = 13),
            color = RunkkColors.TextPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(RunkkColors.SurfaceHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 삭제 확인(F-09b, W1 RunkkConfirmDialog), 신고 시트(F-10b, W1 RunkkReportDialog), 반응한 사람 시트(F-09d). */
@Composable
private fun ChatDialogs(state: ChatUiState, actions: ChatActions) {
    if (state.confirmDelete != null) {
        RunkkConfirmDialog(
            title = stringResource(R.string.chat_delete_title),
            message = stringResource(R.string.chat_delete_message),
            confirmLabel = stringResource(R.string.chat_delete),
            onConfirm = actions.onDeleteConfirm,
            onDismiss = actions.onDeleteDismiss,
            isDestructive = true
        )
    }
    if (state.reportTarget != null) {
        val reasons = ReportReason.entries
        RunkkReportDialog(
            texts = reportDialogTexts(
                title = stringResource(R.string.chat_report_title),
                detailLabel = stringResource(R.string.chat_report_detail_label),
                subtitle = stringResource(R.string.chat_report_subtitle, state.reportTarget.authorName)
            ),
            state = ReportDialogState(
                selectedIndex = state.reportReason?.let(reasons::indexOf),
                detail = state.reportDetail,
                isDetailMissing = state.isReportDetailMissing,
                isSubmitEnabled = state.canSubmitReport,
                detailMax = ChatUiState.REPORT_DETAIL_MAX
            ),
            onSelect = { actions.onReportReason(reasons[it]) },
            onDetail = actions.onReportDetail,
            onSubmit = actions.onReportSubmit,
            onDismiss = actions.onReportDismiss
        )
    }
    if (state.reactorsTarget != null) ReactorsSheet(state, actions)
}

/**
 * 새로 온 남의 메시지를 보조 기술에 한 번 알린다(WCAG 4.1.3). 목록 전체를 라이브 영역으로 두면 통째로 읽히므로, 가장 최근 남의
 * 메시지 한 건만 보이지 않는 칸에 둔다.
 */
@Composable
private fun LatestIncomingAnnouncement(messages: List<ChatMessage>) {
    val latest = messages.lastOrNull { !it.isMine && !it.isRemoved && it.messageId != null } ?: return
    val text = stringResource(R.string.chat_new_message, latest.authorName, latest.content)
    Box(
        Modifier.size(1.dp).semantics {
            contentDescription = text
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/**
 * 목록 항목 키(D-180 (5), AN5-D-16). 내가 보낸 메시지는 낙관적 항목일 때부터 확정 뒤까지 clientMessageId를 가지므로 그것을 먼저 써서
 * 서버 확정으로 messageId가 붙어도 키가 바뀌지 않는다(항목을 다시 만들지 않아 깜빡임·리액션 줄 상태 손실이 없다).
 */
internal fun ChatMessage.listKey(): String = clientMessageId?.let { "c:$it" } ?: "m:$messageId"
