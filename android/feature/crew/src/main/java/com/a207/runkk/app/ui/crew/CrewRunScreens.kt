package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.a207.runkk.app.core.designsystem.RunPermissionEffect
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkHudTone
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkPauseScrim
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkRoundControl
import com.a207.runkk.app.core.designsystem.RunkkRunDistance
import com.a207.runkk.app.core.designsystem.RunkkRunStat
import com.a207.runkk.app.core.designsystem.RunkkRunStats
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.RunkkStopSquare
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.canShowKakaoMap
import com.a207.runkk.app.core.designsystem.rememberHeadingLeft
import com.a207.runkk.app.core.designsystem.rememberIsMoving
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.designsystem.runMotionSpeed
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunAvatarSlot
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.core.navigation.isSlotFilled
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 대기실·진행(합산 방식, 피그마 F-14~F-15d) ----------

@Composable
fun CrewRunRoute(key: Routes.CrewRunLobby, viewModel: CrewRunViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 진행 알림·신체 활동(걸음 센서 케이던스) 권한을 출발 카운트다운에서 한 번 묻는다. 진행 중에 다시 들어온 경우도 묻는다.
    RunPermissionEffect(shouldAsk = state.phase == CrewRunPhase.COUNTDOWN || state.phase == CrewRunPhase.RUNNING)
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

            // 취소(F-14c)도 대기실 틀이다. 대기실에 들어가기 전에 취소를 받았으면(대기실 없음) 사유와 돌아가기만 보인다.
            CrewRunPhase.CANCELLED -> {
                val hasLobby = state.lobby != null
                if (hasLobby) LobbyContent(state, actions) else CancelledBody(state, actions)
            }
        }
    }
    if (state.isConfirmingQuit) CrewQuitDialog(onConfirm = actions.onQuitConfirm, onDismiss = actions.onQuitDismiss)
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

/** 대기실 없이 받은 취소: 서버가 보낸 사유 문구를 그대로 보이고 크루런 일정으로 돌아간다. */
@Composable
private fun CancelledBody(state: CrewRunUiState, actions: CrewRunActions) {
    Column(Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.crew_run_lobby_title))
        RunkkErrorState(
            state.cancelledReason ?: stringResource(R.string.crew_run_cancelled),
            onRetry = actions.onClose,
            retryLabel = stringResource(R.string.crew_run_cancelled_to_schedule),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 칩 「● 크루런 5km」(피그마 RunKK / Run / 러닝 라벨, F-14·F-15): 높이 28, 좌우 10, 모서리 14, surfaceHigh 바탕, 12 Regular 본문색.
 * 가운뎃점은 쓰지 않는다. 거리가 없는 크루런은 「제한 없음」([임시:D-성-28]), 거리를 아직 모르면(대기실·결과 응답 전) 「크루런」만이다.
 */
@Composable
private fun CrewRunChip(distance: Distance?, isDistanceKnown: Boolean) {
    val distanceText = when {
        distance != null -> RunkkFormat.targetLabel(distance)
        isDistanceKnown -> stringResource(R.string.crew_run_distance_unlimited)
        else -> null
    }
    val label = distanceText?.let { stringResource(R.string.crew_run_chip, it) }
        ?: stringResource(R.string.crew_run_live_title)
    val spoken = RunkkFormat.spokenLabel(label)
    Row(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 10.dp)
            .semantics(mergeDescendants = true) { if (spoken != label) contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(RunkkColors.TextPrimary))
        Text(label, style = RunkkTextStyles.text(12, lineHeight = 14), color = RunkkColors.TextPrimary)
    }
}

/**
 * 대기실(F-14·F-14b)과 취소(F-14c): 칩과 나가기, 모집 시각·출발까지 남은 시간, 참가자 3열 격자(나를 첫 칸, 얼굴 원 또는 미입장 점선 원,
 * 이름, 준비 완료·대기 중·미입장)와 「준비 완료 n / m」, 아래 준비 완료 단추(준비하면 회색, 다시 누르면 풀린다). 취소면 남은 시간 0:00 아래에
 * 「출발까지」 대신 서버 취소 사유를 보이고, 아래 단추와 나가기가 크루런 일정으로 돌아간다. 여백은 피그마(머리 24, 본문 20, 아래 12/32)다.
 */
