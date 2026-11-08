package com.a207.runkk.app.ui.crew

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.KakaoRunMap
import com.a207.runkk.app.core.designsystem.LocalKakaoMapKey
import com.a207.runkk.app.core.designsystem.OPPONENT_MARKER_TAG
import com.a207.runkk.app.core.designsystem.OpponentMarkerPlacer
import com.a207.runkk.app.core.designsystem.RouteCanvas
import com.a207.runkk.app.core.designsystem.RouteMarker
import com.a207.runkk.app.core.designsystem.RunMapMarker
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkColors
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
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.SelectLabel
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.canShowKakaoMap
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.designsystem.rememberHeadingLeft
import com.a207.runkk.app.core.designsystem.rememberIsMoving
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import com.a207.runkk.app.domain.crew.PostList
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
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

// ---------- 대기실·진행(합산 방식, 피그마 F-14~F-15d) ----------

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
    val actions = remember(viewModel) {
        CrewRunActions(
            onReady = viewModel::onReadyToggle,
            onRefresh = viewModel::enter,
            onLeave = viewModel::onLeaveLobby,
            onQuitTap = viewModel::onQuitTapped,
            onQuitLongPress = viewModel::onQuitLongPressed,
            onQuitConfirm = viewModel::onQuitConfirmed,
            onQuitDismiss = viewModel::onQuitDismissed,
            onPause = viewModel::onPause,
            onResume = viewModel::onResume,
            onClose = viewModel::onBack,
            onBack = viewModel::onBack
        )
    }
    CrewRunScreen(state = state, actions = actions)
}

data class CrewRunActions(
    val onReady: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onQuitTap: () -> Unit = {},
    val onQuitLongPress: () -> Unit = {},
    val onQuitConfirm: () -> Unit = {},
    val onQuitDismiss: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onClose: () -> Unit = {},
    /** 시스템 뒤로 가기·"돌아가기". 단계별 처리는 CrewRunViewModel.onBack. */
    val onBack: () -> Unit = {}
)

/**
 * 크루런 대기실(F-14·F-14b)·카운트다운·진행(F-15)·일시정지(F-15c)·그만두기 확인(F-15d). 시스템 뒤로 가기는 늘 [CrewRunActions.onBack]으로
 * 보낸다(대기실에 들어가지 못한 오류 화면에서도 나갈 수 있게).
 */
