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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIconButton
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
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.SelectLabel
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.PostList
import java.time.Duration
import java.time.LocalDate
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
        onSubmit = { viewModel.onSubmit(key.crewId) },
        onKind = viewModel::onKind,
        onPlace = viewModel::onPlace,
        onCapacity = viewModel::onCapacity
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
    modifier: Modifier = Modifier,
    onKind: (PostList.Kind) -> Unit = {},
    onPlace: (String) -> Unit = {},
    onCapacity: (Int?) -> Unit = {}
) {
    val isMeetup = state.kind == PostList.Kind.MEETUP
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_run_write_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkSegmentRow(
                options = listOf(
                    stringResource(R.string.crew_schedule_kind_crew_run),
                    stringResource(R.string.crew_schedule_kind_meetup)
                ),
                selectedIndex = SCHEDULE_KINDS.indexOf(state.kind),
                onSelect = { onKind(SCHEDULE_KINDS[it]) },
                groupLabel = stringResource(R.string.crew_schedule_kind_group)
            )
            // 칸 순서(와이어 I8): 제목, 내용, 일시, 장소명, 정원. 대면 모임 제목과 응답 마감은 서버 입력에 없어 두지 않는다.
            if (isMeetup) {
                MeetupBodyField(state, onBody)
                RunWriteSchedule(state, onDay, onTime)
                MeetupPlaceFields(state, onPlace, onCapacity)
            } else {
                RunWriteFields(state, onTitle, onBody, onDistance)
                RunWriteSchedule(state, onDay, onTime)
            }
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RunkkPrimaryButton(
            stringResource(R.string.crew_run_write_submit),
            onClick = onSubmit,
            isEnabled = state.isValid,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

/** 대면 모임 내용. */
@Composable
private fun MeetupBodyField(state: CrewRunWriteUiState, onBody: (String) -> Unit) {
    RunkkTextField(
        value = state.body,
        onValueChange = onBody,
        label = stringResource(R.string.crew_meetup_body_label),
        placeholder = stringResource(R.string.crew_meetup_body_placeholder),
        isSingleLine = false
    )
}

/** 대면 모임 장소명(50자)·정원(2~10, 비우면 크루 정원, FR-132). 지도 좌표·위치 공유는 받지 않는다. */
@Composable
private fun MeetupPlaceFields(state: CrewRunWriteUiState, onPlace: (String) -> Unit, onCapacity: (Int?) -> Unit) {
    RunkkTextField(
        value = state.placeName,
        onValueChange = onPlace,
        label = stringResource(
            R.string.crew_meetup_place_label,
            state.placeName.codePointLength(),
            CrewRunWriteUiState.PLACE_MAX
        ),
        placeholder = stringResource(R.string.crew_meetup_place_placeholder)
    )
    val capacities: List<Int?> = listOf<Int?>(null) +
        (CrewRunWriteUiState.CAPACITY_MIN..CrewRunWriteUiState.CAPACITY_MAX).toList()
    val crewDefault = stringResource(R.string.crew_meetup_capacity_default)
    val resources = LocalContext.current.resources
    RunkkSelectField(
        label = stringResource(R.string.crew_meetup_capacity_label),
        options = capacities,
        selected = state.capacity,
        optionLabel = {
            SelectLabel(it?.let { n -> resources.getString(R.string.crew_meetup_capacity_option, n) } ?: crewDefault)
        },
        onSelect = onCapacity,
        placeholder = crewDefault
    )
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
        isSingleLine = false
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
            optionLabel = { offset -> SelectLabel(dayLabel(resources, state.today, offset)) },
            onSelect = onDay,
            modifier = Modifier.weight(1f)
        )
        RunkkSelectField(
            label = stringResource(R.string.crew_run_write_time_label),
            options = CrewRunWriteUiState.TIMES,
            selected = state.time,
            optionLabel = { SelectLabel(it.format(TIME)) },
            onSelect = onTime,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 오늘부터 [offset]일 뒤 날짜 이름("오늘 10/20(화)", "내일 …", "10/22(목)"). */
internal fun dayLabel(resources: Resources, today: LocalDate, offset: Int): String {
    val date = today.plusDays(offset.toLong()).format(DAY)
    return when (offset) {
        0 -> resources.getString(R.string.crew_run_write_day_today, date)
        1 -> resources.getString(R.string.crew_run_write_day_tomorrow, date)
        else -> date
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
internal val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ---------- 대기실·진행 ----------

@Composable
fun CrewRunRoute(key: Routes.CrewRunLobby, viewModel: CrewRunViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewRunEvent.Finished -> navigator.replace(Routes.CrewRunResult(event.postId))
                CrewRunEvent.Left -> navigator.back()
            }
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
            onClose = viewModel::onBack,
            onBack = viewModel::onBack
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
    val onClose: () -> Unit = {},
    /** 상단 뒤로 단추·시스템 뒤로 가기·"돌아가기". 단계별 처리는 CrewRunViewModel.onBack. */
    val onBack: () -> Unit = {}
)

/**
 * 크루런 대기실·진행 화면(와이어 I5, I6, I12). 시스템 뒤로 가기는 늘 [CrewRunActions.onBack]으로 보낸다(대기실에 들어가지 못한 오류
 * 화면에서도 나갈 수 있게). 들어가는 중과 대기실에는 상단에 뒤로 단추를 둔다(대기실에서 누르면 대기실을 나간다).
 */
@Composable
fun CrewRunScreen(state: CrewRunUiState, actions: CrewRunActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.onBack)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 32.dp)
    ) {
        val isLive = state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN ||
            state.phase == CrewRunPhase.FINISHED
        RunkkTopBar(
            title = stringResource(if (isLive) R.string.crew_run_live_title else R.string.crew_run_lobby_title),
            onBack = actions.onBack.takeIf { state.phase == CrewRunPhase.ENTERING || state.phase == CrewRunPhase.LOBBY }
        )
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunPhaseBody(state, actions)
        }
        if (state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN) {
            RunQuitControl(state, actions)
        }
    }
    if (state.isConfirmingQuit) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_run_quit_confirm_title),
            message = stringResource(R.string.crew_run_quit_confirm_message),
            confirmLabel = stringResource(R.string.crew_run_quit_confirm_action),
            onConfirm = actions.onQuitConfirm,
            onDismiss = actions.onQuitDismiss,
            dismissLabel = stringResource(R.string.crew_run_quit_confirm_dismiss),
            isDestructive = true
        )
    }
}