@Composable
private fun LobbyContent(state: CrewRunUiState, actions: CrewRunActions) {
    val lobby = state.lobby ?: return
    val isCancelled = state.phase == CrewRunPhase.CANCELLED
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CrewRunChip(state.distance, isDistanceKnown = true)
            Spacer(Modifier.weight(1f))
            RunkkPillButton(
                stringResource(R.string.crew_run_lobby_leave),
                onClick = if (isCancelled) actions.onClose else actions.onLeave,
                isEnabled = isCancelled || !state.isLeaving
            )
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 3.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LobbyUntilStart(state, lobby)
            Participants(lobby, state.mateFaces)
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        val bottom = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        if (isCancelled) {
            RunkkButton(
                stringResource(R.string.crew_run_cancelled_to_schedule),
                onClick = actions.onClose,
                modifier = bottom
            )
        } else {
            LobbyReadyButton(state, actions, bottom)
        }
    }
}

/** 「오늘 오후 9:00」(12), 큰 남은 시간(48 Bold), 「출발까지」(13, 취소면 서버 취소 사유). */
@Composable
private fun LobbyUntilStart(state: CrewRunUiState, lobby: Lobby) {
    val meet = lobbyMeetLabel(lobby.meetAt)
    val cancelled = if (state.phase == CrewRunPhase.CANCELLED) {
        state.cancelledReason ?: stringResource(R.string.crew_run_cancelled)
    } else {
        null
    }
    val untilSpoken = state.untilStart?.let { RunkkFormat.durationSpoken(it) }
        ?: stringResource(R.string.crew_run_lobby_until_checking)
    val description = cancelled?.let { "$it, $meet" }
        ?: stringResource(R.string.crew_run_lobby_until_description, untilSpoken, meet)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics {
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(meet, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
        Text(
            untilLabel(state.untilStart),
            style = RunkkTextStyles.display(48, lineHeightRatio = 1f),
            color = RunkkColors.TextPrimary
        )
        Text(
            cancelled ?: stringResource(R.string.crew_run_lobby_until_label),
            style = RunkkTextStyles.text(13, lineHeight = 16),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center
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

/**
 * 준비 완료 단추(켬/끔, Large 52 알약). 준비 전은 라임, 준비하면 회색(Gray: surfaceHigh·보조 글자, F-14b)이고 다시 누르면 풀린다(FR-147).
 * 거리 없는 크루런(제한 없음)도 누를 수 있다.
 */
@Composable
private fun LobbyReadyButton(state: CrewRunUiState, actions: CrewRunActions, modifier: Modifier) {
    val readyState = stringResource(
        if (state.isReady) R.string.crew_run_lobby_ready_state_on else R.string.crew_run_lobby_ready_state_off
    )
    // 다시 누르면 풀리는 켬/끔이라 스위치로 알린다.
    val switch = modifier.semantics {
        role = Role.Switch
        stateDescription = readyState
    }
    RunkkButton(
        stringResource(R.string.crew_run_ready),
        onClick = actions.onReady,
        modifier = switch,
        style = if (state.isReady) RunkkButtonStyle.Gray else RunkkButtonStyle.Lime,
        isLoading = state.isBusy
    )
}

/** 참가자(F-14): 「참가자」(15 Bold), 「준비 완료 n / m」(12), 3열 격자(칸 사이 14). */
@Composable
private fun Participants(lobby: Lobby, faces: Map<String, List<FaceLayer>>) {
    val readyCount = lobby.participants.count { it.isReady }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                stringResource(R.string.crew_run_lobby_participants),
                style = RunkkTextStyles.text(15, FontWeight.Bold, lineHeight = 18),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_run_lobby_ready_count, readyCount, lobby.participants.size),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextPrimary
            )
        }
        // 나를 첫 칸에 둔다(피그마 F-14). 나머지는 서버가 준 입장 순 그대로다.
        Column(verticalArrangement = Arrangement.spacedBy(LOBBY_GRID_GAP)) {
            lobby.participants.sortedByDescending { it.isMe }.chunked(LOBBY_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LOBBY_GRID_GAP)) {
                    row.forEach { p -> ParticipantCell(p, faces, Modifier.weight(1f)) }
                    repeat(LOBBY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 참가자 한 칸: 원 66, 이름(13, 나는 「나」), 준비 완료(12 Bold)·대기 중·미입장(12). 사이 간격 6. */
@Composable
private fun ParticipantCell(p: Lobby.Participant, faces: Map<String, List<FaceLayer>>, modifier: Modifier = Modifier) {
    val name = if (p.isMe) stringResource(R.string.crew_member_me) else p.displayName
    val status = stringResource(
        when {
            p.isReady -> R.string.crew_run_ready
            p.isEntered -> R.string.crew_run_participant_waiting
            else -> R.string.crew_run_participant_not_entered
        }
    )
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LobbySeat(p, faces)
        Text(
            name,
            style = RunkkTextStyles.text(13, lineHeight = 16),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            status,
            style = RunkkTextStyles.text(12, if (p.isReady) FontWeight.Bold else FontWeight.Normal, lineHeight = 18),
            color = RunkkColors.TextPrimary
        )
    }
}

/**
 * 참가자 원 66([임시:D-성-12]): 들어온 사람은 surfaceHigh 원 안에 아바타 얼굴(셀카 부위 그림, 없으면 얼굴 키·표시 성별의 기본 얼굴),
 * 아직 안 들어온 자리는 바탕 없는 점선 원(외곽선색)이다. 장식이라 읽기 정보는 칸이 붙인다.
 */
@Composable
private fun LobbySeat(p: Lobby.Participant, faces: Map<String, List<FaceLayer>>) {
    if (!p.isEntered) {
        val dash = RunkkColors.Outline
        Box(
            Modifier
                .size(LOBBY_SEAT)
                .testTag(LOBBY_EMPTY_SEAT_TAG)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    val dashLength = 4.dp.toPx()
                    drawCircle(
                        color = dash,
                        radius = (size.minDimension - stroke) / 2,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
                        )
                    )
                }
        )
        return
    }
    RunkkAvatarCircle(
        faceKey = p.faceKey,
        size = LOBBY_SEAT,
        modifier = Modifier.testTag(LOBBY_FACE_SEAT_TAG),
        faceLayers = p.faceKey?.let(faces::get).orEmpty(),
        character = AvatarCharacter.of(p.shownGender)
    )
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
 * 진행(F-15)·일시정지(F-15c): 칩(64 칸 맨 위), 큰 내 거리, 경과·현재 페이스·평균 페이스, 지도(모서리·테두리 없이 가장자리 24 페이드,
 * 내 아바타와 크루원 아바타), 「함께 달리는 크루원 n명」, 길게 눌러 종료와 일시정지(사이 56). 정지 중에는 덮개(scrim)를 덮고 종료·재개만
 * 받는다(개인 러닝 B-4와 같다). 여백은 피그마(위 20, 좌우 24, 아래 36, 블록 사이 16)다.
 */
@Composable
private fun RunningContent(state: CrewRunUiState, actions: CrewRunActions) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(CHIP_AREA)) {
                CrewRunChip(state.distance, isDistanceKnown = state.lobby != null)
            }
            BigDistance(state.mine.distance)
            RunStats(state.mine)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CrewRunMap(state)
                MapEdgeFade(Modifier.matchParentSize())
                state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.BottomCenter)) }
            }
            Text(
                stringResource(R.string.crew_run_together, state.runningMates),
                style = RunkkTextStyles.text(14, lineHeight = 17),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            )
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            // 정지 중에도 자리를 지켜 위 줄이 내려앉지 않게 하고, 단추는 덮개 위(PausedOverlay)에 다시 그린다.
            val hidden = if (state.isPaused) Modifier.graphicsLayer { alpha = 0f }.clearAndSetSemantics {} else Modifier
            Box(hidden) { RunControls(state, actions, isPaused = false) }
        }
        if (state.isPaused) PausedOverlay(state, actions)
    }
}

