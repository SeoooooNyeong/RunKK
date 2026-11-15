package com.a207.runkk.app.ui.battle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.AvatarStageBackdrop
import com.a207.runkk.app.core.designsystem.CelebrityCharacter
import com.a207.runkk.app.core.designsystem.CelebrityFigure
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalKakaoMapKey
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.ProgressDot
import com.a207.runkk.app.core.designsystem.RunPermissionEffect
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkCountdownBanner
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkMeMap
import com.a207.runkk.app.core.designsystem.RunkkRoundControl
import com.a207.runkk.app.core.designsystem.RunkkRunDistance
import com.a207.runkk.app.core.designsystem.RunkkRunLabel
import com.a207.runkk.app.core.designsystem.RunkkRunStat
import com.a207.runkk.app.core.designsystem.RunkkRunStats
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkStopSquare
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTrackCard
import com.a207.runkk.app.core.designsystem.RunkkTrackProgressBar
import com.a207.runkk.app.core.designsystem.RunkkViewModeToggle
import com.a207.runkk.app.core.designsystem.TrackHorizonAlignment
import com.a207.runkk.app.core.designsystem.TrackLayout
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.canShowKakaoMap
import com.a207.runkk.app.core.designsystem.rememberIsMoving
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.RunViewMode
import com.a207.runkk.app.core.navigation.AvatarSlotPose
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunAvatarSlot
import com.a207.runkk.app.core.navigation.RunAvatarSubject
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import kotlin.math.abs

/**
 * 대결 진행(C-4·D-3·E-3)과 셀프런·셀럽런 카운트다운(D-2·E-2). 끝나면 결과 화면이 이 화면을 대신하고, 셀프런·셀럽런을 그만두면
 * 결과 없이 기록런 탭으로 돌아간다(D-266).
 */
@Composable
fun BattleRunningRoute(key: Routes.BattleRunning, viewModel: BattleRunningViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    // 진행 알림·신체 활동 권한을 한 번 묻는다(AN8-5, D-259, S15P31A207-242). 거부해도 대결은 이어 간다.
    RunPermissionEffect()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BattleRunningEvent.Finished ->
                    navigator.replace(Routes.BattleResult(event.battleId, isJustFinished = true))

                BattleRunningEvent.Discarded -> navigator.selectTab(Routes.RECORD_RUN)
            }
        }
    }
    val close = {
        viewModel.onFailureClosed()
        navigator.back()
    }
    // 뒤로가기는 그만두기와 같다: 포기 확인을 띄운다(셀프런·셀럽런 출발 전이면 취소 확인, D-337). 시작하지 못한 화면은 닫는다.
    BackHandler {
        if (state.phase == BattlePhase.FAILED) close() else viewModel.onForfeitLongPressed()
    }
    BattleRunningScreen(
        state = state,
        actions = BattleRunningActions(
            onForfeitTap = viewModel::onForfeitTapped,
            onForfeitLongPress = viewModel::onForfeitLongPressed,
            onForfeitConfirm = viewModel::onForfeitConfirmed,
            onForfeitDismiss = viewModel::onForfeitDismissed,
            onCancelTap = viewModel::onCancelTapped,
            onCancelConfirm = viewModel::onCancelConfirmed,
            onCancelDismiss = viewModel::onCancelDismissed,
            onReselect = viewModel::onReselect,
            onClose = close,
            onViewModeChange = viewModel::onViewModeChange
        )
    )
}

/** 대결 진행 화면의 사용자 행동. */
data class BattleRunningActions(
    val onForfeitTap: () -> Unit = {},
    val onForfeitLongPress: () -> Unit = {},
    val onForfeitConfirm: () -> Unit = {},
    val onForfeitDismiss: () -> Unit = {},
    val onCancelTap: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onCancelDismiss: () -> Unit = {},
    val onReselect: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onViewModeChange: (RunViewMode) -> Unit = {}
)

