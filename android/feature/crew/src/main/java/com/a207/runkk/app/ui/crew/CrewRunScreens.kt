package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLiveDot
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkOverline
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 모집글 쓰기 ----------

@Composable
fun CrewRunWriteRoute(key: Routes.CrewRunWrite, viewModel: CrewRunWriteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewRunWriteEvent.Posted -> navigator.back()
            }
        }
    }
    CrewRunWriteScreen(
        state = state,
        onBack = navigator::back,
        onTitle = viewModel::onTitle,
        onBody = viewModel::onBody,
        onDistance = viewModel::onDistance,
        onDay = viewModel::onDay,
        onTime = viewModel::onTime,
        onSubmit = { viewModel.onSubmit(key.crewId) }
    )
}

@Composable
fun CrewRunWriteScreen(
    state: CrewRunWriteUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onDistance: (Distance) -> Unit,
    onDay: (Int) -> Unit,
    onTime: (LocalTime) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_run_write_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunWriteFields(state, onTitle, onBody, onDistance)
            RunWriteSchedule(state, onDay, onTime)
            Text(
                stringResource(R.string.crew_run_write_notice),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        RunkkPrimaryButton(
            stringResource(R.string.crew_run_write_submit),
            onClick = onSubmit,
            enabled = state.valid,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

/** 제목·내용·거리 입력. */
@Composable
private fun RunWriteFields(
    state: CrewRunWriteUiState,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onDistance: (Distance) -> Unit
) {
    RunkkTextField(
        value = state.title,
        onValueChange = onTitle,
        label = stringResource(R.string.crew_run_write_title_label),
        placeholder = stringResource(R.string.crew_run_write_title_placeholder)
    )
    RunkkTextField(
        value = state.body,
        onValueChange = onBody,
        label = stringResource(R.string.crew_run_write_body_label),
        placeholder = stringResource(R.string.crew_run_write_body_placeholder),
        singleLine = false
    )
    RunkkSegmentRow(
        options = CREW_RUN_DISTANCES.map(RunkkFormat::targetLabel),
        selectedIndex = CREW_RUN_DISTANCES.indexOf(state.distance),
        onSelect = { onDistance(CREW_RUN_DISTANCES[it]) },
        groupLabel = stringResource(R.string.crew_run_write_distance_group),
        spokenLabels = CREW_RUN_DISTANCES.map { RunkkFormat.spokenLabel(RunkkFormat.targetLabel(it)) }
    )
}

/** 날짜·출발 시각 고르기. */
@Composable
private fun RunWriteSchedule(state: CrewRunWriteUiState, onDay: (Int) -> Unit, onTime: (LocalTime) -> Unit) {
    val resources = LocalContext.current.resources
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSelectField(
            label = stringResource(R.string.crew_run_write_date_label),
            options = (0 until CrewRunWriteUiState.DAYS).toList(),
            selected = state.dayOffset,
            optionLabel = { offset -> dayLabel(resources, state, offset) },
            onSelect = onDay,
            modifier = Modifier.weight(1f)
        )
        RunkkSelectField(
            label = stringResource(R.string.crew_run_write_time_label),
            options = CrewRunWriteUiState.TIMES,
            selected = state.time,
            optionLabel = { it.format(TIME) },
            onSelect = onTime,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun dayLabel(resources: Resources, state: CrewRunWriteUiState, offset: Int): String {
    val date = state.today.plusDays(offset.toLong()).format(DAY)
    return when (offset) {
        0 -> resources.getString(R.string.crew_run_write_day_today, date)
        1 -> resources.getString(R.string.crew_run_write_day_tomorrow, date)
        else -> date
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ---------- 대기실·진행 ----------

@Composable
fun CrewRunRoute(key: Routes.CrewRunLobby, viewModel: CrewRunViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewRunEvent.Finished -> {
                    navigator.back()
                    navigator.navigate(Routes.CrewRunResult(event.postId))
                }

                CrewRunEvent.Left -> navigator.back()
            }
        }
    }
    val running = state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN
    BackHandler {
        when {
            running -> viewModel.onQuitTapped()

            state.phase == CrewRunPhase.CANCELLED -> {
                viewModel.onCancelledClosed()
                navigator.back()
            }

            else -> viewModel.onLeaveLobby()
        }
    }
    CrewRunScreen(
        state = state,
        actions = CrewRunActions(
            onReady = viewModel::onReadyToggle,
            onRefresh = viewModel::enter,
            onLeave = viewModel::onLeaveLobby,
            onQuitTap = viewModel::onQuitTapped,
            onQuitLongPress = viewModel::onQuitLongPressed,
            onQuitConfirm = viewModel::onQuitConfirmed,
            onQuitDismiss = viewModel::onQuitDismissed,
            onClose = {
                viewModel.onCancelledClosed()
                navigator.back()
            }
        )
    )
}

data class CrewRunActions(
    val onReady: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onQuitTap: () -> Unit = {},
    val onQuitLongPress: () -> Unit = {},
    val onQuitConfirm: () -> Unit = {},
    val onQuitDismiss: () -> Unit = {},
    val onClose: () -> Unit = {}
)

@Composable
fun CrewRunScreen(state: CrewRunUiState, actions: CrewRunActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkLiveDot(color = if (state.phase == CrewRunPhase.RUNNING) RunkkColors.Lavender else RunkkColors.Lime)
            RunkkOverline(
                "CREW RUN" + (state.distance?.let { " · ${RunkkFormat.targetLabel(it)}" } ?: ""),
                color = if (state.phase == CrewRunPhase.RUNNING) RunkkColors.Lavender else RunkkColors.Lime,
                heading = true,
                modifier = Modifier.weight(1f)
            )
        }
        RunPhaseBody(state, actions)
        if (state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN) {
            RunQuitControl(state, actions)
        }
    }
    if (state.confirmingQuit) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_run_quit_confirm_title),
            message = stringResource(R.string.crew_run_quit_confirm_message),
            confirmLabel = stringResource(R.string.crew_run_quit_confirm_action),
            onConfirm = actions.onQuitConfirm,
            onDismiss = actions.onQuitDismiss,
            dismissLabel = stringResource(R.string.crew_run_quit_confirm_dismiss),
            destructive = true
        )
    }
}

