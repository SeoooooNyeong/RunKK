package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarStageBackdrop
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalKakaoMapKey
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ProgressDot
import com.a207.runkk.app.core.designsystem.RunPermissionEffect
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
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
import com.a207.runkk.app.core.designsystem.RunkkMeMap
import com.a207.runkk.app.core.designsystem.RunkkPauseScrim
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPullToRefresh
import com.a207.runkk.app.core.designsystem.RunkkRoundControl
import com.a207.runkk.app.core.designsystem.RunkkRunDistance
import com.a207.runkk.app.core.designsystem.RunkkRunLabel
import com.a207.runkk.app.core.designsystem.RunkkRunStat
import com.a207.runkk.app.core.designsystem.RunkkRunStats
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatusChip
import com.a207.runkk.app.core.designsystem.RunkkStatusChipTone
import com.a207.runkk.app.core.designsystem.RunkkStopSquare
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.RunkkTrackCard
import com.a207.runkk.app.core.designsystem.RunkkTrackProgressBar
import com.a207.runkk.app.core.designsystem.RunkkViewModeToggle
import com.a207.runkk.app.core.designsystem.TrackHorizonAlignment
import com.a207.runkk.app.core.designsystem.TrackLayout
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.canShowKakaoMap
import com.a207.runkk.app.core.designsystem.rememberIsMoving
import com.a207.runkk.app.core.designsystem.rememberRunPowerSaveStart
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.designsystem.runMotionSpeed
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Gender
import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.model.RunnerLook
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunAvatarSlot
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import com.a207.runkk.app.core.navigation.SlotRunnerLook
import com.a207.runkk.app.domain.crew.CrewRunProgress
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.CrewRunStanding
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// ---------- 대기실·진행(합산 방식, 피그마 F-14~F-15d) ----------

@Composable
fun CrewRunRoute(key: Routes.CrewRunLobby, viewModel: CrewRunViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    // 보이는 동안만 변경 신호를 받고, 가려졌다 돌아오면 대기실을 다시 읽는다(설계 05-32 [임시:FR-성-372]).
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
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
    val onReady = rememberCrewRunReady(viewModel, isLobby = state.phase == CrewRunPhase.LOBBY)
    val actions = remember(viewModel, onReady) {
        CrewRunActions(
            onReady = onReady,
            onRefresh = viewModel::enter,
            onLeave = viewModel::onLeaveLobby,
            onQuitTap = viewModel::onQuitTapped,
            onQuitLongPress = viewModel::onQuitLongPressed,
            onQuitConfirm = viewModel::onQuitConfirmed,
            onQuitDismiss = viewModel::onQuitDismissed,
            onPause = viewModel::onPause,
            onResume = viewModel::onResume,
            onClose = viewModel::onBack,
            onBack = viewModel::onBack,
            onViewModeChange = viewModel::onViewModeChange,
            onStartNow = viewModel::onStartNow,
            onJoin = viewModel::onJoin,
            onJoinLeave = viewModel::onJoinDismissed,
            onPullRefresh = viewModel::onPullRefresh
        )
    }
    CrewRunScreen(state = state, actions = actions)
}

/**
 * 준비 완료 단추 처리([임시:FR-성-380], [임시:D-성-902]). 준비 전이면 절전 모드 안내를 거쳐 준비하고, 준비를 풀 때는 묻지 않는다.
 * 이미 준비한 채 대기실에 다시 들어오면([isLobby]가 처음 참이 될 때) 한 번 확인하되 준비 상태는 뒤집지 않는다.
 */
