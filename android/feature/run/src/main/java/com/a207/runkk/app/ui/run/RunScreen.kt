package com.a207.runkk.app.ui.run

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkBigNumber
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkInfoChip
import com.a207.runkk.app.core.designsystem.RunkkLiveDot
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.PauseLimit
import com.a207.runkk.app.domain.run.RunProgress
import java.time.Duration
import java.time.Instant

/** 자유런 시작 카운트다운(B1)·러닝 측정(B2)·화면 잠금(B3). */
@Composable
fun RunRoute(viewModel: RunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onRetryStart()
        }
    RunEventEffect(viewModel, navigator)
    // 동의 관리·자유런 설정에서 돌아오면 준비 단계를 다시 확인한다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }
    RunBackHandler(state.phase, viewModel)
    // 1초마다 진행값이 바뀌어도 행동 묶음은 다시 만들지 않는다.
    val context = LocalContext.current
    val actions = remember(viewModel, navigator, permission, context) {
        RunActions(
            onCancel = viewModel::onCancelCountdown,
            onCountdownTapped = viewModel::onCountdownTapped,
            onSettings = {
                viewModel.onSettingsTapped()
                navigator.navigate(Routes.FREE_RUN_SETTINGS)
            },
            onLock = viewModel::onLockTapped,
            onUnlockTap = viewModel::onUnlockTapped,
            onUnlockLongPress = viewModel::onUnlockLongPressed,
            onFinishTap = viewModel::onFinishTapped,
            onFinishLongPress = viewModel::onFinishLongPressed,
            onPause = viewModel::onPauseTapped,
            onResume = viewModel::onResumeTapped,
            onDiscard = viewModel::onDiscardTapped,
            onDiscardConfirm = viewModel::onDiscardConfirmed,
            onDiscardDismiss = viewModel::onDiscardDismissed,
            // "동의하러 가기": 기기 위치 권한이 없으면 권한을 묻고, 권한이 있으면 빠진 것은 수집 동의라 동의 관리로 간다.
            onRequestPermission = {
                if (hasLocationPermission(context)) {
                    navigator.navigate(Routes.CONSENTS)
                } else {
                    permission.launch(LOCATION_PERMISSIONS)
                }
            },
            onOpenConsents = { navigator.navigate(Routes.CONSENTS) },
            onRetry = viewModel::onRetryStart,
            onConsentDismiss = viewModel::onConsentDialogDismissed,
            onErrorShown = viewModel::onErrorShown
        )
    }
    RunScreen(state = state, actions = actions)
}

private val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** 기기 위치 권한(정밀·대략 중 하나)이 있는가. */
private fun hasLocationPermission(context: android.content.Context): Boolean = LOCATION_PERMISSIONS.any {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

/** 화면 이벤트(저장됨·저장 안 함·나감)를 이동으로 바꾼다. */
@Composable
private fun RunEventEffect(viewModel: RunViewModel, navigator: RunkkNavigator) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RunEvent.Finished -> {
                    navigator.restart(Routes.HOME)
                    navigator.navigate(Routes.RunResult(event.recordId))
                }

                RunEvent.NotSaved -> navigator.restart(Routes.HOME)

                RunEvent.Exit -> navigator.selectTab(Routes.HOME)
            }
        }
    }
}

/** 측정 중 뒤로 가기는 종료가 아니라 "길게 눌러 종료" 안내다(FR-181). 잠금 중에는 아무것도 하지 않는다. */
@Composable
private fun RunBackHandler(phase: RunPhase, viewModel: RunViewModel) {
    BackHandler {
        when (phase) {
            RunPhase.COUNTDOWN, RunPhase.PREPARING -> viewModel.onCancelCountdown()
            RunPhase.RUNNING -> viewModel.onFinishTapped()
            RunPhase.LOCKED -> viewModel.onUnlockTapped()
            RunPhase.FINISHING -> Unit
        }
    }
}