@Composable
fun CrewRunScreen(state: CrewRunUiState, actions: CrewRunActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.onBack)
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        when (state.phase) {
            CrewRunPhase.ENTERING -> EnteringBody(state, actions)
            CrewRunPhase.LOBBY -> LobbyContent(state, actions)
            CrewRunPhase.COUNTDOWN -> CountdownBody(state)
            CrewRunPhase.RUNNING, CrewRunPhase.FINISHED -> RunningContent(state, actions)
            CrewRunPhase.CANCELLED -> CancelledBody(state, actions)
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

/** 대기실에 들어가는 중: 읽는 중이거나 오류. 열리지 않음·이미 시작 같은 거부는 다시 해도 같으니 "돌아가기"를 준다. */
@Composable
private fun EnteringBody(state: CrewRunUiState, actions: CrewRunActions) {
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_run_lobby_title), onBack = actions.onBack)
        val error = state.error
        when {
            error == null -> RunkkLoading(Modifier.weight(1f))

            state.canRetry -> RunkkErrorState(
                error.asString(),
                onRetry = actions.onRefresh,
                modifier = Modifier.weight(1f)
            )

            else -> RunkkErrorState(
                error.asString(),
                onRetry = actions.onBack,
                retryLabel = stringResource(R.string.crew_run_cancelled_back),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 취소된 크루런(문구는 기존 취소 표시 그대로). */
@Composable
private fun CancelledBody(state: CrewRunUiState, actions: CrewRunActions) {
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_run_lobby_title))
        RunkkErrorState(
            state.cancelledReason?.let { stringResource(R.string.crew_run_cancelled_with_reason, it) }
                ?: stringResource(R.string.crew_run_cancelled),
            onRetry = actions.onClose,
            retryLabel = stringResource(R.string.crew_run_cancelled_back),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 칩 "● 크루런 · 5km"(F-14·F-15). */
@Composable
private fun CrewRunChip(distance: Distance?) {
    RunkkStatusPill(
        stringResource(R.string.crew_run_chip, distance?.let(RunkkFormat::targetLabel) ?: RunkkFormat.EMPTY),
        leading = { Box(Modifier.size(8.dp).clip(CircleShape).background(RunkkColors.TextPrimary)) }
    )
}

/**
 * 대기실(F-14·F-14b): 칩과 나가기, 모집 시각·출발까지 남은 시간, 참가자 3열 격자(나·이름, 준비 완료·대기 중·미입장)와
 * "준비 완료 n / m", 아래 준비 완료 단추(준비하면 회색, 다시 누르면 풀린다).
 */
@Composable
private fun LobbyContent(state: CrewRunUiState, actions: CrewRunActions) {
    val lobby = state.lobby ?: return
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CrewRunChip(state.distance)
            Spacer(Modifier.weight(1f))
            RunkkPillButton(
                stringResource(R.string.crew_run_lobby_leave),
                onClick = actions.onLeave,
                isEnabled = !state.isLeaving
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            LobbyUntilStart(state, lobby)
            Participants(lobby)
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        LobbyReadyButton(state, actions)
    }
}

/** "오늘 오후 9:00", 큰 남은 시간, "출발까지". */
@Composable
private fun LobbyUntilStart(state: CrewRunUiState, lobby: Lobby) {
    val meet = lobbyMeetLabel(lobby.meetAt)
    val untilSpoken = state.untilStart?.let { RunkkFormat.durationSpoken(it) }
        ?: stringResource(R.string.crew_run_lobby_until_checking)
    val description = stringResource(R.string.crew_run_lobby_until_description, untilSpoken, meet)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clearAndSetSemantics {
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(meet, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
        Text(untilLabel(state.untilStart), style = RunkkTextStyles.display(52), color = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.crew_run_lobby_until_label),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextPrimary
        )
    }
}

/** 오늘이면 "오늘 오후 9:00", 아니면 모집 시각 표기. */
@Composable
private fun lobbyMeetLabel(at: Instant): String {
    val zone = ZoneId.systemDefault()
    val time = at.atZone(zone)
    if (time.toLocalDate() != LocalDate.now(zone)) return meetLabel(at)
    val pattern = stringResource(R.string.crew_run_lobby_meet_time_pattern)
    return stringResource(
        R.string.crew_run_lobby_meet_today,
        time.format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
    )
}

/** 준비 완료 단추(켬/끔). 준비하면 회색 단추가 된다(F-14b). */
@Composable
private fun LobbyReadyButton(state: CrewRunUiState, actions: CrewRunActions) {
    val readyState = stringResource(
        if (state.isReady) R.string.crew_run_lobby_ready_state_on else R.string.crew_run_lobby_ready_state_off
    )
    // 다시 누르면 풀리는 켬/끔이라 스위치로 알린다.
    val switch = Modifier.fillMaxWidth().padding(top = 16.dp).semantics {
        role = Role.Switch
        stateDescription = readyState
    }
    if (state.isReady) {
        RunkkSecondaryButton(
            stringResource(R.string.crew_run_ready),
            onClick = actions.onReady,
            isEnabled = !state.isBusy,
            modifier = switch
        )
    } else {
        RunkkPrimaryButton(
            stringResource(R.string.crew_run_ready),
            onClick = actions.onReady,
            isEnabled = state.distance != null,
            isLoading = state.isBusy,
            modifier = switch
        )
    }
}

/** 참가자(F-14): "참가자", "준비 완료 n / m", 3열 격자. */
@Composable
private fun Participants(lobby: Lobby) {
    val readyCount = lobby.participants.count { it.isReady }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(
                stringResource(R.string.crew_run_lobby_participants),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_run_lobby_ready_count, readyCount, lobby.participants.size),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextPrimary
            )
        }
        lobby.participants.chunked(LOBBY_COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { p -> ParticipantCell(p, Modifier.weight(1f)) }
                repeat(LOBBY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 참가자 한 칸: 원, 이름(나), 준비 완료(굵게)·대기 중·미입장. */
@Composable
private fun ParticipantCell(p: Lobby.Participant, modifier: Modifier = Modifier) {
    val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
    val status = stringResource(
        when {
            p.isReady -> R.string.crew_run_ready
            p.isEntered -> R.string.crew_run_participant_waiting
            else -> R.string.crew_run_participant_not_entered
        }
    )
    Column(
        modifier = modifier.padding(vertical = 4.dp).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CrewLogo(p.displayName, size = 64, isAccent = p.isMe, modifier = Modifier.clip(CircleShape))
        Text(name, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary, maxLines = 1)
        Text(
            status,
            style = if (p.isReady) RunkkTextStyles.text(13, FontWeight.Bold) else RunkkTextStyles.Caption,
            color = RunkkColors.TextPrimary
        )
    }
}

@Composable
private fun CountdownBody(state: CrewRunUiState) {
    Column(
        Modifier.fillMaxSize().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            state.countdown?.toString() ?: "READY",
            style = RunkkTextStyles.display(160),
            color = RunkkColors.TextPrimary
        )
        Text(
            stringResource(R.string.crew_run_countdown_go),
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary
        )
    }
}

/**
 * 진행(F-15)·일시정지(F-15c): 칩, 큰 내 거리, 경과·현재 페이스·평균 페이스, 지도(내 아바타와 크루원 아바타),
 * "함께 달리는 크루원 n명", 길게 눌러 종료와 일시정지. 정지 중에는 반투명 막을 덮고 종료·재개만 받는다(개인 러닝 B-4와 같다).
 */
@Composable
private fun RunningContent(state: CrewRunUiState, actions: CrewRunActions) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CrewRunChip(state.distance)
            BigDistance(state.mine.distance)
            RunStats(state.mine)
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp)) {
                CrewRunMap(state)
                state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.BottomCenter)) }
            }
            Text(
                stringResource(R.string.crew_run_together, state.runningMates),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            // 정지 중에도 자리를 지켜 위 줄이 내려앉지 않게 하고, 단추는 막 위(PausedOverlay)에 다시 그린다.
            val hidden = if (state.isPaused) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {} else Modifier
            Box(hidden) { RunControls(state, actions, isPaused = false) }
        }
        if (state.isPaused) PausedOverlay(state, actions)
    }
}

