package com.a207.runkk.app.ui.battle

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.CelebrityFigure
import com.a207.runkk.app.core.designsystem.NOTIFICATION_PERMISSION_API
import com.a207.runkk.app.core.designsystem.OPPONENT_MARKER_TAG
import com.a207.runkk.app.core.designsystem.OpponentMarkerPlacer
import com.a207.runkk.app.core.designsystem.RouteCanvas
import com.a207.runkk.app.core.designsystem.RouteMarker
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkCountdownBanner
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.shouldAskNotificationPermission
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import kotlin.math.abs

/**
 * 대결 진행 화면이 열리면 진행 알림 권한을 한 번 묻는다(AN8-5, D-259 "첫 러닝 시작 때"). 첫 러닝이 배틀런·친구 대결·셀프런·셀럽런이어도
 * 진행 알림을 띄울 수 있게 자유런 카운트다운과 같은 규칙(core shouldAskNotificationPermission)을 쓴다. 거부해도 대결은 이어 간다.
 */
@Composable
private fun NotificationPermissionEffect() {
    val context = LocalContext.current
    var isAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < NOTIFICATION_PERMISSION_API) return@LaunchedEffect
        val isGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (shouldAskNotificationPermission(Build.VERSION.SDK_INT, isGranted, isAsked)) {
            isAsked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * 대결 진행(C-4·D-3·E-3)과 셀프런·셀럽런 카운트다운(D-2·E-2). 끝나면 결과 화면이 이 화면을 대신하고, 셀프런·셀럽런을 그만두면
 * 결과 없이 기록런 탭으로 돌아간다(D-266).
 */
@Composable
fun BattleRunningRoute(key: Routes.BattleRunning, viewModel: BattleRunningViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    NotificationPermissionEffect()
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
    // 뒤로가기는 그만두기와 같다: 포기 확인을 띄운다. 시작하지 못한 화면은 닫는다.
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
            onReselect = viewModel::onReselect,
            onClose = close
        )
    )
}

/** 대결 진행 화면의 사용자 행동. */
data class BattleRunningActions(
    val onForfeitTap: () -> Unit = {},
    val onForfeitLongPress: () -> Unit = {},
    val onForfeitConfirm: () -> Unit = {},
    val onForfeitDismiss: () -> Unit = {},
    val onReselect: () -> Unit = {},
    val onClose: () -> Unit = {}
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

/** 머리 "● 배틀런 · 5km"(셀프런·셀럽런은 그 이름). 오른쪽은 내 연결 끊김 또는 랜덤 대결 상대 연결 상태(FR-088, 배틀 QA 5번). */
@Composable
private fun Header(state: BattleRunningUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        BattleHeader(headerTitle(state.kind, distanceLabel(state.distance).asString()))
        Spacer(Modifier.weight(1f))
        val label = when {
            state.isLiveLost -> stringResource(R.string.battle_running_disconnected)
            else -> state.connectionLabel?.asString()
        }
        if (label != null) RunkkStatusPill(label, live = LiveRegionMode.Polite)
    }
}

@Composable
internal fun headerTitle(kind: OpponentKind, distance: String): String = when (kind) {
    OpponentKind.RANDOM, OpponentKind.FRIEND -> stringResource(R.string.battle_header_battle, distance)
    OpponentKind.SELF -> stringResource(R.string.battle_header_self, distance)
    OpponentKind.CELEBRITY -> stringResource(R.string.battle_header_celeb, distance)
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
 * 측정(C-4·D-3·E-3): 큰 거리, 경과·현재 페이스·평균 페이스, 경로선 위 나·상대 마커, "+42 m 앞서는 중"(상대 숫자는 그리지 않는다,
 * FR-098), 길게 눌러 종료. 추월·역전 안내(FR-188)와 종료 카운트다운(FR-107), 연결 끊김은 그대로 보인다.
 */
@Composable
private fun ColumnScope.Running(state: BattleRunningUiState, actions: BattleRunningActions) {
    GuidanceBanner(state)
    BigDistance(state.myDistance)
    Stats(state)
    // 마커 그림(72dp)이 위 지표 줄과 겹치지 않게 경로 칸 위를 띄운다.
    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 24.dp)) { BattleRoute(state) }
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
    RunkkLongPressButton(
        onTap = actions.onForfeitTap,
        onLongPress = actions.onForfeitLongPress,
        tapLabel = stringResource(R.string.battle_running_forfeit_tap_label),
        longPressLabel = stringResource(R.string.battle_running_hold_to_end),
        contentDescription = stringResource(R.string.battle_running_forfeit_desc),
        caption = stringResource(R.string.battle_running_hold_to_end),
        size = 88.dp,
        borderColor = RunkkColors.Surface,
        borderWidth = 0.dp,
        background = RunkkColors.Surface,
        modifier = Modifier.align(Alignment.CenterHorizontally)
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.TextPrimary))
    }
}