@Composable
fun BattleRunningScreen(state: BattleRunningUiState, actions: BattleRunningActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(state)
        when (state.phase) {
            BattlePhase.STARTING, BattlePhase.COUNTDOWN -> Countdown(state, actions.onReselect)

            BattlePhase.FAILED -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.battle_running_start_failed),
                onRetry = actions.onClose,
                retryLabel = stringResource(R.string.battle_running_back),
                modifier = Modifier.weight(1f)
            )

            else -> Running(state, actions)
        }
    }
    if (state.isConfirmingForfeit) ForfeitDialog(state.kind, actions.onForfeitConfirm, actions.onForfeitDismiss)
    if (state.isConfirmingCancel) CancelDialog(actions.onCancelConfirm, actions.onCancelDismiss)
}

/** 그만두기 확인(FR-094, D-266): 문구는 상대 종류마다 다르고 취소는 모두 "계속 달리기"다. */
@Composable
private fun ForfeitDialog(kind: OpponentKind, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(kind.forfeitTitle),
        message = stringResource(kind.forfeitMessage),
        confirmLabel = stringResource(kind.forfeitConfirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.battle_running_forfeit_dismiss),
        isDestructive = true
    )
}

/** 셀프런·셀럽런 취소 확인: 기록 없이 끝낸다(자유런 취소와 같은 문구, 사용자 요청 2026-10-30). */
@Composable
private fun CancelDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    RunkkConfirmDialog(
        title = stringResource(R.string.battle_cancel_title),
        message = stringResource(R.string.battle_cancel_message),
        confirmLabel = stringResource(R.string.battle_cancel_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.battle_running_forfeit_dismiss),
        isDestructive = true
    )
}

/** 머리: 왼쪽 러닝 종류와 목표 거리, 오른쪽 연결 상태(FR-088). */
@Composable
private fun Header(state: BattleRunningUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val kind = stringResource(
            when (state.kind) {
                OpponentKind.SELF -> R.string.battle_record_run_self
                OpponentKind.CELEBRITY -> R.string.battle_record_run_celeb
                else -> R.string.battle_match_title
            }
        )
        RunkkRunLabel(stringResource(R.string.battle_running_label, kind, RunkkFormat.targetLabel(state.distance)))
        Spacer(Modifier.weight(1f))
        val label = when {
            state.isLiveLost -> stringResource(R.string.battle_running_disconnected)
            else -> state.connectionLabel?.asString()
        }
        if (label != null) RunkkStatusPill(label, live = LiveRegionMode.Polite)
    }
}

/** 취소 버튼(자유런 취소와 같은 모양): 작은 원 X(56, 아이콘 24)와 "취소". */
@Composable
private fun CancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RunkkRoundControl(
        icon = RunkkIcons.Close,
        contentDescription = stringResource(R.string.battle_cancel_desc),
        onClick = onClick,
        modifier = modifier,
        size = 56.dp,
        iconSize = 24.dp,
        tint = RunkkColors.TextSecondary,
        caption = stringResource(R.string.battle_cancel_caption)
    )
}

/** 상대 표시 이름: 과거의 나는 "10월 2일의 나"(날짜를 모르면 "과거의 나"), 나머지는 서버 표시명. */
@Composable
internal fun opponentLabel(state: BattleRunningUiState): String = when (state.kind) {
    OpponentKind.SELF -> state.opponentRecordedOn?.let { stringResource(R.string.battle_past_self, monthDay(it)) }
        ?: stringResource(R.string.battle_running_opponent_self)

    else -> state.opponentName.ifBlank { stringResource(R.string.battle_opponent) }
}

/**
 * 출발 전. 셀프런(D-2) "과거의 나와 대결해요"·"다시 고르기", 셀럽런(E-2) "김선교 대공님과 달려요", 나 VS 상대(기록 줄), 자동 출발 고리.
 * 배틀런은 성사 화면이 카운트다운을 그리므로 여기서는 고리만 보인다(진행 화면을 다시 연 경우).
 */