/** 큰 거리 「3.22」(96 Bold)와 오른쪽 아래 「km」(20, 본문색). 칸 높이 82 이상(고정하면 줄 높이 72보다 큰 글자가 잘린다), 위 8. */
@Composable
private fun BigDistance(distance: Distance) {
    RunkkRunDistance(
        distance = RunkkFormat.km(distance),
        unit = stringResource(R.string.crew_run_unit_km),
        spoken = stringResource(R.string.crew_run_km_spoken, RunkkFormat.km(distance)),
        modifier = Modifier.heightIn(min = BIG_DISTANCE_HEIGHT).padding(top = 8.dp),
        tone = RunkkHudTone.Crew
    )
}

/** 경과·현재 페이스·평균 페이스: 3등분, 값 26 Bold, 라벨 12 본문색, 사이 2. */
@Composable
private fun RunStats(mine: CrewRunProgress) {
    RunkkRunStats(
        listOf(
            RunkkRunStat(
                RunkkFormat.duration(mine.elapsed),
                stringResource(R.string.crew_run_stat_elapsed),
                RunkkFormat.durationSpoken(mine.elapsed)
            ),
            RunkkRunStat(
                RunkkFormat.pace(mine.currentPace),
                stringResource(R.string.crew_run_stat_current_pace),
                RunkkFormat.paceSpoken(mine.currentPace)
            ),
            RunkkRunStat(
                RunkkFormat.pace(mine.averagePace),
                stringResource(R.string.crew_run_stat_average_pace),
                RunkkFormat.paceSpoken(mine.averagePace)
            )
        ),
        tone = RunkkHudTone.Crew
    )
}