/** 단계별 본문: 들어오는 중·대기실·카운트다운·중계·취소. */
@Composable
private fun ColumnScope.RunPhaseBody(state: CrewRunUiState, actions: CrewRunActions) {
    when (state.phase) {
        CrewRunPhase.ENTERING -> if (state.error != null) {
            RunkkErrorState(state.error.asString(), onRetry = actions.onRefresh, modifier = Modifier.weight(1f))
        } else {
            RunkkLoading(Modifier.weight(1f))
        }

        CrewRunPhase.LOBBY -> LobbyBody(state, actions)

        CrewRunPhase.COUNTDOWN -> CountdownBody(state)

        CrewRunPhase.RUNNING, CrewRunPhase.FINISHED -> RelayBody(state)

        CrewRunPhase.CANCELLED -> RunkkErrorState(
            state.cancelledReason?.let { stringResource(R.string.crew_run_cancelled_with_reason, it) }
                ?: stringResource(R.string.crew_run_cancelled),
            onRetry = actions.onClose,
            retryLabel = stringResource(R.string.crew_run_cancelled_back),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 달리는 중 그만두기: 안내 문구와 길게 눌러 그만두는 단추. */
@Composable
private fun ColumnScope.RunQuitControl(state: CrewRunUiState, actions: CrewRunActions) {
    state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
    RunkkLongPressButton(
        onTap = actions.onQuitTap,
        onLongPress = actions.onQuitLongPress,
        tapLabel = stringResource(R.string.crew_run_quit_tap_label),
        longPressLabel = stringResource(R.string.crew_run_quit_hint),
        contentDescription = stringResource(R.string.crew_run_quit_description),
        caption = stringResource(R.string.crew_run_quit_hint),
        size = 72.dp,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Icon(
            RunkkIcons.Close,
            contentDescription = null,
            tint = RunkkColors.Lavender,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ColumnScope.LobbyBody(state: CrewRunUiState, actions: CrewRunActions) {
    val lobby = state.lobby ?: return
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LobbyUntilStart(state, lobby)
        LobbyStats(state, lobby)
        Participants(lobby)
        Text(
            stringResource(R.string.crew_run_lobby_notice),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        state.error?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                modifier = Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                }
            )
        }
    }
    LobbyButtons(state, actions)
}

/** 출발까지 남은 시간과 모집 일시. */
@Composable
private fun LobbyUntilStart(state: CrewRunUiState, lobby: Lobby) {
    val untilSpoken = state.untilStart?.let { RunkkFormat.durationSpoken(it) }
        ?: stringResource(R.string.crew_run_lobby_until_checking)
    val description = stringResource(R.string.crew_run_lobby_until_description, untilSpoken, meetLabel(lobby.meetAt))
    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.crew_run_lobby_until_label),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Text(untilLabel(state.untilStart), style = RunkkTextStyles.display(72), color = RunkkColors.Lime)
        Text(meetLabel(lobby.meetAt), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
    }
}

/** 크루런 거리와 준비 완료 인원 타일. */
@Composable
private fun LobbyStats(state: CrewRunUiState, lobby: Lobby) {
    val readyCount = lobby.participants.count { it.ready }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.crew_run_lobby_distance_label),
            state.distance?.let(RunkkFormat::targetLabel) ?: RunkkFormat.EMPTY,
            centered = true,
            valueStyle = RunkkTextStyles.DisplaySmall,
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.crew_run_ready),
            "$readyCount/${lobby.participants.size}",
            spoken = stringResource(R.string.crew_run_lobby_ready_spoken, lobby.participants.size, readyCount),
            centered = true,
            valueStyle = RunkkTextStyles.DisplaySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 대기실 아래 단추: 준비 완료(켬/끔)·새로 고침·대기실 나가기. */
@Composable
private fun LobbyButtons(state: CrewRunUiState, actions: CrewRunActions) {
    val readyState = stringResource(
        if (state.ready) R.string.crew_run_lobby_ready_state_on else R.string.crew_run_lobby_ready_state_off
    )
    RunkkPrimaryButton(
        stringResource(if (state.ready) R.string.crew_run_lobby_ready_waiting else R.string.crew_run_ready),
        onClick = actions.onReady,
        enabled = state.distance != null,
        loading = state.busy,
        // 다시 누르면 풀리는 켬/끔이라 스위치로 알린다.
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.Switch
            stateDescription = readyState
        }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSecondaryButton(
            stringResource(R.string.crew_run_lobby_refresh),
            onClick = actions.onRefresh,
            modifier = Modifier.weight(1f),
            height = 48.dp
        )
        RunkkSecondaryButton(
            stringResource(R.string.crew_run_lobby_leave),
            onClick = actions.onLeave,
            enabled = !state.leaving,
            modifier = Modifier.weight(1f),
            height = 48.dp
        )
    }
}