@Composable
private fun ColumnScope.Countdown(state: BattleRunningUiState, onReselect: () -> Unit) {
    val distance = distanceLabel(state.distance).asString()
    val opponent = opponentLabel(state)
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        if (state.kind.isRecord) {
            Text(
                if (state.kind == OpponentKind.SELF) {
                    stringResource(R.string.battle_countdown_self_title)
                } else {
                    stringResource(R.string.battle_countdown_celeb_title, opponent, particle(opponent, "과", "와"))
                },
                style = RunkkTextStyles.Headline,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Versus(
                mine = { Contender(stringResource(R.string.battle_me), it, caption = myCaption(state, distance)) },
                theirs = {
                    Contender(
                        opponent,
                        it,
                        figure = if (state.kind == OpponentKind.SELF) Figure.PAST_SELF else Figure.CELEBRITY,
                        celebrityFigure = state.celebrityFigure,
                        caption = state.opponentRecord?.let { record ->
                            stringResource(R.string.battle_record_line, distance, RunkkFormat.duration(record))
                        }
                    )
                }
            )
        }
        StartRing(state.countdown, total = RECORD_COUNTDOWN_SECONDS, modifier = Modifier.padding(top = 24.dp))
    }
    if (state.kind == OpponentKind.SELF) {
        RunkkSecondaryButton(
            stringResource(R.string.battle_countdown_reselect),
            onClick = onReselect,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 셀럽런 카운트다운의 내 줄 "10km 최고 1:02:10", 기록이 없으면 "첫 도전"(D-264). 셀프런은 줄이 없다. */
@Composable
private fun myCaption(state: BattleRunningUiState, distance: String): String? = when (state.kind) {
    OpponentKind.CELEBRITY -> state.myBest?.let {
        stringResource(R.string.battle_best_line, distance, RunkkFormat.duration(it))
    }
        ?: stringResource(R.string.battle_countdown_first_try)

    else -> null
}

/**
 * 측정(C-4·D-3·E-3): 큰 거리, 경과·현재 페이스·평균 페이스, 트랙 카드(또는 지도, [임시:FR-성-27]·[임시:FR-성-28]), "+42 m 앞서는 중"
 * (상대 숫자는 그리지 않는다, FR-098), 길게 눌러 종료. 추월·역전 안내(FR-188)와 종료 카운트다운(FR-107), 연결 끊김은 그대로 보인다.
 */
@Composable
private fun ColumnScope.Running(state: BattleRunningUiState, actions: BattleRunningActions) {
    GuidanceBanner(state)
    BigDistance(state.myDistance)
    Stats(state)
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        BattleTrack(
            state,
            actions.onViewModeChange,
            Modifier.fillMaxWidth().heightIn(max = TRACK_CARD_HEIGHT).fillMaxHeight()
        )
    }
    Gap(state)
    state.error?.let {
        Text(
            it.asString(),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.Lavender,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
    state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
    // 자유런처럼 왼쪽에 취소(기록 없음, 셀프런·셀럽런만), 가운데에 길게 눌러 그만두기(패배로 기록)
    Box(modifier = Modifier.fillMaxWidth()) {
        if (state.kind.isCancellable) {
            CancelButton(actions.onCancelTap, Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
        }
        RunkkLongPressButton(
            onTap = actions.onForfeitTap,
            onLongPress = actions.onForfeitLongPress,
            tapLabel = stringResource(R.string.battle_running_forfeit_tap_label),
            longPressLabel = stringResource(R.string.battle_running_hold_to_end),
            contentDescription = stringResource(R.string.battle_running_forfeit_desc),
            caption = stringResource(R.string.battle_running_hold_to_end),
            size = 84.dp,
            borderColor = RunkkColors.LimeFill,
            borderWidth = 0.dp,
            background = RunkkColors.LimeFill,
            modifier = Modifier.align(Alignment.Center)
        ) {
            RunkkStopSquare(size = 28.dp, corner = 7.dp, color = RunkkColors.OnLimeFill)
        }
    }
}

@Composable
private fun BigDistance(distance: Distance) {
    RunkkRunDistance(
        distance = RunkkFormat.km(distance),
        unit = stringResource(R.string.battle_running_unit_km),
        spoken = stringResource(R.string.battle_running_distance_spoken, RunkkFormat.km(distance))
    )
}

/** 경과·현재 페이스·평균 페이스(D-3). */
@Composable
private fun Stats(state: BattleRunningUiState) {
    RunkkRunStats(
        listOf(
            RunkkRunStat(
                RunkkFormat.duration(state.elapsed),
                stringResource(R.string.battle_running_elapsed),
                RunkkFormat.durationSpoken(state.elapsed)
            ),
            RunkkRunStat(
                RunkkFormat.pace(state.currentPace),
                stringResource(R.string.battle_running_current_pace),
                RunkkFormat.paceSpoken(state.currentPace)
            ),
            RunkkRunStat(
                RunkkFormat.pace(state.averagePace),
                stringResource(R.string.battle_running_average_pace),
                RunkkFormat.paceSpoken(state.averagePace)
            )
        )
    )
}

/**
 * 트랙 카드와 지도([임시:D-성-265], [임시:D-성-313], [임시:FR-성-27], [임시:FR-성-28]). 같은 자리 카드에 고른 보기를 그리고, 진행 막대와
 * 「트랙 | 지도」 전환을 카드 위에 겹쳐 둔다. 지도를 띄울 수 없으면(키·라이브러리 없음, 실행 중 지도 오류) 전환을 숨기고 트랙만 그린다
 * (저장한 보기는 바꾸지 않는다).
 *
 * - 트랙: 아래 레인에 나, 위 레인에 상대를 거리 차만큼 앞뒤로 놓는다(폭 30 m, 내 위치 40%). 상대가 화면 밖이면 그쪽 가장자리 칩에
 *   이름과 차이를 적는다. 결승선은 화면 안에 들어올 때만 보인다. 배경은 내가 고른 아바타 무대 배경이다.
 * - 지도: 내가 달린 길과 지금 위치의 내 얼굴만 그린다. 상대, 남은 길, 도착점은 없다. 진행 막대의 상대 점은 남는다.
 */
@Composable
private fun BattleTrack(
    state: BattleRunningUiState,
    onViewModeChange: (RunViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // 실행 중 지도 오류가 나면 트랙만 보인다(D-340, 보기 모드 MAP_UNAVAILABLE)
    var isMapFailed by remember { mutableStateOf(false) }
    val isMapAvailable = canShowKakaoMap(LocalKakaoMapKey.current, isMapFailed)
    val mode = shownViewMode(state.viewMode, isMapAvailable)
    Box(modifier.clip(RoundedCornerShape(26.dp))) {
        when (mode) {
            RunViewMode.TRACK -> TrackView(state, Modifier.fillMaxSize(), if (isMapAvailable) 91.dp else 55.dp)

            RunViewMode.MAP -> RunkkMeMap(
                route = state.route,
                myMarkerSize = MAP_FACE_SIZE,
                onMapError = { isMapFailed = true },
                modifier = Modifier.fillMaxSize().testTag(BATTLE_MAP_TAG),
                myLabel = stringResource(R.string.battle_me)
            ) { MyFace(state, MAP_FACE_SIZE) }
        }
        Column(
            Modifier.align(Alignment.TopStart).padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RunkkTrackProgressBar(
                dots = progressDots(state),
                modifier = Modifier.fillMaxWidth(),
                leading = stringResource(
                    R.string.battle_track_progress,
                    RunkkFormat.km(state.myDistance),
                    goalKm(state.distance)
                ),
                trailing = stringResource(R.string.battle_track_remaining, RunkkFormat.km(remainingOf(state)))
            )
            if (isMapAvailable) RunkkViewModeToggle(selected = mode, onSelect = onViewModeChange)
        }
    }
}

/** 실제로 그릴 보기([임시:D-성-265]): 지도를 띄울 수 없으면 저장한 보기와 상관없이 트랙이다. */
internal fun shownViewMode(saved: RunViewMode, isMapAvailable: Boolean): RunViewMode =
    if (isMapAvailable) saved else RunViewMode.TRACK

/** 목표까지 남은 거리(다 달렸으면 0). */
private fun remainingOf(state: BattleRunningUiState): Distance =
    Distance((state.distance.meters - state.myDistance.meters).coerceAtLeast(0))

/** 진행 막대의 목표 거리 숫자: 5000 m는 「5」, 21097 m는 「21.1」. */
private fun goalKm(distance: Distance): String = if (distance.meters % METERS_PER_KM ==
    0L
) {
    (distance.meters / METERS_PER_KM).toString()
} else {
    RunkkFormat.kmShort(distance)
}

/** 진행 막대의 점: 나(라임)와 상대(보라). 상대 진행값을 받기 전이면 내 점만 찍는다. */
private fun progressDots(state: BattleRunningUiState): List<ProgressDot> {
    val goal = state.distance.meters.coerceAtLeast(1).toFloat()
    val theirs = state.opponentDistance?.meters ?: state.gapMeters?.let { state.myDistance.meters - it }
    return listOfNotNull(
        ProgressDot(state.myDistance.meters / goal, isMine = true, faceKey = state.myAvatar?.faceKey),
        theirs?.let { ProgressDot(it / goal, isMine = false, faceKey = state.opponentLook?.faceKey) }
    )
}

/** 트랙 보기: 위치 계산은 매번 다시 한다(받은 거리값이 바로 다음 그림에 반영된다, [임시:D-성-273]). */
@Composable
private fun TrackView(state: BattleRunningUiState, modifier: Modifier, overlayBottom: Dp) {
    val gap = state.gapMeters
    val layout = TrackLayout.of(
        aheadMeters = gap?.let { -it.toDouble() },
        remainingMeters = (state.distance.meters - state.myDistance.meters).toDouble()
    )
    val opponentName = opponentLabel(state)
    val edge = layout.other as? TrackLayout.Other.Edge
    val isMoving = rememberIsMoving(state.myDistance) && state.isMeasuring && state.currentPace != null
    val frameMillis = runFrameMillis(state.currentPace)
    // 내 아바타 칸(design-oo 05-30 7절): 대결은 일시정지가 없어 RUNNING이면 RUN, 마감·카운트다운 등은 STAND. 트랙에서는 늘 오른쪽을 본다.
    val pose = if (state.phase == BattlePhase.RUNNING) AvatarSlotPose.RUN else AvatarSlotPose.STAND
    val isAvatarOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.AVATAR_CUSTOMIZE)
    RunkkTrackCard(
        layout = layout,
        myTag = stringResource(R.string.battle_me),
        otherTag = opponentName,
        myAvatar = {
            RunAvatarSlot(pose, facingLeft = false) { RunkkRunningFigure(isMoving, frameMillis) }
        },
        otherAvatar = { OpponentRunner(state) },
        modifier = modifier.testTag(BATTLE_TRACK_TAG),
        myPace = state.currentPace?.let { RunkkFormat.pace(it) },
        isMoving = isMoving,
        speedMetersPerSecond = state.currentPace?.let { 1_000f / it.secondsPerKm } ?: 0f,
        overlayBottom = overlayBottom,
        aheadChip = edge?.takeIf { it.isAhead }?.let {
            stringResource(R.string.battle_track_edge_ahead, opponentName, it.meters)
        },
        behindChip = edge?.takeIf { !it.isAhead }?.let {
            stringResource(R.string.battle_track_edge_behind, opponentName, it.meters)
        },
        rulerLabel = gap?.let { stringResource(R.string.battle_track_ruler, abs(it)) },
        myFace = { MyFace(state, TAG_FACE_SIZE) },
        otherFace = opponentFace(state),
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
private fun MyFace(state: BattleRunningUiState, size: Dp) {
    RunkkAvatarCircle(
        faceKey = state.myAvatar?.faceKey,
        size = size,
        faceLayers = state.myAvatar?.faceLayers.orEmpty(),
        borderColor = RunkkColors.Lime
    )
}

/** 상대 이름표의 얼굴 원. 배치고사 상대(물음표)와 셀럽은 얼굴 원을 두지 않는다. 과거의 나는 내 캐릭터다. */
@Composable
private fun opponentFace(state: BattleRunningUiState): (@Composable () -> Unit)? {
    if (state.isOpponentHidden || state.kind == OpponentKind.CELEBRITY) return null
    val character = opponentCharacter(state)
    val faceKey = state.opponentLook?.faceKey
    return { RunkkAvatarCircle(faceKey = faceKey, size = TAG_FACE_SIZE, character = character) }
}

/** 상대 기본 캐릭터: 받은 모습의 성별, 없으면 상대 성별, 과거의 나는 내 캐릭터다. */
@Composable
private fun opponentCharacter(state: BattleRunningUiState): AvatarCharacter = when {
    state.opponentLook != null -> AvatarCharacter.of(state.opponentLook.gender)
    state.kind == OpponentKind.SELF -> LocalAvatarCharacter.current
    else -> AvatarCharacter.of(state.opponentGender)
}

/**
 * 위 레인의 상대([임시:D-성-253], [임시:D-성-259]). 배치고사 상대는 성사 화면과 같은 점선 원 「?」(FR-276), 셀럽은 전용 캐릭터(키가
 * 없으면 공통 SD 그림, D-254), 그 밖(랜덤·친구·고스트·과거의 나)은 받은 모습의 달리는 아바타다. 모습을 받지 못했으면 성별 기본 모습이고,
 * 아바타 칸이 비었거나 그리지 못하면 그 성별의 달리기 그림이다. 과거의 나는 반투명하다. 움직이면 달리고 멈추면 선다.
 */
@Composable
private fun OpponentRunner(state: BattleRunningUiState) {
    val isTheirsMoving = rememberIsMoving(state.opponentDistance) && state.isMeasuring
    val celebrity = CelebrityCharacter.of(state.celebrityFigure)
    val character = opponentCharacter(state)
    when {
        state.isOpponentHidden -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OpponentFigure(Figure.UNKNOWN, size = UNKNOWN_SIZE)
        }

        state.kind == OpponentKind.CELEBRITY && celebrity != null -> RunkkRunningFigure(
            isTheirsMoving,
            runFrameMillis(null),
            stand = celebrity.stand,
            runCycle = celebrity.runCycle
        )

        state.kind == OpponentKind.CELEBRITY -> CelebrityFigure(Modifier.fillMaxSize())

        else -> Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = if (state.kind ==
                    OpponentKind.SELF
                ) {
                    PAST_SELF_ALPHA
                } else {
                    1f
                }
            }
        ) {
            RunAvatarSlot(
                // 내 아바타와 같은 기준이다. 상대 거리가 처음 들어올 때 자세를 바꾸면 그림을 다시 준비하느라 1~2초 비어 보인다.
                pose = if (state.phase == BattlePhase.RUNNING) AvatarSlotPose.RUN else AvatarSlotPose.STAND,
                facingLeft = false,
                subject = RunAvatarSubject.other(state.opponentLook?.toSlot(), character == AvatarCharacter.FEMALE)
            ) {
                RunkkRunningFigure(isTheirsMoving, runFrameMillis(null), character = character)
            }
        }
    }
}

@Composable
private fun GuidanceBanner(state: BattleRunningUiState) {
    if (state.phase == BattlePhase.CLOSING) {
        val arrived = state.arrivedName ?: stringResource(R.string.battle_opponent)
        RunkkCountdownBanner(
            text = stringResource(R.string.battle_running_closing, arrived, state.closingSeconds ?: 0),
            spoken = stringResource(
                R.string.battle_running_closing_spoken,
                arrived,
                state.closingLimitSeconds ?: state.closingSeconds ?: 0
            )
        )
        return
    }
    when (state.guidance) {
        BattleGuidance.Kind.OVERTOOK -> RunkkBanner(
            stringResource(
                if (state.kind ==
                    OpponentKind.SELF
                ) {
                    R.string.battle_running_overtook_self
                } else {
                    R.string.battle_running_overtook
                }
            ),
            isStrong = true,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        )

        BattleGuidance.Kind.OVERTAKEN -> Text(
            stringResource(R.string.battle_running_overtaken),
            style = RunkkTextStyles.text(15, FontWeight.Black),
            color = RunkkColors.Lavender,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Medium)
                .border(1.dp, RunkkColors.Lavender, RunkkShapes.Medium)
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )

        null -> Unit
    }
}

/** 거리 차 "+42 m 앞서는 중" / "-118 m 뒤처지는 중". 상대 누적 거리·경과 숫자는 그리지 않는다(FR-098). */
@Composable
private fun Gap(state: BattleRunningUiState) {
    val gap = state.gapMeters
    val spoken = when {
        gap == null -> stringResource(R.string.battle_running_gap_waiting)
        gap >= 0 -> stringResource(R.string.battle_running_gap_ahead_spoken, gap)
        else -> stringResource(R.string.battle_running_gap_behind_spoken, abs(gap))
    }
    val description = stringResource(R.string.battle_running_gap_desc, spoken)
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        if (gap == null) {
            Text(spoken, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
            return@Row
        }
        Text(gapMetersLabel(gap), style = RunkkTextStyles.text(20, FontWeight.Black), color = RunkkColors.TextPrimary)
        Text(
            stringResource(if (gap >= 0) R.string.battle_running_ahead else R.string.battle_running_behind),
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

/** "+42 m", "-118 m"(격차 0은 앞서는 쪽 "+0 m"). */
internal fun gapMetersLabel(gap: Int): String = if (gap >= 0) "+$gap m" else "$gap m"

private const val PAST_SELF_ALPHA = 0.78f

/** 트랙 카드 높이(시안 350×328). 화면이 낮으면 남는 높이만큼 줄어든다. */
private val TRACK_CARD_HEIGHT = 328.dp

/** 배치고사 「?」 원은 상대 아바타 칸 가운데에 성사 화면과 같은 모양으로 그린다. */
private val UNKNOWN_SIZE = 72.dp

/** 이름표 얼굴 원과 지도 위 내 얼굴 원. */
private val TAG_FACE_SIZE = 22.dp
private val MAP_FACE_SIZE = 44.dp
private const val METERS_PER_KM = 1_000L

/** 트랙 카드와 지도 카드의 시험 표지. */
internal const val BATTLE_TRACK_TAG = "battle-track"
internal const val BATTLE_MAP_TAG = "battle-map"

/** 셀프런·셀럽런 출발 전 카운트다운(3초, design-review.md M34). 고리 몫의 분모다. */
private const val RECORD_COUNTDOWN_SECONDS = 3L

@Preview(name = "대결 진행", widthDp = 390, heightDp = 844)
@Composable
private fun BattleRunningPreview() {
    RunkkTheme(reduceMotion = true) {
        BattleRunningScreen(
            BattleRunningUiState(
                battleId = 1,
                opponentName = "바람처럼",
                phase = BattlePhase.RUNNING,
                gapMeters = 42,
                opponentDistance = Distance(2_800),
                myDistance = Distance(2_842),
                elapsed = Duration.ofSeconds(940),
                connectionLabel = UiText.Res(R.string.battle_running_connected),
                guidance = BattleGuidance.Kind.OVERTOOK,
                overtakes = 2
            ),
            BattleRunningActions()
        )
    }
}