/**
 * 지도와 아바타(F-15): 경로 끝에 내 아바타(58), 아직 끝내지 않은 크루원(45)은 내 경로 위 거리 차만큼 떨어진 지점(OpponentMarkerPlacer, 대결
 * 상대와 같은 방식). 크루원 캐릭터는 그 크루원의 표시 성별(대기실 응답, D-327)이다. 피그마처럼 이름표는 없고, 멈춘 사람만 「이름 멈춤」
 * 표시를 단다(각자 실제 상태, [임시:D-성-13]). Kakao 지도를 띄울 수 없으면 격자 바탕 경로선이다. 모서리·테두리는 없다.
 */
@Composable
private fun CrewRunMap(state: CrewRunUiState) {
    val mine = state.mine
    val route = mine.route
    val me = route.points.lastOrNull()
    val isMoving = rememberIsMoving(mine.distance) && !mine.isPaused && mine.currentPace != null
    val frameMillis = runFrameMillis(mine.currentPace)
    val isLeft = rememberHeadingLeft(route)
    // 내 아바타 칸(design-oo 05-30 7절, D-856, PM 2026-11-15): 자유런·대결과 같이 크루런 중이어도 일시정지했거나, 멈춰 있거나(거리가 3초
    // 늘지 않음), 페이스를 모르면 STAND이고, 달리기 동작은 내 현재 페이스 빠르기로 돈다. 칸이 있으면 내 칸만 88dp다(크루원 그림 칸은 그대로)
    val pose = if (state.phase == CrewRunPhase.RUNNING && isMoving) AvatarSlotPose.RUN else AvatarSlotPose.STAND
    val runSpeed = runMotionSpeed(mine.currentPace)
    val myFigureSize = if (isSlotFilled(SlotKey.RUN_AVATAR)) MY_AVATAR_SLOT else MY_FIGURE
    val myFigure: @Composable () -> Unit = {
        RunAvatarSlot(pose, isLeft, runSpeed) { RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft) }
    }
    val myLabel = if (mine.isPaused) {
        stringResource(R.string.crew_run_mate_paused, stringResource(R.string.crew_member_me))
    } else {
        null
    }
    val resources = LocalContext.current.resources
    val mates = state.relay.filter { !it.isMe && it.isRunning }.mapNotNull { row ->
        val gap = (mine.distance.meters - row.distance.meters).toInt()
        OpponentMarkerPlacer.place(route, mine.distance, gap)?.let { point ->
            val isPaused = row.state == MateState.PAUSED
            MateMarker(
                point,
                if (isPaused) resources.getString(R.string.crew_run_mate_paused, row.name) else null,
                row.state == MateState.RUNNING && !mine.isPaused,
                AvatarCharacter.of(row.shownGender)
            )
        }
    }
    // 크루원 그림은 내 그림 칸(58) 바닥 가운데에 45로 작게 그린다.
    val mateFigure: @Composable (MateMarker) -> Unit = { m ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.size(MATE_FIGURE)) {
                RunkkRunningFigure(m.isMoving, runFrameMillis(null), character = m.character, isFacingLeft = isLeft)
            }
        }
    }
    // 실행 중 지도 오류가 나면 격자로 바꾼다(D-340)
    var isMapFailed by remember { mutableStateOf(false) }
    if (canShowKakaoMap(LocalKakaoMapKey.current, isMapFailed)) {
        KakaoRunMap(
            route = route,
            modifier = Modifier.fillMaxSize(),
            markerSize = MY_FIGURE,
            others = mates.map { m -> RunMapMarker(m.point, label = m.label, figure = { mateFigure(m) }) },
            myMarkerSize = myFigureSize,
            myLabel = myLabel,
            onMapError = { isMapFailed = true },
            isFramed = false
        ) { myFigure() }
        return
    }
    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(myFigureSize)) { myFigure() }
        }
        return
    }
    val markers = mates.map { m ->
        RouteMarker(m.point, label = m.label, tag = OPPONENT_MARKER_TAG, figure = { mateFigure(m) })
    } +
        RouteMarker(point = me, label = myLabel, figure = myFigure, size = myFigureSize)
    RouteCanvas(
        route = route,
        markers = markers,
        modifier = Modifier.fillMaxSize(),
        markerSize = MY_FIGURE,
        isFramed = false
    )
}

