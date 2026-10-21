package com.a207.runkk.app.ui.run

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
import com.a207.runkk.app.core.designsystem.RunkkOverline
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.run.HeartRateSample
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
    val actions = remember(viewModel, navigator, permission) {
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
            onRequestPermission = {
                permission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            },
            onOpenConsents = { navigator.navigate(Routes.CONSENTS) },
            onRetry = viewModel::onRetryStart,
            onConsentDismiss = viewModel::onConsentDialogDismissed,
            onErrorShown = viewModel::onErrorShown
        )
    }
    RunScreen(state = state, actions = actions)
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
    if (state.showConsentDialog) {
        RunkkConfirmDialog(
            title = stringResource(R.string.run_consent_title),
            message = stringResource(R.string.run_consent_message),
            confirmLabel = stringResource(R.string.run_consent_allow),
            onConfirm = actions.onRequestPermission,
            dismissLabel = stringResource(R.string.run_consent_manage),
            onDismiss = actions.onOpenConsents,
            onDismissRequest = actions.onConsentDismiss
        )
    }
}

/** 모드 라벨 끝: 목표 거리가 있으면 "5K", 없으면 "FREE". */
private fun modeSuffix(target: Distance?): String = target?.let(RunkkFormat::targetLabel) ?: "FREE"

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

/** 카운트다운 위쪽 줄: 취소와 GPS 상태. */
@Composable
private fun CountdownTopBar(signal: SignalState?, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RunkkPillButton(stringResource(R.string.run_countdown_cancel), onClick = onCancel)
        Spacer(Modifier.weight(1f))
        RunkkStatusPill(gpsLabel(signal)) {
            RunkkLiveDot(
                color = if (signal ==
                    SignalState.UNSTABLE
                ) {
                    RunkkColors.Lavender
                } else {
                    RunkkColors.Lime
                }
            )
        }
    }
}

/** 카운트다운 가운데: 모드 이름과 숫자 원(준비 단계에서는 다시 시도). */
@Composable
private fun CountdownCenter(state: RunUiState, actions: RunActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        val mode = "FREE RUN · ${modeSuffix(state.targetDistance)}"
        val modeSpoken = RunkkFormat.spokenLabel(mode)
        Text(
            mode,
            style = RunkkTextStyles.display(20, letterSpacingEm = 0.12f),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.semantics { contentDescription = modeSpoken }
        )
        if (state.phase == RunPhase.COUNTDOWN) {
            CountdownBall(state.countdown, actions.onCountdownTapped)
        } else {
            RunkkPillButton(stringResource(R.string.run_countdown_retry), onClick = actions.onRetry)
        }
    }
}

/** 카운트다운 아래 카드: 오늘 계획(목표 거리·페이스)과 설정 바꾸기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountdownPlanCard(state: RunUiState, onSettings: () -> Unit) {
    RunkkCard(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.run_countdown_plan),
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
                highlighted =
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
    Box(
        modifier = Modifier
            .size(240.dp)
            .drawBehind {
                if (ring >
                    0f
                ) {
                    drawCircle(
                        RunkkColors.Lime.copy(alpha = (1f - ring) * RING_ALPHA),
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
    val progress = state.progress
    Column(
        modifier = Modifier.fillMaxSize().background(
            RunkkColors.Background
        ).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RunningTopBar(state)
        state.splitBanner?.let {
            RunkkBanner(it.asString(), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        RunningDistance(state)
        RunningStats(progress)
        Spacer(Modifier.weight(1f))
        state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
        RunningControls(actions)
    }
}

/** 측정 위쪽 줄: 모드 이름과 GPS(워치 연결 시 표시) 상태. */
@Composable
private fun RunningTopBar(state: RunUiState) {
    val progress = state.progress
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        RunkkLiveDot()
        RunkkOverline("RUNNING · ${modeSuffix(state.targetDistance)}", color = RunkkColors.Lime)
        Spacer(Modifier.weight(1f))
        RunkkStatusPill(
            if (progress.heartRate != null) {
                stringResource(R.string.run_running_gps_with_watch, gpsLabel(progress.signal))
            } else {
                gpsLabel(progress.signal)
            }
        )
    }
}

/** 측정 가운데: 큰 거리 숫자와 목표 진행 막대. */
@Composable
private fun RunningDistance(state: RunUiState) {
    val progress = state.progress
    val distanceSpoken = stringResource(R.string.run_distance_spoken, RunkkFormat.km(progress.distance))
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkBigNumber(
            value = RunkkFormat.km(progress.distance),
            caption = "KILOMETERS",
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

/** 측정 값 4칸: 경과 시간·현재 페이스·평균 페이스·심박. */
@Composable
private fun RunningStats(progress: RunProgress) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.run_running_elapsed),
            RunkkFormat.duration(progress.elapsed),
            spoken = RunkkFormat.durationSpoken(progress.elapsed),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.run_running_current_pace),
            RunkkFormat.pace(progress.currentPace),
            spoken = RunkkFormat.paceSpoken(progress.currentPace),
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.run_stat_avg_pace),
            RunkkFormat.pace(progress.averagePace),
            spoken = RunkkFormat.paceSpoken(progress.averagePace),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.run_running_heart_rate),
            progress.heartRate?.bpm?.toString() ?: RunkkFormat.EMPTY,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 측정 아래 버튼 줄: 화면 잠금과 길게 눌러 종료. */
@Composable
private fun RunningControls(actions: RunActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        RunkkIconButton(
            RunkkIcons.Lock,
            contentDescription = stringResource(R.string.run_running_lock_desc),
            onClick = actions.onLock,
            visualSize = 64.dp,
            iconSize = 24.dp,
            borderColor = RunkkColors.BorderStrong,
            modifier = Modifier.padding(top = 16.dp)
        )
        RunkkLongPressButton(
            onTap = actions.onFinishTap,
            onLongPress = actions.onFinishLongPress,
            tapLabel = stringResource(R.string.run_running_finish_tap),
            longPressLabel = stringResource(R.string.run_hold_to_finish),
            contentDescription = stringResource(R.string.run_running_finish_desc),
            caption = stringResource(R.string.run_hold_to_finish)
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.Lavender))
        }
        Spacer(Modifier.width(64.dp))
    }
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
            RunkkOverline("FREE RUN · ${modeSuffix(state.targetDistance)}")
        }
        LockedCenter(state.progress, modifier = Modifier.weight(1f).fillMaxWidth())
        state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.CenterHorizontally)) }
        LockedUnlockButton(actions, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** 잠금 화면 가운데: 큰 거리와 경과 시간·평균 페이스. */
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
        Text(
            RunkkFormat.km(progress.distance),
            style = RunkkTextStyles.DisplayHuge,
            color = RunkkColors.Lime,
            modifier = Modifier.semantics { contentDescription = distanceSpoken }
        )
        Text(
            text = buildAnnotatedString {
                append(RunkkFormat.duration(progress.elapsed))
                withStyle(SpanStyle(color = RunkkColors.BorderStrong)) { append("   /   ") }
                append(RunkkFormat.pace(progress.averagePace))
                withStyle(
                    SpanStyle(color = RunkkColors.TextSecondary, fontSize = RunkkTextStyles.display(14).fontSize)
                ) {
                    append(" AVG")
                }
            },
            style = RunkkTextStyles.display(24),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { contentDescription = summarySpoken }
        )
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
        caption = stringResource(R.string.run_hold_to_unlock),
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
                splitBanner = UiText.Plain("2km 통과 · 11:04 · 평균 5:32")
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