/** 러닝 화면 사용자 행동. 누름 종류마다 다른 행동이다(D-49). */
data class RunActions(
    val onCancel: () -> Unit = {},
    val onCountdownTapped: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onLock: () -> Unit = {},
    val onUnlockTap: () -> Unit = {},
    val onUnlockLongPress: () -> Unit = {},
    val onFinishTap: () -> Unit = {},
    val onFinishLongPress: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onDiscardConfirm: () -> Unit = {},
    val onDiscardDismiss: () -> Unit = {},
    val onRequestPermission: () -> Unit = {},
    val onOpenConsents: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onConsentDismiss: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)

@Composable
fun RunScreen(state: RunUiState, actions: RunActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val errorText = state.error?.asString()
    LaunchedEffect(state.error) {
        errorText?.let {
            snackbar.showSnackbar(it)
            actions.onErrorShown()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        when (state.phase) {
            RunPhase.PREPARING, RunPhase.COUNTDOWN -> CountdownContent(state, actions)
            RunPhase.RUNNING, RunPhase.FINISHING -> RunningContent(state, actions)
            RunPhase.LOCKED -> LockedContent(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
    // A7 위치 동의 필요 안내: "닫기"는 대화상자만 닫고, "동의하러 가기"는 권한·동의를 받으러 간다.
    if (state.isConsentDialogVisible) {
        RunkkConfirmDialog(
            title = stringResource(R.string.run_consent_title),
            message = stringResource(R.string.run_consent_message),
            confirmLabel = stringResource(R.string.run_consent_go),
            onConfirm = actions.onRequestPermission,
            dismissLabel = stringResource(R.string.run_consent_close),
            onDismiss = actions.onConsentDismiss
        )
    }
    // 자유런 취소 확인: 확인하면 기록을 남기지 않고 끝낸다.
    if (state.isDiscardDialogVisible) {
        RunkkConfirmDialog(
            title = stringResource(R.string.run_discard_title),
            message = stringResource(R.string.run_discard_message),
            confirmLabel = stringResource(R.string.run_discard_confirm),
            onConfirm = actions.onDiscardConfirm,
            dismissLabel = stringResource(R.string.run_discard_keep),
            onDismiss = actions.onDiscardDismiss,
            isDestructive = true
        )
    }
    // B2-3: 정지 55분부터 자동 종료 안내. 잠금 중에는 해제 말고 누를 것이 없으므로 띄우지 않는다(FR-182).
    state.autoEndIn?.takeIf { state.phase == RunPhase.RUNNING }?.let { left ->
        PauseAutoEndDialog(
            pausedFor = state.pausedFor ?: Duration.ZERO,
            autoEndIn = left,
            onFinishNow = actions.onFinishLongPress,
            onResume = actions.onResume
        )
    }
}

/** 모드 이름 줄: 목표가 있으면 "자유런, 목표 5km", 없으면 "자유런". */
@Composable
private fun modeTitle(target: Distance?): String = target?.let {
    stringResource(R.string.run_mode_free_target, RunkkFormat.targetLabel(it))
} ?: stringResource(R.string.run_mode_free)

@Composable
private fun gpsLabel(signal: SignalState?): String = stringResource(
    when (signal) {
        SignalState.NORMAL -> R.string.run_gps_good
        SignalState.UNSTABLE -> R.string.run_gps_weak
        null -> R.string.run_gps_searching
    }
)

@Composable
private fun CountdownContent(state: RunUiState, actions: RunActions) {
    Column(
        modifier = Modifier.fillMaxSize().background(
            RunkkColors.Background
        ).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)
    ) {
        CountdownTopBar(state.progress.signal, actions.onCancel)
        CountdownCenter(state, actions, modifier = Modifier.weight(1f).fillMaxWidth())
        CountdownPlanCard(state, actions.onSettings)
    }
}

/** 카운트다운 위쪽 줄: 취소와 "자유런, GPS 신호 좋음". */
@Composable
private fun CountdownTopBar(signal: SignalState?, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RunkkPillButton(stringResource(R.string.run_countdown_cancel), onClick = onCancel)
        Spacer(Modifier.weight(1f))
        val gps = stringResource(
            when (signal) {
                SignalState.NORMAL -> R.string.run_countdown_gps_good
                SignalState.UNSTABLE -> R.string.run_countdown_gps_weak
                null -> R.string.run_gps_searching
            }
        )
        RunkkStatusPill(stringResource(R.string.run_countdown_status, stringResource(R.string.run_mode_free), gps)) {
            RunkkLiveDot(color = if (signal == SignalState.UNSTABLE) RunkkColors.Lavender else RunkkColors.Lime)
        }
    }
}

/** 카운트다운 가운데: 숫자 원(준비 단계에서는 다시 시도). */
@Composable
private fun CountdownCenter(state: RunUiState, actions: RunActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        if (state.phase == RunPhase.COUNTDOWN) {
            CountdownBall(state.countdown, actions.onCountdownTapped)
        } else {
            RunkkPillButton(stringResource(R.string.run_countdown_retry), onClick = actions.onRetry)
        }
    }
}

/** 카운트다운 아래 카드: 지난번 설정(또는 오늘 계획) 요약 칩과 설정 바꾸기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountdownPlanCard(state: RunUiState, onSettings: () -> Unit) {
    RunkkCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    if (state.isFromCoachPlan) R.string.run_countdown_plan else R.string.run_countdown_last_settings
                ),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            RunkkTextLink(
                stringResource(R.string.run_countdown_change),
                onClick = onSettings,
                contentDescription = stringResource(R.string.run_countdown_change_desc)
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkInfoChip(
                state.targetDistance?.let {
                    stringResource(R.string.run_countdown_target_distance, RunkkFormat.km(it).trimEnd('0').trimEnd('.'))
                } ?: stringResource(R.string.run_countdown_target_none),
                isHighlighted =
                    state.targetDistance != null
            )
            state.targetPace?.let {
                RunkkInfoChip(
                    stringResource(R.string.run_countdown_target_pace, RunkkFormat.pace(it)),
                    spoken = stringResource(R.string.run_countdown_target_pace, RunkkFormat.paceSpoken(it))
                )
            }
            RunkkInfoChip(stringResource(R.string.run_countdown_split_guide))
        }
    }
}

/** 큰 숫자 원(240dp). 누르면 바로 시작(FR-180). 동작 줄이기가 아니면 고리가 퍼진다. */
@Composable
private fun CountdownBall(number: Int, onTap: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val ring = if (reduce) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "countdownRing")
        val value by transition.animateFloat(
            0f,
            1f,
            InfiniteRepeatableSpec(tween(RING_MILLIS), RepeatMode.Restart),
            label = "ring"
        )
        value
    }
    val startNow = stringResource(R.string.run_countdown_start_now)
    val ringColor = RunkkColors.Lime
    Box(
        modifier = Modifier
            .size(240.dp)
            .drawBehind {
                if (ring >
                    0f
                ) {
                    drawCircle(
                        ringColor.copy(alpha = (1f - ring) * RING_ALPHA),
                        radius =
                            size.minDimension / 2 + 32.dp.toPx() * ring
                    )
                }
            }
            .clip(CircleShape)
            .background(RunkkColors.Lime)
            .clickable(role = Role.Button, onClickLabel = startNow, onClick = onTap)
            .semantics {
                contentDescription = "$number"
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center
    ) {
        Text("$number", style = RunkkTextStyles.display(168, lineHeightRatio = 1f), color = RunkkColors.OnLime)
    }
}