/** 지도 가장자리 페이드(피그마 사방 24 알파 마스크): 바탕색에서 투명으로 가는 띠 넷을 지도 위에 덮는다(지도 뷰는 마스크를 받지 않는다). */
@Composable
private fun MapEdgeFade(modifier: Modifier = Modifier) {
    val edge = RunkkColors.Background
    Box(
        modifier.clearAndSetSemantics {}.drawBehind {
            val fade = MAP_FADE.toPx().coerceAtMost(size.minDimension / 2)
            val clear = edge.copy(alpha = 0f)
            drawRect(
                Brush.verticalGradient(listOf(edge, clear), startY = 0f, endY = fade),
                size = Size(size.width, fade)
            )
            drawRect(
                Brush.verticalGradient(listOf(clear, edge), startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade)
            )
            drawRect(
                Brush.horizontalGradient(listOf(edge, clear), startX = 0f, endX = fade),
                size = Size(fade, size.height)
            )
            drawRect(
                Brush.horizontalGradient(listOf(clear, edge), startX = size.width - fade, endX = size.width),
                topLeft = Offset(size.width - fade, 0f),
                size = Size(fade, size.height)
            )
        }
    )
}

/**
 * 아래 단추(F-15): 가운데 정렬, 사이 56. 길게 눌러 종료(라임 84, 정지 사각 28 모서리 7, 아래 14에 「길게 눌러 종료」 14)와
 * 일시정지(흰 84, 테두리 2.33 외곽선색, 막대 둘)·재개(흰 84, 테두리 없음, 재생). 덮개 위([isPaused])에서는 캡션이 흰색이다.
 */