/** 단계별 본문: 들어오는 중·대기실·카운트다운·중계·취소. */
@Composable
private fun ColumnScope.RunPhaseBody(state: CrewRunUiState, actions: CrewRunActions) {
    when (state.phase) {
        CrewRunPhase.ENTERING -> if (state.error != null) {
            // 열리지 않음·이미 시작 같은 거부는 다시 해도 같으니 "돌아가기"를 준다.
            if (state.canRetry) {
                RunkkErrorState(state.error.asString(), onRetry = actions.onRefresh, modifier = Modifier.weight(1f))
            } else {
                RunkkErrorState(
                    state.error.asString(),
                    onRetry = actions.onBack,
                    retryLabel = stringResource(R.string.crew_run_cancelled_back),
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            RunkkLoading(Modifier.weight(1f))
        }

        CrewRunPhase.LOBBY -> LobbyBody(state, actions)

        CrewRunPhase.COUNTDOWN -> CountdownBody(state)

        CrewRunPhase.RUNNING, CrewRunPhase.FINISHED -> RelayBody(state)

        // 취소(피그마 F-14c): 서버가 보낸 사유 문구를 그대로 보이고 크루런 일정으로 돌아간다.
        CrewRunPhase.CANCELLED -> RunkkErrorState(
            state.cancelledReason ?: stringResource(R.string.crew_run_cancelled),
            onRetry = actions.onClose,
            retryLabel = stringResource(R.string.crew_run_cancelled_to_schedule),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 달리는 중 그만두기 단추(와이어 I6). 누르면 확인(I12)을 연다. */
@Composable
private fun RunQuitControl(state: CrewRunUiState, actions: CrewRunActions) {
    val description = stringResource(R.string.crew_run_quit_description)
    // 시스템 뒤로 가기를 누르면 그만두기 단추를 쓰라는 안내가 뜬다.
    state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    RunkkSecondaryButton(
        stringResource(R.string.crew_run_quit_confirm_action),
        onClick = actions.onQuitLongPress,
        isEnabled = !state.isConfirmingQuit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .semantics { contentDescription = description },
        height = 52.dp
    )
}

/** 대기실(와이어 I5): 거리, 출발까지 남은 시간, 참가자와 준비 수, 준비 완료 단추. */
@Composable
private fun ColumnScope.LobbyBody(state: CrewRunUiState, actions: CrewRunActions) {
    val lobby = state.lobby ?: return
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            state.distance?.let(RunkkFormat::targetLabel) ?: RunkkFormat.EMPTY,
            style = RunkkTextStyles.BodyStrong,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LobbyUntilStart(state, lobby)
        Participants(lobby)
        state.error?.let {
            Text(
                it.asString(),
                style = RunkkTextStyles.Caption,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    LobbyReadyButton(state, actions)
}

/** 출발까지 남은 시간(크게)과 "출발까지". */
@Composable
private fun LobbyUntilStart(state: CrewRunUiState, lobby: Lobby) {
    val untilSpoken = state.untilStart?.let { RunkkFormat.durationSpoken(it) }
        ?: stringResource(R.string.crew_run_lobby_until_checking)
    val description = stringResource(R.string.crew_run_lobby_until_description, untilSpoken, meetLabel(lobby.meetAt))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RunkkShapes.ExtraLarge)
            .padding(vertical = 24.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            untilLabel(state.untilStart),
            style = RunkkTextStyles.display(72),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.crew_run_lobby_until_label),
            style = RunkkTextStyles.Caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 준비 완료 단추(켬/끔). */
@Composable
private fun LobbyReadyButton(state: CrewRunUiState, actions: CrewRunActions) {
    val readyState = stringResource(
        if (state.isReady) R.string.crew_run_lobby_ready_state_on else R.string.crew_run_lobby_ready_state_off
    )
    RunkkPrimaryButton(
        stringResource(if (state.isReady) R.string.crew_run_lobby_ready_waiting else R.string.crew_run_ready),
        onClick = actions.onReady,
        isEnabled = state.distance != null,
        isLoading = state.isBusy,
        // 다시 누르면 풀리는 켬/끔이라 스위치로 알린다.
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).semantics {
            role = Role.Switch
            stateDescription = readyState
        }
    )
}

/** 참가자(와이어 I5): "참가자", "준비 완료 n / m", 줄마다 이름(나)과 준비 완료·기다리는 중. */
@Composable
private fun Participants(lobby: Lobby) {
    val colors = MaterialTheme.colorScheme
    val readyCount = lobby.participants.count { it.isReady }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_run_lobby_participants),
                style = RunkkTextStyles.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_run_lobby_ready_count, readyCount, lobby.participants.size),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
        lobby.participants.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(colors.surface)
                    .border(1.dp, if (p.isMe) colors.primary else colors.outlineVariant, RunkkShapes.Medium)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
                CrewLogo(p.displayName, size = 32, isAccent = p.isMe, modifier = Modifier.clip(CircleShape))
                Text(name, style = RunkkTextStyles.BodyStrong, color = colors.onSurface, modifier = Modifier.weight(1f))
                RunkkBadge(
                    stringResource(if (p.isReady) R.string.crew_run_ready else R.string.crew_run_participant_waiting),
                    tone = if (p.isReady) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
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
        Text(
            state.countdown?.toString() ?: "READY",
            style = RunkkTextStyles.display(160),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.crew_run_countdown_go),
            style = RunkkTextStyles.BodyStrong,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 진행 중 중계(와이어 I6, FR-149): 내 순위·거리, 1위와의 거리 차, 순위표(순위, 이름, 누적 거리, 막대). */
@Composable
private fun ColumnScope.RelayBody(state: CrewRunUiState) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.distance?.let {
            Text(
                RunkkFormat.targetLabel(it),
                style = RunkkTextStyles.BodyStrong,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RelayMine(state.relay)
        val total = state.distance?.meters?.coerceAtLeast(1)
        state.relay.forEach { row -> RelayRowItem(row, total) }
        if (state.relay.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.crew_run_relay_waiting),
                    style = RunkkTextStyles.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        state.error?.let {
            Text(it.asString(), style = RunkkTextStyles.Caption, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

/** 내 순위 "2 / 5"와 내 거리, 1위가 아니면 "1위와 190m". */
@Composable
private fun RelayMine(relay: List<RelayRow>) {
    val mine = relay.firstOrNull { it.isMe } ?: return
    val leader = relay.filter { it.rank != null }.minByOrNull { it.rank ?: Int.MAX_VALUE }
    val colors = MaterialTheme.colorScheme
    val gap = leader?.takeIf { !it.isMe }?.let { (it.distance.meters - mine.distance.meters).coerceAtLeast(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(colors.primaryContainer)
            .border(1.dp, colors.primary, RunkkShapes.ExtraLarge)
            .padding(20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RelayStat(
                mine.rank?.let { stringResource(R.string.crew_run_live_rank, it, relay.size) } ?: RunkkFormat.EMPTY,
                stringResource(R.string.crew_run_live_rank_label),
                Modifier.weight(1f)
            )
            RelayStat(
                RunkkFormat.km(mine.distance),
                stringResource(R.string.crew_run_live_distance_label),
                Modifier.weight(1f)
            )
        }
        gap?.let {
            Text(
                stringResource(R.string.crew_run_live_gap, it),
                style = RunkkTextStyles.BodyStrong,
                color = colors.onSurface
            )
        }
    }
}

@Composable
private fun RelayStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = RunkkTextStyles.display(36), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text(label, style = RunkkTextStyles.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 중계 한 줄: 순위·표시명·연결 상태·누적 거리와 진행 막대. */
@Composable
private fun RelayRowItem(row: RelayRow, total: Long?) {
    val status = connectionLabelRes(row.connection)?.let { stringResource(it) }
    val rankText = row.rank?.let { stringResource(R.string.crew_run_rank, it) }
    val nameText = if (row.isMe) stringResource(R.string.crew_run_me_named, row.name) else row.name
    val kmText = stringResource(R.string.crew_run_km_spoken, RunkkFormat.km(row.distance))
    val description = listOfNotNull(rankText, nameText, kmText, status).joinToString(", ")
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(colors.surface)
            .border(1.dp, if (row.isMe) colors.primary else colors.outlineVariant, RunkkShapes.Medium)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                row.rank?.toString() ?: "-",
                style = RunkkTextStyles.display(20),
                color = if (row.rank == 1) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 20.dp)
            )
            Text(
                if (row.isMe) stringResource(R.string.crew_member_me) else row.name,
                style = RunkkTextStyles.BodyStrong,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            status?.let { RunkkBadge(it, tone = RunkkBadgeTone.Muted) }
            Text(
                stringResource(R.string.crew_contribution_km, RunkkFormat.km(row.distance)),
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = colors.onSurface
            )
        }
        if (total != null) {
            RunkkProgressBar(
                row.distance.meters.toFloat() / total,
                color = if (row.isMe) colors.primary else colors.onSurfaceVariant,
                trackColor = colors.outlineVariant
            )
        }
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
        // 확인: 크루 일정으로 돌아간다(대기실은 결과를 열 때 백 스택에서 뺐다).
        onHome = navigator::back,
        onRetry = { viewModel.load(postId, isForced = true) }
    )
}

/** 크루런 결과(와이어 I7): 뒤로, "크루런 결과", 인원, 내 순위·기록·평균 페이스, 순위표, 카드 공유와 확인. */
@Composable
fun CrewRunResultScreen(
    state: CrewRunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_run_result_title), onBack = onHome)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val result = state.result
            when {
                result == null && state.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }

                result == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.crew_run_result_load_failed),
                    onRetry = onRetry
                )

                else -> ResultBody(result)
            }
        }
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
private fun ResultBody(result: CrewRunResult) {
    Text(
        stringResource(R.string.crew_run_result_people, result.finishers.size),
        style = RunkkTextStyles.BodyStrong,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    result.finishers.firstOrNull { it.isMe }?.let { MyResult(it) }
    result.finishers.sortedBy { it.rank ?: Int.MAX_VALUE }.forEach { f -> FinisherRow(f) }
}

/** 내 결과(와이어 I7): 순위(크게), "기록, 평균 페이스". 그만뒀으면 "중간에 그만둠". */
@Composable
private fun MyResult(me: CrewRunResult.Finisher) {
    val colors = MaterialTheme.colorScheme
    val time = me.elapsedSeconds?.let { RunkkFormat.duration(Duration.ofSeconds(it)) }
    val pace = me.averagePaceSecondsPerKm?.takeIf { it > 0 }?.let { RunkkFormat.pace(Pace(it)) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(colors.primaryContainer)
            .border(1.dp, colors.primary, RunkkShapes.ExtraLarge)
            .padding(24.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val rank = me.rank
        if (rank != null) {
            // 숫자 글꼴에는 한글이 없어 "위"는 본문 글꼴로 붙인다.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rank.toString(), style = RunkkTextStyles.display(56), color = colors.primary)
                Text(
                    stringResource(R.string.crew_run_rank_unit),
                    style = RunkkTextStyles.text(24, FontWeight.Black),
                    color = colors.primary,
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
                )
            }
        } else {
            Text(stringResource(unfinishedRes(me)), style = RunkkTextStyles.CardTitle, color = colors.onSurface)
        }
        if (time != null) {
            Text(
                listOfNotNull(time, pace?.let { stringResource(R.string.crew_run_result_average_pace, it) })
                    .joinToString(", "),
                style = RunkkTextStyles.BodyStrong,
                color = colors.onSurface
            )
        }
    }
}

@StringRes
private fun unfinishedRes(f: CrewRunResult.Finisher): Int =
    if (f.hasQuit) R.string.crew_run_result_quit else R.string.crew_run_result_unfinished

/** 순위표 한 줄: 순위(못 끝냈으면 -), 이름(나), 기록 또는 "중간에 그만둠". */
@Composable
private fun FinisherRow(f: CrewRunResult.Finisher) {
    val colors = MaterialTheme.colorScheme
    val time = f.elapsedSeconds?.let { RunkkFormat.duration(Duration.ofSeconds(it)) }
    val unfinished = stringResource(unfinishedRes(f))
    val description = listOfNotNull(
        f.rank?.let { stringResource(R.string.crew_run_rank, it) } ?: unfinished,
        if (f.isMe) stringResource(R.string.crew_run_me_named, f.displayName) else f.displayName,
        f.elapsedSeconds?.takeIf { f.rank != null }?.let { RunkkFormat.durationSpoken(Duration.ofSeconds(it)) },
        f.averagePaceSecondsPerKm?.takeIf { it > 0 && f.rank != null }
            ?.let { stringResource(R.string.crew_run_result_average_spoken, RunkkFormat.paceSpoken(Pace(it))) }
    ).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(colors.surface)
            .border(1.dp, if (f.isMe) colors.primary else colors.outlineVariant, RunkkShapes.Medium)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            f.rank?.toString() ?: "-",
            style = RunkkTextStyles.display(20),
            color = if (f.rank == 1) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 20.dp)
        )
        Text(
            if (f.isMe) stringResource(R.string.crew_member_me) else f.displayName,
            style = RunkkTextStyles.BodyStrong,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (f.rank != null) time ?: RunkkFormat.EMPTY else unfinished,
            style = if (f.rank != null) RunkkTextStyles.text(14, FontWeight.Bold) else RunkkTextStyles.Caption,
            color = if (f.rank != null) colors.onSurface else colors.onSurfaceVariant
        )
    }
}