@Composable
private fun Participants(lobby: Lobby) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lobby.participants.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(RunkkColors.Surface)
                    .border(1.dp, if (p.me) RunkkColors.Lime else RunkkColors.Surface, RunkkShapes.Medium)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    p.displayName,
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (p.me) RunkkBadge(stringResource(R.string.crew_member_me), tone = RunkkBadgeTone.Neutral)
                RunkkBadge(
                    stringResource(if (p.ready) R.string.crew_run_ready else R.string.crew_run_participant_entering),
                    tone = if (p.ready) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CountdownBody(state: CrewRunUiState) {
    Column(
        Modifier.weight(1f).fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(state.countdown?.toString() ?: "READY", style = RunkkTextStyles.display(160), color = RunkkColors.Lime)
        Text(
            stringResource(R.string.crew_run_countdown_go),
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary
        )
    }
}

/** 진행 중 중계(FR-149): 순위·표시명·누적 거리·연결 상태. */
@Composable
private fun ColumnScope.RelayBody(state: CrewRunUiState) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.crew_run_relay_notice),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        val total = state.distance?.meters?.coerceAtLeast(1)
        state.relay.forEach { row -> RelayRowItem(row, total) }
        if (state.relay.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.crew_run_relay_waiting),
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        state.error?.let { Text(it.asString(), style = RunkkTextStyles.Caption, color = RunkkColors.Lavender) }
    }
}