@Composable
private fun BigDistance(distance: Distance) {
    val spoken = stringResource(R.string.battle_running_distance_spoken, RunkkFormat.km(distance))
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(RunkkFormat.km(distance), style = RunkkTextStyles.DisplayHuge, color = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.battle_running_unit_km),
            style = RunkkTextStyles.text(18),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
    }
}

/** 경과·현재 페이스·평균 페이스(D-3). */
@Composable
private fun Stats(state: BattleRunningUiState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat(
            RunkkFormat.duration(state.elapsed),
            stringResource(R.string.battle_running_elapsed),
            RunkkFormat.durationSpoken(state.elapsed)
        )
        Stat(
            RunkkFormat.pace(state.currentPace),
            stringResource(R.string.battle_running_current_pace),
            RunkkFormat.paceSpoken(state.currentPace)
        )
        Stat(
            RunkkFormat.pace(state.averagePace),
            stringResource(R.string.battle_running_average_pace),
            RunkkFormat.paceSpoken(state.averagePace)
        )
    }
}

@Composable
private fun Stat(value: String, label: String, spoken: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$label $spoken" }
    ) {
        Text(value, style = RunkkTextStyles.display(28), color = RunkkColors.TextPrimary)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/**
 * 경로선과 마커(D-261, D-262): 경로 끝에 "나", 상대는 내 경로 위 "내 거리 + 격차" 지점(OpponentMarkerPlacer, 격차를 모르거나 경로가
 * 짧으면 그리지 않는다). 과거의 나는 반투명, 셀럽은 공통 SD 그림이다(D-254). 경로가 아직 없으면 내 아바타만 가운데에 둔다.
 */
@Composable
private fun BattleRoute(state: BattleRunningUiState) {
    val route = state.route
    val me = route.points.lastOrNull()
    if (me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RunkkAvatar(faceKey = null, expression = AvatarExpression.RUN, modifier = Modifier.size(72.dp))
        }
        return
    }
    val mine = stringResource(R.string.battle_me)
    val opponentName = opponentLabel(state)
    val theirs = OpponentMarkerPlacer.place(route, state.myDistance, state.gapMeters)
    val kind = state.kind
    val markers = remember(me, theirs, opponentName, kind, mine) {
        listOfNotNull(
            theirs?.let { point ->
                when (kind) {
                    OpponentKind.CELEBRITY -> RouteMarker(
                        point,
                        label = opponentName,
                        tag = OPPONENT_MARKER_TAG,
                        figure = { CelebrityFigure(Modifier.fillMaxSize()) }
                    )

                    OpponentKind.SELF -> RouteMarker(
                        point,
                        label = opponentName,
                        alpha = PAST_SELF_ALPHA,
                        tag = OPPONENT_MARKER_TAG
                    )

                    else -> RouteMarker(point, label = opponentName, tag = OPPONENT_MARKER_TAG)
                }
            },
            RouteMarker(point = me, label = mine)
        )
    }
    RouteCanvas(route = route, markers = markers, modifier = Modifier.fillMaxSize())
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
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
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

private const val PAST_SELF_ALPHA = 0.5f

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