@Composable
private fun rememberCrewRunReady(viewModel: CrewRunViewModel, isLobby: Boolean): () -> Unit {
    val readyStart = rememberRunPowerSaveStart { if (!viewModel.state.value.isReady) viewModel.onReadyToggle() }
    var isEntryChecked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isLobby) {
        if (isLobby && !isEntryChecked) {
            isEntryChecked = true
            if (viewModel.state.value.isReady) readyStart()
        }
    }
    return remember(viewModel, readyStart) {
        { if (viewModel.state.value.isReady) viewModel.onReadyToggle() else readyStart() }
    }
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
    val onBack: () -> Unit = {},
    /** 「트랙 | 지도」 전환([임시:FR-성-28]). */
    val onViewModeChange: (RunViewMode) -> Unit = {},
    /** 모집자 「지금 시작」(설계 05-32). */
    val onStartNow: () -> Unit = {},
    /** 출발 시트 「함께 뛰기」. */
    val onJoin: () -> Unit = {},
    /** 출발 시트 「나가기」. */
    val onJoinLeave: () -> Unit = {},
    /** 대기실 당겨서 새로고침. */
    val onPullRefresh: () -> Unit = {}
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

            // 카운트다운은 대기실 틀 위 링이다(설계 05-32 흐름 1). 대기실 응답 없이 받았으면 큰 숫자만 보인다.
            CrewRunPhase.COUNTDOWN -> if (state.lobby != null) LobbyContent(state, actions) else CountdownBody(state)

            // 출발 뒤 참여하지 않은 입장자: 대기실 위에 출발 시트(「함께 뛰기」·「나가기」).
            CrewRunPhase.JOIN_CHOICE -> LobbyContent(state, actions)

            CrewRunPhase.RUNNING, CrewRunPhase.FINISHED -> RunningContent(state, actions)

            // 취소(F-14c)도 대기실 틀이다. 대기실에 들어가기 전에 취소를 받았으면(대기실 없음) 사유와 돌아가기만 보인다.
            CrewRunPhase.CANCELLED -> {
                val hasLobby = state.lobby != null
                if (hasLobby) LobbyContent(state, actions) else CancelledBody(state, actions)
            }
        }
    }
    if (state.isConfirmingQuit) CrewQuitDialog(onConfirm = actions.onQuitConfirm, onDismiss = actions.onQuitDismiss)
    if (state.phase == CrewRunPhase.JOIN_CHOICE) DepartureSheet(state, actions)
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
 * 가운뎃점은 쓰지 않는다. 거리가 없는 크루런은 「제한 없음」(D-660), 거리를 아직 모르면(대기실·결과 응답 전) 「크루런」만이다.
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
    RunkkRunLabel(label)
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
    val isCountdown = state.phase == CrewRunPhase.COUNTDOWN
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
        // 당겨서 새로고침(설계 05-32): 놓친 변경을 맞춘다. 취소된 대기실은 다시 읽지 않는다.
        RunkkPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onPullRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 3.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isCountdown) LobbyCountdown(state, lobby) else LobbyUntilStart(state, lobby)
                Participants(lobby, state.mateFaces)
                state.error?.takeIf { state.phase != CrewRunPhase.JOIN_CHOICE }?.let {
                    Text(
                        it.asString(),
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.Lavender,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        val bottom = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        when {
            isCancelled -> RunkkButton(
                stringResource(R.string.crew_run_cancelled_to_schedule),
                onClick = actions.onClose,
                modifier = bottom
            )

            // 출발 시트가 아래를 덮는다.
            state.phase == CrewRunPhase.JOIN_CHOICE -> Spacer(bottom.height(52.dp))

            // 카운트다운 중 준비를 풀면 출발 후보에서만 빠지고 대기실에 남는다([임시:D-성-882]).
            isCountdown && state.isReady -> RunkkButton(
                stringResource(R.string.crew_run_countdown_cancel),
                onClick = actions.onReady,
                modifier = bottom,
                style = RunkkButtonStyle.Outline,
                isLoading = state.isBusy
            )

            state.canStartNow -> Column(bottom, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkButton(
                    stringResource(R.string.crew_run_start_now),
                    onClick = actions.onStartNow,
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = state.isStarting
                )
                LobbyReadyButton(state, actions, Modifier.fillMaxWidth())
            }

            else -> LobbyReadyButton(state, actions, bottom)
        }
    }
}