@Composable
private fun RunControls(state: CrewRunUiState, actions: CrewRunActions, isPaused: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RunkkLongPressButton(
                onTap = actions.onQuitTap,
                onLongPress = actions.onQuitLongPress,
                tapLabel = stringResource(R.string.crew_run_finish_tap_label),
                longPressLabel = stringResource(R.string.crew_run_hold_to_end),
                contentDescription = stringResource(R.string.crew_run_quit_description),
                size = BIG_BUTTON,
                borderColor = RunkkColors.LimeFill,
                borderWidth = 0.dp,
                background = RunkkColors.LimeFill
            ) {
                // 라임 위 사각형은 두 테마 모두 짙은 색이다(다크에서 흰 사각형이면 대비가 낮다).
                RunkkStopSquare(size = 28.dp, corner = 7.dp, color = RunkkColors.OnLimeFill)
            }
            Text(
                stringResource(R.string.crew_run_hold_to_end),
                style = RunkkTextStyles.text(14),
                // 덮개 위에서는 흰 글자(피그마 F-15c), 두 테마 모두 덮개가 어둡다.
                color = if (isPaused) Color.White else RunkkColors.TextSecondary,
                modifier = Modifier.padding(top = 14.dp).clearAndSetSemantics {}
            )
        }
        RunkkRoundControl(
            icon = if (isPaused) RunkkIcons.Play else CrewPauseIcon,
            contentDescription = stringResource(if (isPaused) R.string.crew_run_resume else R.string.crew_run_pause),
            onClick = if (isPaused) actions.onResume else actions.onPause,
            size = BIG_BUTTON,
            iconSize = BIG_ICON,
            borderColor = RunkkColors.Outline,
            borderWidth = if (isPaused) 0.dp else PAUSE_BORDER,
            isEnabled = !state.isConfirmingQuit
        )
    }
}

/** 일시정지(F-15c): 덮개(scrim, #171B12 45%) 위에 종료·재개. 보조 기술에는 「일시정지」 화면으로 알린다. */
@Composable
private fun PausedOverlay(state: CrewRunUiState, actions: CrewRunActions) {
    RunkkPauseScrim(paneTitle = stringResource(R.string.crew_run_pause), tone = RunkkHudTone.Crew) {
        RunControls(state, actions, isPaused = true)
    }
}

/** 남은 시간(피그마 F-14 「12:34」, F-14c 「0:00」): 분은 앞자리 0 없이, 한 시간 이상이면 「1:02:03」. 모르면 「-」. */
internal fun untilLabel(left: Duration?): String {
    val seconds = left?.seconds?.coerceAtLeast(0) ?: return RunkkFormat.EMPTY
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

/** 대기실 참가자 원 시험 표지: 얼굴 원, 미입장 점선 원. */
internal const val LOBBY_FACE_SEAT_TAG = "crew-run-seat-face"
internal const val LOBBY_EMPTY_SEAT_TAG = "crew-run-seat-empty"

private const val LOBBY_COLUMNS = 3
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private val LOBBY_SEAT = 66.dp
private val LOBBY_GRID_GAP = 14.dp
private val CHIP_AREA = 64.dp
private val BIG_DISTANCE_HEIGHT = 82.dp
private val MY_FIGURE = 58.dp

/** 내 Rive 아바타 칸(SlotKey.RUN_AVATAR가 있을 때, [임시:D-성-121]). 크루원 그림 칸은 [MY_FIGURE] 그대로다. */
private val MY_AVATAR_SLOT = 88.dp
private val MATE_FIGURE = 45.dp
private val MAP_FADE = 24.dp
private val BIG_BUTTON = 84.dp
private val BIG_ICON = 28.dp
private val CONTROL_GAP = 56.dp
private val PAUSE_BORDER = 2.33.dp

/** 크루런 일시정지 아이콘(피그마 RunKK / Icon / 일시정지): 28 칸에 막대 둘(6.4×28, 모서리 1.4, 사이 5.25). 공용 Pause보다 막대가 굵고 길다. */
private val CrewPauseIcon: ImageVector by lazy {
    ImageVector.Builder("CrewPause", 28.dp, 28.dp, 28f, 28f).addPath(
        pathData = PathParser().parsePathString(
            "M9 0h3.6a1.4 1.4 0 0 1 1.4 1.4v25.2a1.4 1.4 0 0 1-1.4 1.4H9a1.4 1.4 0 0 1-1.4-1.4V1.4A1.4 1.4 0 0 1 9 0z" +
                "M20.65 0h3.6a1.4 1.4 0 0 1 1.4 1.4v25.2a1.4 1.4 0 0 1-1.4 1.4h-3.6a1.4 1.4 0 0 1-1.4-1.4V1.4" +
                "A1.4 1.4 0 0 1 20.65 0z"
        ).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()
}
