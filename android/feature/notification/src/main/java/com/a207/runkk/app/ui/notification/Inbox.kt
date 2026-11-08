package com.a207.runkk.app.ui.notification

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.domain.notification.Inbox
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** 최신순 알림을 오늘·이번 주(월요일 시작)·그 전으로 묶는다(FR-178). */
internal fun groupInbox(items: List<Inbox.Item>, clock: Clock): List<InboxGroup> {
    val today = LocalDate.now(clock)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sorted = items.sortedByDescending { it.receivedAt }
    fun day(item: Inbox.Item) = item.receivedAt.atZone(clock.zone).toLocalDate()
    return listOf(
        InboxGroup(R.string.notification_group_today, sorted.filter { day(it) == today }),
        InboxGroup(
            R.string.notification_group_this_week,
            sorted.filter {
                day(it) < today &&
                    !day(it).isBefore(weekStart)
            }
        ),
        InboxGroup(R.string.notification_group_earlier, sorted.filter { day(it).isBefore(weekStart) })
    ).filter { it.items.isNotEmpty() }
}

@Composable
fun InboxRoute(viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val uriHandler = LocalUriHandler.current
    // 이벤트 목록에서 돌아오면 이벤트 항목의 새 이벤트 점을 다시 센다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.Navigate -> navigator.navigate(event.key)

                // 바깥 브라우저가 없으면 알림함에 머문다
                is InboxEvent.OpenLink -> runCatching { uriHandler.openUri(event.url) }
            }
        }
    }
    InboxScreen(
        state,
        onBack = navigator::back,
        onItem = viewModel::onItemTapped,
        onRetry = viewModel::refresh,
        onMessageShown = viewModel::onMessageShown,
        onEvents = { navigator.navigate(Routes.EVENTS) }
    )
}

@Composable
fun InboxScreen(
    state: InboxUiState,
    onBack: () -> Unit,
    onItem: (Inbox.Item) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageShown: () -> Unit = {},
    onEvents: () -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        InboxContent(state, onBack, onItem, onRetry, onEvents)
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
private fun InboxContent(
    state: InboxUiState,
    onBack: () -> Unit,
    onItem: (Inbox.Item) -> Unit,
    onRetry: () -> Unit,
    onEvents: () -> Unit
) {
    // 1차 배포에서 닫은 기능(크루, 아바타)의 알림은 목록에서 뺀다
    val scope = LocalReleaseScope.current
    val groups = remember(state.groups, scope) {
        state.groups.map { group -> group.copy(items = group.items.filter { InboxLabels.isVisibleIn(it.type, scope) }) }
            .filter { it.items.isNotEmpty() }
    }
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.notification_inbox_title), onBack = onBack)
        state.eventSummary?.let {
            EventInboxRow(
                it,
                onClick = onEvents,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
            )
        }
        when {
            state.isLoading && groups.isEmpty() -> RunkkLoading(modifier = Modifier.weight(1f))

            state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)

            groups.isEmpty() -> RunkkEmptyState(stringResource(R.string.notification_empty_title))

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)
            ) {
                // 보드(F7)는 묶음 제목 없이 최신순 한 목록이다.
                val items = groups.flatMap { it.items }
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    InboxRow(item, state, onClick = { onItem(item) })
                    if (index < items.lastIndex) HorizontalDivider(color = RunkkColors.Border)
                }
            }
        }
    }
}

/** 알림 한 줄: 안 읽음 점, 종류 라벨, 본문(안 읽음은 굵게), 오른쪽 상대 시간. */
@Composable
private fun InboxRow(item: Inbox.Item, state: InboxUiState, onClick: () -> Unit) {
    val readState = stringResource(if (item.isRead) R.string.notification_read else R.string.notification_unread)
    val resources = LocalContext.current.resources
    val time = remember(item.receivedAt, state.now, state.zone) {
        inboxTime(resources, item.receivedAt, state.now, state.zone)
    }
    val label = InboxLabels.of(item.type)?.let { stringResource(it) } ?: item.title
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) { stateDescription = readState },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (item.isRead) Color.Transparent else RunkkColors.Lime)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            Text(
                item.body,
                style = RunkkTextStyles.text(14, if (item.isRead) FontWeight.Normal else FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
        }
        Text(time, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

@Preview(name = "알림함", widthDp = 390, heightDp = 844)
@Composable
private fun InboxPreview() {
    val now = Instant.parse("2026-10-21T03:00:00Z")
    val clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))
    RunkkTheme(reduceMotion = true) {
        InboxScreen(
            InboxUiState(
                isLoading = false,
                groups = groupInbox(
                    listOf(
                        Inbox.Item(
                            1,
                            "CREW_RUN_SOON",
                            "크루런 곧 시작",
                            "새벽크루 크루런이 10분 뒤 시작해요",
                            now.minusSeconds(600),
                            false,
                            "CREW_RUN_RECRUIT",
                            "31"
                        ),
                        Inbox.Item(
                            2,
                            "PLAN_MISSED",
                            "오늘 계획을 놓쳤어요",
                            "보충 거리 2km를 추천해요",
                            now.minusSeconds(172_800),
                            true,
                            null
                        )
                    ),
                    clock
                ),
                now = now,
                zone = clock.zone
            ),
            onBack = {},
            onItem = {},
            onRetry = {}
        )
    }
}