/**
 * 전원 준비·지금 시작의 출발 카운트다운(설계 05-32 흐름 1, 피그마 62514:2897): 모집 시각(12), 「모두 준비됐어요」(20 Bold), 남은 초를
 * 담은 라임 링(지름 132, 두께 10, 남은 몫만큼 채운다), 「초 뒤 출발」. 남은 초는 측정 시작 절대시각(startAt)까지다.
 */
@Composable
private fun LobbyCountdown(state: CrewRunUiState, lobby: Lobby) {
    val seconds = state.countdown ?: COUNTDOWN_SECONDS
    val spoken = stringResource(R.string.crew_run_countdown_spoken, seconds.toInt())
    val track = RunkkColors.SurfaceHigh
    val fill = RunkkColors.LimeFill
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics {
            contentDescription = spoken
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(lobbyMeetLabel(lobby.meetAt), style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.crew_run_countdown_all_ready),
            style = RunkkTextStyles.Title,
            color = RunkkColors.TextPrimary
        )
        Box(
            modifier = Modifier.size(COUNTDOWN_RING).drawBehind {
                val stroke = COUNTDOWN_STROKE.toPx()
                val inset = stroke / 2
                val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, 0f, FULL_SWEEP, useCenter = false, topLeft, arc, style = Stroke(stroke))
                val share = (seconds.toFloat() / COUNTDOWN_SECONDS).coerceIn(0f, 1f)
                drawArc(fill, START_ANGLE, FULL_SWEEP * share, useCenter = false, topLeft, arc, style = Stroke(stroke))
            },
            contentAlignment = Alignment.Center
        ) {
            Text(
                seconds.toString(),
                style = RunkkTextStyles.display(64, lineHeightRatio = 1f),
                color = RunkkColors.TextPrimary
            )
        }
        Text(
            stringResource(R.string.crew_run_countdown_seconds_left),
            style = RunkkTextStyles.text(13, FontWeight.Bold, lineHeight = 16),
            color = RunkkColors.TextPrimary
        )
    }
}

/**
 * 출발 시트(설계 05-32 흐름 2, 피그마 62514:2914): 준비하지 않은 입장자에게 출발을 알린다. 「달리는 중」 칩, 「크루런이 시작됐어요」,
 * 「준비한 n명이 출발했어요. 지금 함께 뛰면 지금부터 기록해요.」, 「함께 뛰기」(라임)와 「나가기」(테두리). 바깥 누름·뒤로 가기도 나가기다.
 */
@Composable
private fun DepartureSheet(state: CrewRunUiState, actions: CrewRunActions) {
    RunkkBottomSheet(onDismissRequest = actions.onJoinLeave, hasDragHandle = true, hasCloseButton = false) {
        DepartureSheetContent(state, actions)
    }
}

/** 출발 시트의 내용(시트 창 없이 스냅샷으로도 그린다). */
@Composable
internal fun DepartureSheetContent(state: CrewRunUiState, actions: CrewRunActions) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RunkkStatusChip(stringResource(R.string.crew_run_departed_chip), RunkkStatusChipTone.Lime)
            Text(
                stringResource(R.string.crew_run_departed_title),
                style = RunkkTextStyles.SheetTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_run_departed_body, state.departedCount),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary
            )
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkButton(
                stringResource(R.string.crew_run_join_together),
                onClick = actions.onJoin,
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.isJoining
            )
            RunkkButton(
                stringResource(R.string.crew_run_lobby_leave),
                onClick = actions.onJoinLeave,
                modifier = Modifier.fillMaxWidth(),
                style = RunkkButtonStyle.Outline,
                isEnabled = !state.isJoining
            )
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
 * 참가자 원 66(D-644): 들어온 사람은 surfaceHigh 원 안에 아바타 얼굴(셀카 부위 그림, 없으면 얼굴 키·표시 성별의 기본 얼굴),
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
        character = lobbySeatCharacter(p, LocalAvatarCharacter.current)
    )
}