/** 큰 거리 "3.22"와 오른쪽 아래 작은 "km". */
@Composable
private fun BigDistance(distance: Distance) {
    val spoken = stringResource(R.string.crew_run_km_spoken, RunkkFormat.km(distance))
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(RunkkFormat.km(distance), style = RunkkTextStyles.DisplayHuge, color = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.crew_run_unit_km),
            style = RunkkTextStyles.text(18),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
    }
}

/** 경과·현재 페이스·평균 페이스(대결 진행과 같은 세 칸). */
@Composable
private fun RunStats(mine: CrewRunProgress) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RunStat(
            RunkkFormat.duration(mine.elapsed),
            stringResource(R.string.crew_run_stat_elapsed),
            RunkkFormat.durationSpoken(mine.elapsed)
        )
        RunStat(
            RunkkFormat.pace(mine.currentPace),
            stringResource(R.string.crew_run_stat_current_pace),
            RunkkFormat.paceSpoken(mine.currentPace)
        )
        RunStat(
            RunkkFormat.pace(mine.averagePace),
            stringResource(R.string.crew_run_stat_average_pace),
            RunkkFormat.paceSpoken(mine.averagePace)
        )
    }
}

@Composable
private fun RunStat(value: String, label: String, spoken: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$label $spoken" }
    ) {
        Text(value, style = RunkkTextStyles.display(28), color = RunkkColors.TextPrimary)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/**
 * 지도와 아바타(F-15): 경로 끝에 내 아바타, 아직 끝내지 않은 크루원은 내 경로 위 거리 차만큼 떨어진 지점(OpponentMarkerPlacer, 대결 상대와
 * 같은 방식). 크루원 성별을 모르므로 기본 캐릭터이고 멈췄으면 이름표가 "이름 · 멈춤"이다. Kakao 지도를 띄울 수 없으면 격자 바탕 경로선이다.
 */
@Composable
private fun CrewRunMap(state: CrewRunUiState) {
    val mine = state.mine
    val route = mine.route
    val me = route.points.lastOrNull()
    val isMoving = rememberIsMoving(mine.distance) && !mine.isPaused && mine.currentPace != null
    val frameMillis = runFrameMillis(mine.currentPace)
    val isLeft = rememberHeadingLeft(route)
    val myName = stringResource(R.string.crew_member_me)
    val myLabel = if (mine.isPaused) stringResource(R.string.crew_run_mate_paused, myName) else myName
    val resources = LocalContext.current.resources
    val mates = state.relay.filter { !it.isMe && it.isRunning }.mapNotNull { row ->
        val gap = (mine.distance.meters - row.distance.meters).toInt()
        OpponentMarkerPlacer.place(route, mine.distance, gap)?.let { point ->
            val isPaused = row.state == MateState.PAUSED
            MateMarker(
                point,
                if (isPaused) resources.getString(R.string.crew_run_mate_paused, row.name) else row.name,
                row.state == MateState.RUNNING && !mine.isPaused
            )
        }
    }
    val mateFigure: @Composable (Boolean) -> Unit = { moving ->
        RunkkRunningFigure(moving, runFrameMillis(null), character = AvatarCharacter.of(null), isFacingLeft = isLeft)
    }
    // 실행 중 지도 오류가 나면 격자로 바꾼다(D-340)
    var isMapFailed by remember { mutableStateOf(false) }
    if (canShowKakaoMap(LocalKakaoMapKey.current, isMapFailed)) {
        KakaoRunMap(
            route = route,
            modifier = Modifier.fillMaxSize(),
            others = mates.map { m -> RunMapMarker(m.point, label = m.label, figure = { mateFigure(m.isMoving) }) },
            myLabel = myLabel,
            onMapError = { isMapFailed = true }
        ) { RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft) }
        return
    }
    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp)) { RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft) }
        }
        return
    }
    val markers = mates.map { m ->
        RouteMarker(m.point, label = m.label, tag = OPPONENT_MARKER_TAG, figure = { mateFigure(m.isMoving) })
    } +
        RouteMarker(point = me, label = myLabel, figure = {
            RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft)
        })
    RouteCanvas(route = route, markers = markers, modifier = Modifier.fillMaxSize())
}