@Composable
private fun RunningContent(state: RunUiState, actions: RunActions) {
    Column(
        modifier = Modifier.fillMaxSize().background(
            RunkkColors.Background
        ).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RunningTopBar(state)
        if (state.isPaused) {
            PausedNotice(state.pausedFor)
        } else {
            state.splitBanner?.let {
                RunkkBanner(it.asString(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        RunningDistance(state)
        RunningStats(state.progress)
        Spacer(Modifier.weight(1f))
        state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
        RunningControls(state.isPaused, actions)
    }
}

/** 측정 위쪽 줄: "자유런, 목표 5km"와 GPS·워치 연결 알약. 정지 중에는 라벤더 글자. */
@Composable
private fun RunningTopBar(state: RunUiState) {
    val progress = state.progress
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!state.isPaused) RunkkLiveDot()
        Text(
            modeTitle(state.targetDistance),
            style = RunkkTextStyles.text(15, FontWeight.Black),
            color = if (state.isPaused) RunkkColors.Lavender else RunkkColors.Lime,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkStatusPill(gpsLabel(progress.signal))
        // 워치 심박이 들어오면 워치가 연결된 것으로 본다.
        if (progress.heartRate != null) RunkkStatusPill(stringResource(R.string.run_watch_connected))
    }
}

/** 측정 가운데: "거리" 라벨, 큰 거리 숫자와 목표 진행 막대. 정지 중에는 "거리 (멈춤)". */
@Composable
private fun RunningDistance(state: RunUiState) {
    val progress = state.progress
    val distanceSpoken = stringResource(R.string.run_distance_spoken, RunkkFormat.km(progress.distance))
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(if (state.isPaused) R.string.run_running_distance_paused else R.string.run_running_distance),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        RunkkBigNumber(
            value = RunkkFormat.km(progress.distance),
            caption = "km",
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = distanceSpoken
            }
        )
        state.targetProgress?.let { ratio ->
            RunkkProgressBar(ratio, modifier = Modifier.padding(top = 16.dp))
            Text(
                text = state.remaining?.let { stringResource(R.string.run_running_remaining, RunkkFormat.km(it)) }
                    ?: stringResource(R.string.run_running_goal_done),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** 측정 값 4칸: 경과(운동) 시간, 현재 페이스(최근 30초), 평균 페이스, 심박. 정지 중에는 멈춤 표시. */
@Composable
private fun RunningStats(progress: RunProgress) {
    val paused = progress.isPaused
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(if (paused) R.string.run_running_elapsed_paused else R.string.run_running_elapsed),
            StatTileValue(
                RunkkFormat.duration(progress.elapsed),
                spoken = RunkkFormat.durationSpoken(progress.elapsed)
            ),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(if (paused) R.string.run_running_current_pace_paused else R.string.run_running_current_pace),
            StatTileValue(
                if (paused) PAUSED_PACE else RunkkFormat.pace(progress.currentPace),
                spoken = if (paused) null else RunkkFormat.paceSpoken(progress.currentPace)
            ),
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.run_stat_avg_pace),
            StatTileValue(
                RunkkFormat.pace(progress.averagePace),
                spoken = RunkkFormat.paceSpoken(progress.averagePace)
            ),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(if (paused) R.string.run_running_heart_rate_paused else R.string.run_running_heart_rate),
            StatTileValue(progress.heartRate?.bpm?.toString() ?: RunkkFormat.EMPTY),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 정지 중 표시(B2-2, FR-194): "일시정지"와 정지한 지 얼마나 됐는지. 정지로 바뀌면 보조 기술이 "일시정지"를 알린다. */
@Composable
private fun PausedNotice(pausedFor: Duration?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Lavender, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.run_paused_label),
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.Lavender,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (pausedFor != null) {
            Text(
                stringResource(R.string.run_paused_for, RunkkFormat.duration(pausedFor)),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/**
 * 측정 아래 버튼 줄(FR-194, FR-181): 측정 중에는 취소, 잠금, 길게 눌러 종료, 일시정지. 정지 중에는 취소, 잠금, 재개, 길게 눌러 종료.
 * 정지 중에도 길게 눌러 끝낼 수 있다(FR-036). 취소는 확인을 거쳐 기록을 남기지 않고 끝낸다.
 */
@Composable
private fun RunningControls(isPaused: Boolean, actions: RunActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        CaptionedIconButton(
            RunkkIcons.Close,
            caption = stringResource(R.string.run_discard_caption),
            contentDescription = stringResource(R.string.run_discard_desc),
            onClick = actions.onDiscard
        )
        CaptionedIconButton(
            RunkkIcons.Lock,
            caption = stringResource(R.string.run_lock_caption),
            contentDescription = stringResource(R.string.run_running_lock_desc),
            onClick = actions.onLock
        )
        if (isPaused) {
            CaptionedIconButton(
                RunkkIcons.Play,
                caption = stringResource(R.string.run_resume_desc),
                contentDescription = stringResource(R.string.run_resume_desc),
                onClick = actions.onResume,
                isAccent = true
            )
            FinishButton(actions, caption = stringResource(R.string.run_hold_to_finish))
        } else {
            FinishButton(actions, caption = stringResource(R.string.run_hold_to_finish))
            CaptionedIconButton(
                RunkkIcons.Pause,
                caption = stringResource(R.string.run_pause_desc),
                contentDescription = stringResource(R.string.run_pause_desc),
                onClick = actions.onPause
            )
        }
    }
}

/** 길게 눌러 종료 버튼(짧게 누르면 안내, D-49). */
@Composable
private fun FinishButton(actions: RunActions, caption: String) {
    RunkkLongPressButton(
        onTap = actions.onFinishTap,
        onLongPress = actions.onFinishLongPress,
        tapLabel = stringResource(R.string.run_running_finish_tap),
        longPressLabel = stringResource(R.string.run_hold_to_finish),
        contentDescription = stringResource(R.string.run_running_finish_desc),
        caption = caption
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.Lavender))
    }
}

/** 원형 아이콘 버튼(64dp)과 아래 글자("취소", "잠금", "일시정지", "재개"). 글자는 버튼 이름과 같아 따로 읽지 않는다. */
@Composable
private fun CaptionedIconButton(
    icon: ImageVector,
    caption: String,
    contentDescription: String,
    onClick: () -> Unit,
    isAccent: Boolean = false
) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkIconButton(
            icon,
            contentDescription = contentDescription,
            onClick = onClick,
            visualSize = 64.dp,
            iconSize = 24.dp,
            borderColor = if (isAccent) RunkkColors.Lime else RunkkColors.BorderStrong
        )
        Text(
            caption,
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/**
 * 장시간 정지 자동 종료 안내(B2-3, FR-195): 정지 55분부터 남은 시간을 막대와 함께 보인다. "지금 종료"는 길게 눌러 종료와 같고,
 * "재개"는 측정을 이어 간다. 둘 중 하나를 고를 때까지 닫히지 않는다.
 */
@Composable
private fun PauseAutoEndDialog(
    pausedFor: Duration,
    autoEndIn: Duration,
    onFinishNow: () -> Unit,
    onResume: () -> Unit
) {
    val window = PauseLimit.END_AFTER.minus(PauseLimit.WARN_AFTER).seconds.coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = {},
        containerColor = RunkkColors.Surface,
        titleContentColor = RunkkColors.TextPrimary,
        textContentColor = RunkkColors.TextSecondary,
        title = {
            Text(
                stringResource(
                    R.string.run_auto_end_title,
                    PauseLimit.END_AFTER.minus(PauseLimit.WARN_AFTER).toMinutes()
                ),
                style = RunkkTextStyles.CardTitle
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.run_auto_end_message, pausedFor.toMinutes()), style = RunkkTextStyles.Body)
                RunkkProgressBar(
                    progress = autoEndIn.seconds.toFloat() / window,
                    color = RunkkColors.Lavender
                )
                Text(
                    stringResource(R.string.run_auto_end_left, RunkkFormat.duration(autoEndIn)),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(
                    stringResource(R.string.run_resume_desc),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onFinishNow) {
                Text(
                    stringResource(R.string.run_auto_end_finish_now),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lavender
                )
            }
        }
    )
}

@Composable
private fun LockedContent(state: RunUiState, actions: RunActions) {
    val paneTitle = stringResource(R.string.run_locked_pane_title)
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 잠금 화면으로 바뀌면 보조 기술이 화면 이름을 알린다.
            .semantics { this.paneTitle = paneTitle }
            // 잠금 화면에는 해제 버튼 말고 누를 것이 없다(FR-182).
            .background(RunkkColors.BackgroundLocked)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunkkStatusPill(stringResource(R.string.run_locked_pill)) {
                Icon(
                    RunkkIcons.Lock,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                modeTitle(state.targetDistance),
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = RunkkColors.TextSecondary
            )
        }
        // 잠금 중 워치 정지(PAUSED_LOCKED): 정지 표시만 보인다. 재개·종료는 잠금을 푼 뒤에 한다(D-120, D-157).
        if (state.isPaused) PausedNotice(state.pausedFor)
        LockedCenter(state.progress, modifier = Modifier.weight(1f).fillMaxWidth())
        state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
        LockedUnlockButton(actions, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** 잠금 화면 가운데(B3): 큰 거리와 km, 경과 시간과 평균·현재 페이스. */
@Composable
private fun LockedCenter(progress: RunProgress, modifier: Modifier = Modifier) {
    val distanceSpoken = stringResource(R.string.run_distance_spoken, RunkkFormat.km(progress.distance))
    val summarySpoken = stringResource(
        R.string.run_locked_summary_spoken,
        RunkkFormat.durationSpoken(progress.elapsed),
        RunkkFormat.paceSpoken(progress.averagePace)
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = distanceSpoken }
        ) {
            Text(RunkkFormat.km(progress.distance), style = RunkkTextStyles.DisplayHuge, color = RunkkColors.Lime)
            Text(
                "km",
                style = RunkkTextStyles.display(28),
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.clearAndSetSemantics { contentDescription = summarySpoken }
        ) {
            Text(
                RunkkFormat.duration(progress.elapsed),
                style = RunkkTextStyles.display(32),
                color = RunkkColors.TextPrimary
            )
            LockedPace(stringResource(R.string.run_locked_average), progress.averagePace)
            LockedPace(stringResource(R.string.run_locked_current), progress.currentPace)
        }
    }
}

/** 잠금 화면의 페이스 한 칸: "평균 5:32". */
@Composable
private fun LockedPace(label: String, pace: Pace?) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(RunkkFormat.pace(pace), style = RunkkTextStyles.display(24), color = RunkkColors.TextPrimary)
    }
}

/** 잠금 해제 버튼: 짧게 누르면 안내, 길게 누르면 해제. */
@Composable
private fun LockedUnlockButton(actions: RunActions, modifier: Modifier = Modifier) {
    RunkkLongPressButton(
        onTap = actions.onUnlockTap,
        onLongPress = actions.onUnlockLongPress,
        tapLabel = stringResource(R.string.run_locked_unlock_tap),
        longPressLabel = stringResource(R.string.run_locked_unlock_long),
        contentDescription = stringResource(R.string.run_locked_unlock_desc),
        caption = stringResource(R.string.run_locked_unlock_long),
        borderColor = RunkkColors.BorderStrong,
        borderWidth = 2.dp,
        modifier = modifier
    ) {
        Icon(
            RunkkIcons.Unlock,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

/** 정지 중 현재 페이스 자리(보드 "—"). */
private const val PAUSED_PACE = "—"

private const val RING_MILLIS = 1_000
private const val RING_ALPHA = 0.5f

private val previewProgress = RunProgress(
    elapsed = Duration.ofSeconds(712),
    distance = Distance(2_140),
    currentPace = Pace(321),
    averagePace = Pace(332),
    signal = SignalState.NORMAL,
    isLocked = false,
    heartRate = HeartRateSample(Instant.EPOCH, 152)
)

@Preview(name = "B1 시작 카운트다운", widthDp = 390, heightDp = 844)
@Composable
private fun CountdownPreview() {
    RunkkTheme(reduceMotion = true) {
        RunScreen(RunUiState(phase = RunPhase.COUNTDOWN, targetDistance = Distance(5_000)), RunActions())
    }
}

@Preview(name = "B2 러닝 측정", widthDp = 390, heightDp = 844)
@Composable
private fun RunningPreview() {
    RunkkTheme(reduceMotion = true) {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = previewProgress,
                targetDistance = Distance(5_000),
                splitBanner = UiText.Plain("2km, 11:04, 평균 페이스 5:32")
            ),
            RunActions()
        )
    }
}

@Preview(name = "B3 러닝 측정(화면 잠금)", widthDp = 390, heightDp = 844)
@Composable
private fun LockedPreview() {
    RunkkTheme(reduceMotion = true) {
        RunScreen(
            RunUiState(
                phase = RunPhase.LOCKED,
                progress = previewProgress.copy(isLocked = true),
                targetDistance = Distance(5_000)
            ),
            RunActions()
        )
    }
}