/**
 * 대기실 원의 기본 얼굴 남녀. 내 자리는 내 성별([mine])이다: 서버가 보내는 표시 성별은 성별을 공개하지 않으면 대체값이라, 그대로 쓰면
 * 내 얼굴이 다른 성별로 보인다. 다른 사람은 표시 성별 그대로다.
 */
internal fun lobbySeatCharacter(p: Lobby.Participant, mine: AvatarCharacter): AvatarCharacter =
    if (p.isMe) mine else AvatarCharacter.of(p.shownGender)

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
 * 진행(F-15)·일시정지(F-15c): 칩(64 칸 맨 위), 큰 내 거리, 경과·현재 페이스·평균 페이스, 트랙 카드(또는 지도, [임시:FR-성-30]), 순위 문장
 * (「8명 중 3위, 바로 뒤 한강달림과 12 m」), 길게 눌러 종료와 일시정지(사이 56). 정지 중에는 덮개(scrim)를 덮고 종료·재개만 받는다
 * (개인 러닝 B-4와 같다). 여백은 피그마(위 20, 좌우 24, 아래 36, 블록 사이 16)다.
 */
@Composable
private fun RunningContent(state: CrewRunUiState, actions: CrewRunActions) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(CHIP_AREA)) {
                CrewRunChip(state.distance, isDistanceKnown = state.lobby != null)
            }
            BigDistance(state.mine.distance)
            RunStats(state.mine)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CrewTrack(
                    state,
                    actions.onViewModeChange,
                    Modifier.fillMaxWidth().heightIn(max = TRACK_CARD_HEIGHT).fillMaxHeight()
                )
                state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.BottomCenter)) }
            }
            Text(
                standingSentence(state.standing),
                style = RunkkTextStyles.text(14, lineHeight = 17),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(CREW_STANDING_TAG)
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
 * 트랙 카드와 지도([임시:D-성-266], [임시:D-성-313], [임시:FR-성-30]). 대결과 같은 구성이다: 같은 자리 카드에 고른 보기를 그리고 진행
 * 막대와 「트랙 | 지도」 전환을 카드 위에 겹쳐 둔다. 지도를 띄울 수 없으면 전환을 숨기고 트랙만 그린다.
 *
 * - 트랙: 아래 레인은 나, 위 레인은 누적 거리가 나와 가장 가까운 크루원 한 명이다. 나머지는 그리지 않고 가장자리 칩(「앞 2명 ›」,
 *   「‹ 뒤 4명」)으로 센다. 가장 가까운 크루원이 화면 밖이면 그쪽 칩이 이름과 차이가 된다.
 * - 지도: 내가 달린 길과 내 얼굴만 그린다. 크루원은 그리지 않는다.
 * - 진행 막대: 참가 크루원을 모두 점으로 찍는다. 목표 거리가 없는 크루런은 가장 앞선 사람이 오른쪽 끝이다.
 */
@Composable
private fun CrewTrack(state: CrewRunUiState, onViewModeChange: (RunViewMode) -> Unit, modifier: Modifier = Modifier) {
    // 실행 중 지도 오류가 나면 트랙만 보인다(D-340)
    var isMapFailed by remember { mutableStateOf(false) }
    val isMapAvailable = canShowKakaoMap(LocalKakaoMapKey.current, isMapFailed)
    val mode = if (isMapAvailable) state.viewMode else RunViewMode.TRACK
    val mine = state.mine.distance
    val goal = state.distance
    Box(modifier.clip(RoundedCornerShape(26.dp))) {
        when (mode) {
            RunViewMode.TRACK -> CrewTrackView(state, Modifier.fillMaxSize(), if (isMapAvailable) 91.dp else 55.dp)

            RunViewMode.MAP -> RunkkMeMap(
                route = state.mine.route,
                myMarkerSize = MAP_FACE_SIZE,
                onMapError = { isMapFailed = true },
                modifier = Modifier.fillMaxSize().testTag(CREW_MAP_TAG),
                myLabel = stringResource(R.string.crew_member_me)
            ) { MyFace(state, MAP_FACE_SIZE) }
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RunkkTrackProgressBar(
                dots = progressDots(state),
                modifier = Modifier.fillMaxWidth(),
                leading = if (goal == null) {
                    stringResource(R.string.crew_run_track_distance, RunkkFormat.km(mine))
                } else {
                    stringResource(R.string.crew_run_track_progress, RunkkFormat.km(mine), goalKm(goal))
                },
                trailing = goal?.let {
                    val remaining = Distance((it.meters - mine.meters).coerceAtLeast(0))
                    stringResource(R.string.crew_run_track_remaining, RunkkFormat.km(remaining))
                }
            )
            if (isMapAvailable) RunkkViewModeToggle(selected = mode, onSelect = onViewModeChange)
        }
    }
}

/** 진행 막대의 목표 거리 숫자: 5000 m는 「5」, 21097 m는 「21.1」. */
private fun goalKm(distance: Distance): String = if (distance.meters % METERS_PER_KM ==
    0L
) {
    (distance.meters / METERS_PER_KM).toString()
} else {
    RunkkFormat.kmShort(distance)
}

/**
 * 진행 막대의 점: 참가 크루원 모두(끝낸 사람 포함)와 나. 목표 거리가 없으면 가장 앞선 사람의 거리를 오른쪽 끝으로 둔다. 내 점은 중계
 * 줄이 아니라 내 측정 거리로 찍는다(중계보다 새 값).
 */
internal fun progressDots(state: CrewRunUiState): List<ProgressDot> {
    val mine = state.mine.distance.meters
    val others = state.relay.filter { !it.isMe }
    val goal = (state.distance?.meters ?: (others.map { it.distance.meters } + mine).max()).coerceAtLeast(1).toFloat()
    return others.map { ProgressDot(it.distance.meters / goal, isMine = false) } +
        ProgressDot(mine / goal, isMine = true, faceKey = state.myAvatar?.faceKey)
}

/** 트랙 보기: 위치 계산은 매번 다시 한다(받은 거리값이 바로 다음 그림에 반영된다). */
@Composable
private fun CrewTrackView(state: CrewRunUiState, modifier: Modifier, overlayBottom: Dp) {
    val mine = state.mine
    val standing = state.standing
    val nearest = standing.nearest
    val layout = TrackLayout.of(
        aheadMeters = standing.nearestGapMeters?.toDouble(),
        remainingMeters = state.distance?.let { (it.meters - mine.distance.meters).toDouble() }
    )
    val edge = layout.other as? TrackLayout.Other.Edge
    val isMoving = rememberIsMoving(mine.distance) && !mine.isPaused && mine.currentPace != null
    val frameMillis = runFrameMillis(mine.currentPace)
    // 트랙에서는 오른쪽을 보고, 일시정지하거나 움직임과 페이스가 없으면 선다(D-856).
    val pose = if (state.phase == CrewRunPhase.RUNNING && isMoving) AvatarSlotPose.RUN else AvatarSlotPose.STAND
    val isNearestPaused = nearest?.state?.name == MateState.PAUSED.name
    val nearestName = nearest?.name.orEmpty()
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    RunkkTrackCard(
        layout = layout,
        myTag = stringResource(R.string.crew_member_me),
        // 멈춘 사람만 「이름 멈춤」으로 적는다(각자 실제 상태, [임시:D-성-13]).
        otherTag = nearest?.let {
            if (isNearestPaused) stringResource(R.string.crew_run_mate_paused, it.name) else it.name
        },
        myAvatar = {
            RunAvatarSlot(pose, facingLeft = false, runSpeed = runMotionSpeed(mine.currentPace)) {
                RunkkRunningFigure(isMoving, frameMillis)
            }
        },
        otherAvatar = {
            key(nearest?.memberId, nearest?.name) { NearestRunner(state) }
        },
        modifier = modifier.testTag(CREW_TRACK_TAG),
        myPace = mine.currentPace?.let { RunkkFormat.pace(it) },
        isMoving = isMoving,
        speedMetersPerSecond = mine.currentPace?.let { 1_000f / it.secondsPerKm } ?: 0f,
        overlayBottom = overlayBottom,
        aheadChip = when {
            edge != null && edge.isAhead -> stringResource(R.string.crew_run_track_edge_ahead, nearestName, edge.meters)
            standing.ahead > 0 -> stringResource(R.string.crew_run_track_ahead, standing.ahead)
            else -> null
        },
        behindChip = when {
            edge != null && !edge.isAhead -> stringResource(
                R.string.crew_run_track_edge_behind,
                nearestName,
                edge.meters
            )

            standing.behind > 0 -> stringResource(R.string.crew_run_track_behind, standing.behind)

            else -> null
        },
        rulerLabel = standing.nearestGapMeters?.let { stringResource(R.string.crew_run_track_ruler, abs(it)) },
        myFace = { MyFace(state, TAG_FACE_SIZE) },
        otherFace = nearest?.let { mate ->
            val row = state.relay.firstOrNull { it.memberId != null && it.memberId == mate.memberId }
            val faceKey = mate.memberId?.let(state.looks::get)?.faceKey
            return@let {
                RunkkAvatarCircle(
                    faceKey = faceKey,
                    size = TAG_FACE_SIZE,
                    faceLayers = faceKey?.let(state.mateFaces::get).orEmpty(),
                    character = AvatarCharacter.of(row?.shownGender)
                )
            }
        },
        backdrop = {
            if (isAvatarOpen) {
                AvatarStageBackdrop(
                    state.myAvatar?.backgroundKey,
                    Modifier.fillMaxSize(),
                    alignment = TrackHorizonAlignment
                )
            }
        }
    )
}

/** 내 얼굴 원(이름표 22, 지도 위 내 위치 44): 홈과 같은 내 얼굴이다. */
@Composable
private fun MyFace(state: CrewRunUiState, size: Dp) {
    RunkkAvatarCircle(
        faceKey = state.myAvatar?.faceKey,
        size = size,
        faceLayers = state.myAvatar?.faceLayers.orEmpty(),
        borderColor = RunkkColors.Lime
    )
}

/**
 * 위 레인의 가장 가까운 크루원([임시:D-성-259], [임시:D-성-266]): 대기실에서 받은 모습으로 달리는 아바타를 그린다. 모습이 없으면(대기실에
 * 들어오지 못한 참가자, 옛 서버) 표시 성별의 기본 모습이고, 아바타 칸이 비었거나 그리지 못하면 그 성별의 달리기 그림이다. 멈춘 사람은 선다.
 */
@Composable
private fun NearestRunner(state: CrewRunUiState) {
    val mate = state.standing.nearest ?: return
    val row = state.relay.firstOrNull { it.memberId != null && it.memberId == mate.memberId }
    val look = mate.memberId?.let(state.looks::get)
    val character = look?.let { AvatarCharacter.of(it.gender) } ?: AvatarCharacter.of(row?.shownGender)
    val isMoving = rememberIsMoving(Distance(mate.meters.toLong()))
    val isRunning = state.phase == CrewRunPhase.RUNNING && !state.mine.isPaused &&
        mate.state.name == MateState.RUNNING.name && isMoving
    RunAvatarSlot(
        pose = if (isRunning) AvatarSlotPose.RUN else AvatarSlotPose.STAND,
        facingLeft = false,
        runSpeed = runMotionSpeed(null),
        subject = RunAvatarSubject.other(look?.toSlot(), character == AvatarCharacter.FEMALE)
    ) {
        RunkkRunningFigure(isRunning, runFrameMillis(null), character = character)
    }
}

/** 받은 모습을 칸 값(원시값, core.navigation)으로 옮긴다([임시:D-성-259]). 표시 성별이 여자일 때만 여자 몸이다. */
internal fun RunnerLook.toSlot(): SlotRunnerLook = SlotRunnerLook(
    isFemale = gender == Gender.FEMALE,
    topItemId = topItemId,
    bottomItemId = bottomItemId,
    shoesItemId = shoesItemId,
    bodyVariant = bodyVariant,
    runAmount = runAmount,
    faceKey = faceKey
)

/**
 * 카드 아래 문장([임시:FR-성-30]): 순위와 인원, 가장 가까운 크루원과의 차이(「8명 중 3위, 바로 뒤 한강달림과 12 m」). 가운뎃점은 쓰지
 * 않는다. 가장 가까운 사람이 없으면(혼자, 모두 끝냄) 순위와 인원만 적는다.
 */
@Composable
internal fun standingSentence(standing: CrewRunStanding): String {
    val nearest = standing.nearest
    val gap = standing.nearestGapMeters
    if (nearest == null || gap == null) return stringResource(R.string.crew_run_standing, standing.total, standing.rank)
    val particle = if (endsWithFinalConsonant(nearest.name)) "과" else "와"
    return when {
        gap > 0 -> stringResource(
            R.string.crew_run_standing_ahead,
            standing.total,
            standing.rank,
            nearest.name,
            particle,
            gap
        )

        gap < 0 -> stringResource(
            R.string.crew_run_standing_behind,
            standing.total,
            standing.rank,
            nearest.name,
            particle,
            -gap
        )

        else -> stringResource(R.string.crew_run_standing_level, standing.total, standing.rank, nearest.name, particle)
    }
}

/** 마지막 글자가 받침 있는 한글인가(조사 과/와). 한글이 아니면 받침 없음으로 본다. */
private fun endsWithFinalConsonant(word: String): Boolean {
    val last = word.trimEnd().lastOrNull() ?: return false
    return last in HANGUL_FIRST..HANGUL_LAST && (last - HANGUL_FIRST) % HANGUL_FINALS != 0
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

/** 출발 카운트다운 초(서버 예약 시각 + 3초가 측정 시작, [임시:D-성-881]). */
private const val COUNTDOWN_SECONDS = 3L
private val COUNTDOWN_RING = 132.dp
private val COUNTDOWN_STROKE = 10.dp
private const val FULL_SWEEP = 360f
private const val START_ANGLE = -90f
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private val LOBBY_SEAT = 66.dp
private val LOBBY_GRID_GAP = 14.dp
private val CHIP_AREA = 64.dp
private val BIG_DISTANCE_HEIGHT = 82.dp

/** 트랙 카드 높이(시안 350×328). 화면이 낮으면 남는 높이만큼 줄어든다. */
private val TRACK_CARD_HEIGHT = 328.dp

/** 이름표 얼굴 원과 지도 위 내 얼굴 원. */
private val TAG_FACE_SIZE = 22.dp
private val MAP_FACE_SIZE = 44.dp
private const val METERS_PER_KM = 1_000L
private const val HANGUL_FIRST = '가'
private const val HANGUL_LAST = '힣'
private const val HANGUL_FINALS = 28

/** 트랙 카드, 지도 카드, 순위 문장의 시험 표지. */
internal const val CREW_TRACK_TAG = "crew-run-track"
internal const val CREW_MAP_TAG = "crew-run-map"
internal const val CREW_STANDING_TAG = "crew-run-standing"
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
