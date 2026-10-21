package com.a207.runkk.app.ui.notification

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.FeatureEntry
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.route
import com.a207.runkk.app.domain.notification.Inbox
import com.a207.runkk.app.domain.notification.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 알림 묶음(오늘·이번 주·그 전). */
data class InboxGroup(@StringRes val titleRes: Int, val items: List<Inbox.Item>)

data class InboxUiState(
    val loading: Boolean = true,
    val groups: List<InboxGroup> = emptyList(),
    val error: UiText? = null,
    val message: UiText? = null
) {
    val unreadCount: Int get() = groups.sumOf { g -> g.items.count { !it.isRead } }
}

sealed interface InboxEvent {
    data class Navigate(val route: String) : InboxEvent
}

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

/** 알림함(FR-175·FR-178): NotificationRepository.inbox·open. 누르면 읽음으로 하고 이동 경로(core.navigation 이름)로 간다. */
@HiltViewModel
class InboxViewModel @Inject constructor(private val notifications: NotificationRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private val _events = Channel<InboxEvent>(Channel.BUFFERED)
    val events: Flow<InboxEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = notifications.inbox()) {
                is Outcome.Success -> _state.update {
                    it.copy(loading = false, groups = groupInbox(result.value.items, clock))
                }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun markRead(id: Long, read: Boolean) = _state.update { s ->
        s.copy(
            groups = s.groups.map { g ->
                g.copy(
                    items = g.items.map {
                        if (it.id ==
                            id
                        ) {
                            it.copy(isRead = read)
                        } else {
                            it
                        }
                    }
                )
            }
        )
    }

    fun onItemTapped(item: Inbox.Item) {
        if (!item.isRead) {
            markRead(item.id, read = true)
            viewModelScope.launch {
                val opened = notifications.open(item.id)
                if (opened is Outcome.Failure) {
                    markRead(item.id, read = false)
                    _state.update { it.copy(message = RunkkErrorText.of(opened.error)) }
                }
            }
        }
        item.route?.let { _events.trySend(InboxEvent.Navigate(it)) }
    }
}

@Composable
fun InboxRoute(viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is InboxEvent.Navigate -> navigator.navigate(event.route)
            }
        }
    }
    InboxScreen(
        state,
        onBack = navigator::back,
        onItem = viewModel::onItemTapped,
        onRetry = viewModel::refresh,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun InboxScreen(
    state: InboxUiState,
    onBack: () -> Unit,
    onItem: (Inbox.Item) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onMessageShown: () -> Unit = {}
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
        InboxContent(state, onBack, onItem, onRetry)
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

@Composable
private fun InboxContent(state: InboxUiState, onBack: () -> Unit, onItem: (Inbox.Item) -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.notification_inbox_title), onBack = onBack)
        when {
            state.loading && state.groups.isEmpty() -> RunkkLoading(modifier = Modifier.weight(1f))

            state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)

            state.groups.isEmpty() -> RunkkEmptyState(
                stringResource(R.string.notification_empty_title),
                message = stringResource(R.string.notification_empty_message)
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.groups.forEach { group ->
                    item(key = "title-${group.titleRes}") {
                        RunkkSectionTitle(
                            stringResource(group.titleRes),
                            modifier = Modifier.padding(top = 16.dp, start = 8.dp)
                        )
                    }
                    items(group.items, key = { it.id }) { item -> InboxRow(item, onClick = { onItem(item) }) }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(item: Inbox.Item, onClick: () -> Unit) {
    val readState = stringResource(if (item.isRead) R.string.notification_read else R.string.notification_unread)
    val timePattern = stringResource(R.string.notification_time_pattern)
    val time = remember(timePattern) { DateTimeFormatter.ofPattern(timePattern, Locale.KOREAN) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { stateDescription = readState },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.padding(
                top = 6.dp
            ).size(8.dp).clip(CircleShape).background(if (item.isRead) RunkkColors.Border else RunkkColors.Lavender)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.title,
                style = RunkkTextStyles.text(15, if (item.isRead) FontWeight.Medium else FontWeight.Black),
                color = RunkkColors.TextPrimary
            )
            Text(item.body, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
            Text(
                time.format(item.receivedAt.atZone(ZoneId.systemDefault())),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 알림함 경로를 그래프에 등록한다(FeatureEntry 구현, 6.1.14). */
class NotificationFeatureEntry @Inject constructor() : FeatureEntry {
    override val route: String = Routes.NOTIFICATIONS

    override fun register(builder: EntryProviderScope<NavKey>) {
        builder.route(Routes.NOTIFICATIONS) { InboxRoute() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NotificationEntryModule {
    @Binds
    @IntoSet
    fun bindNotificationEntry(entry: NotificationFeatureEntry): FeatureEntry
}

@Preview(name = "알림함", widthDp = 390, heightDp = 844)
@Composable
private fun InboxPreview() {
    val now = Instant.parse("2026-10-21T03:00:00Z")
    val clock = Clock.fixed(now, ZoneId.of("Asia/Seoul"))
    RunkkTheme(reduceMotion = true) {
        InboxScreen(
            InboxUiState(
                loading = false,
                groups = groupInbox(
                    listOf(
                        Inbox.Item(
                            1,
                            "CREW_RUN_SOON",
                            "크루런 곧 시작",
                            "새벽크루 크루런이 10분 뒤 시작해요",
                            now.minusSeconds(600),
                            false,
                            "crew"
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
                )
            ),
            onBack = {},
            onItem = {},
            onRetry = {}
        )
    }
}