/** 아래 단추: 길게 눌러 종료(짧게 누르면 안내, 길게 누르면 그만두기 확인 F-15d)와 일시정지·재개. */
@Composable
private fun RunControls(state: CrewRunUiState, actions: CrewRunActions, isPaused: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        RunkkLongPressButton(
            onTap = actions.onQuitTap,
            onLongPress = actions.onQuitLongPress,
            tapLabel = stringResource(R.string.crew_run_finish_tap_label),
            longPressLabel = stringResource(R.string.crew_run_hold_to_end),
            contentDescription = stringResource(R.string.crew_run_quit_description),
            caption = stringResource(R.string.crew_run_hold_to_end),
            size = BIG_BUTTON,
            borderColor = RunkkColors.LimeFill,
            borderWidth = 0.dp,
            background = RunkkColors.LimeFill
        ) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.TextPrimary))
        }
        val label = stringResource(if (isPaused) R.string.crew_run_resume else R.string.crew_run_pause)
        Box(
            modifier = Modifier
                .size(BIG_BUTTON)
                .clip(CircleShape)
                .background(RunkkColors.Surface)
                .border(2.dp, if (isPaused) RunkkColors.Surface else RunkkColors.BorderStrong, CircleShape)
                .clickable(
                    enabled = !state.isConfirmingQuit,
                    role = Role.Button,
                    onClickLabel = label,
                    onClick = if (isPaused) actions.onResume else actions.onPause
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPaused) RunkkIcons.Play else RunkkIcons.Pause,
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(BIG_BUTTON / 3)
            )
        }
    }
}

/** 일시정지(F-15c): 반투명 막 위에 종료·재개. 보조 기술에는 "일시정지" 화면으로 알린다. */
@Composable
private fun PausedOverlay(state: CrewRunUiState, actions: CrewRunActions) {
    val paneTitle = stringResource(R.string.crew_run_pause)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = PAUSE_SCRIM_ALPHA))
            .semantics { this.paneTitle = paneTitle }
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        RunControls(state, actions, isPaused = true)
    }
}

/** 남은 시간 "12:34" 또는 "1:02:03". */
internal fun untilLabel(left: Duration?): String = left?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY

private const val LOBBY_COLUMNS = 3
private const val PAUSE_SCRIM_ALPHA = 0.45f
private val BIG_BUTTON = 84.dp