/** 중계 한 줄: 순위·표시명·연결 상태·누적 거리와 진행 막대. */
@Composable
private fun RelayRowItem(row: RelayRow, total: Long?) {
    val status = connectionLabelRes(row.connection)?.let { stringResource(it) }
    val rankText = row.rank?.let { stringResource(R.string.crew_run_rank, it) }
    val nameText = if (row.me) stringResource(R.string.crew_run_me_named, row.name) else row.name
    val kmText = stringResource(R.string.crew_run_km_spoken, RunkkFormat.km(row.distance))
    val description = listOfNotNull(rankText, nameText, kmText, status).joinToString(", ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(if (row.me) RunkkColors.LimeContainer else RunkkColors.Surface)
            .border(
                if (row.me) 2.dp else 1.dp,
                if (row.me || row.rank == 1) RunkkColors.Lime else RunkkColors.Border,
                RunkkShapes.Medium
            )
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RelayRowTop(row, status)
        if (total != null) {
            RunkkProgressBar(
                row.distance.meters.toFloat() / total,
                color = if (row.rank == 1) RunkkColors.Lime else RunkkColors.Lavender
            )
        }
    }
}

/** 중계 한 줄의 윗줄: 순위·표시명·배지·누적 거리. */
@Composable
private fun RelayRowTop(row: RelayRow, status: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            row.rank?.toString() ?: "-",
            style = RunkkTextStyles.display(24),
            color = if (row.rank == 1) RunkkColors.Lime else RunkkColors.TextSecondary,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Text(
            row.name,
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (row.me) RunkkBadge(stringResource(R.string.crew_member_me), tone = RunkkBadgeTone.Lime)
        status?.let { RunkkBadge(it, tone = RunkkBadgeTone.Muted) }
        Text(
            "${RunkkFormat.km(row.distance)} KM",
            style = RunkkTextStyles.display(20),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 연결 상태 문구(서버 CrewRunRelay.Connection 이름). 연결됨이면 null. */
@StringRes
internal fun connectionLabelRes(connection: String): Int? = when (connection) {
    "CHECKING" -> R.string.crew_run_connection_checking
    "QUIT" -> R.string.crew_run_connection_quit
    "ARRIVED" -> R.string.crew_run_connection_arrived
    else -> null
}

/** 남은 시간 "12:34" 또는 "1:02:03". */
internal fun untilLabel(left: Duration?): String = left?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY

// ---------- 결과 ----------

@Composable
fun CrewRunResultRoute(postId: Long, viewModel: CrewRunResultViewModel = hiltViewModel()) {
    LaunchedEffect(postId) { viewModel.load(postId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CrewRunResultScreen(
        state = state,
        onCard = { navigator.navigate(Routes.RECORDS) },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(postId, force = true) }
    )
}

@Composable
fun CrewRunResultScreen(
    state: CrewRunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkOverline("CREW RUN RESULT", heading = true)
            val result = state.result
            when {
                result == null && state.loading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }

                result == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.crew_run_result_load_failed),
                    onRetry = onRetry
                )

                else -> ResultBody(state, result)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkSecondaryButton(
                stringResource(R.string.crew_run_result_card),
                onClick = onCard,
                modifier = Modifier.weight(1f)
            )
            RunkkPrimaryButton(
                stringResource(R.string.crew_run_result_home),
                onClick = onHome,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ResultBody(state: CrewRunResultUiState, result: CrewRunResult) {
    ResultSummary(state, result)
    if (result.firstCrewRun) RunkkBanner(stringResource(R.string.crew_run_result_first))
    result.finishers.sortedBy { it.rank ?: Int.MAX_VALUE }.forEach { f -> FinisherRow(f) }
}

/** 결과 요약 카드: 완주 인원과 크루 주간 거리에 더한 거리. */
@Composable
private fun ResultSummary(state: CrewRunResultUiState, result: CrewRunResult) {
    val all = result.finishers.size
    Column(
        Modifier.fillMaxWidth().clip(
            RunkkShapes.ExtraLarge
        ).background(RunkkColors.Lime).padding(24.dp).semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (state.finishedCount == all && all > 0) {
                stringResource(R.string.crew_run_result_all_finished, all)
            } else {
                stringResource(R.string.crew_run_result_some_finished, all, state.finishedCount)
            },
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = RunkkColors.OnLime
        )
        Text(
            stringResource(R.string.crew_run_result_contribution, RunkkFormat.kmShort(result.raceContribution)),
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.OnLime
        )
    }
}

/** 완주자 한 줄: 순위·표시명·기록·평균 페이스(못 끝낸 사람은 그만둠·미완주 표시). */
@Composable
private fun FinisherRow(f: CrewRunResult.Finisher) {
    val time = f.elapsedSeconds?.let { RunkkFormat.duration(Duration.ofSeconds(it)) }
    val pace = f.averagePaceSecondsPerKm?.takeIf { it > 0 }?.let { RunkkFormat.pace(Pace(it)) }
    val unfinished =
        stringResource(if (f.quit) R.string.crew_run_connection_quit else R.string.crew_run_result_unfinished)
    val description = listOfNotNull(
        f.rank?.let { stringResource(R.string.crew_run_rank, it) } ?: unfinished,
        if (f.me) stringResource(R.string.crew_run_me_named, f.displayName) else f.displayName,
        f.elapsedSeconds?.let { RunkkFormat.durationSpoken(Duration.ofSeconds(it)) },
        f.averagePaceSecondsPerKm?.takeIf { it > 0 }
            ?.let { stringResource(R.string.crew_run_result_average_spoken, RunkkFormat.paceSpoken(Pace(it))) }
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface)
            .border(
                if (f.me) 2.dp else 1.dp,
                if (f.me || f.rank == 1) RunkkColors.Lime else RunkkColors.Border,
                RunkkShapes.Medium
            )
            .padding(16.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            f.rank?.toString() ?: "-",
            style = RunkkTextStyles.display(24),
            color = if (f.rank == 1) RunkkColors.Lime else RunkkColors.TextSecondary,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Text(
            f.displayName,
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (f.me) RunkkBadge(stringResource(R.string.crew_member_me), tone = RunkkBadgeTone.Lime)
        FinisherTrailing(f, unfinished, time, pace)
    }
}

/** 완주자 한 줄의 오른쪽: 못 끝냈으면 배지, 끝냈으면 기록과 평균 페이스. */
@Composable
private fun FinisherTrailing(f: CrewRunResult.Finisher, unfinished: String, time: String?, pace: String?) {
    if (f.rank == null) {
        RunkkBadge(unfinished, tone = RunkkBadgeTone.Muted)
    } else {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                time ?: RunkkFormat.EMPTY,
                style = RunkkTextStyles.display(20),
                color = RunkkColors.TextPrimary
            )
            pace?.let {
                Text(
                    stringResource(R.string.crew_run_result_average_pace, it),
                    style = RunkkTextStyles.Micro,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    }
}
