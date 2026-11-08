package com.a207.runkk.app.ui.run

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.KakaoRunMap
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalKakaoMapKey
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.RouteCanvas
import com.a207.runkk.app.core.designsystem.RouteMarker
import com.a207.runkk.app.core.designsystem.RunPermissionEffect
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkRunningFigure
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.canShowKakaoMap
import com.a207.runkk.app.core.designsystem.rememberHeadingLeft
import com.a207.runkk.app.core.designsystem.rememberIsMoving
import com.a207.runkk.app.core.designsystem.runCycleOf
import com.a207.runkk.app.core.designsystem.runFrameMillis
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RouteLine
import com.a207.runkk.app.core.model.SignalState
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.RunkkNavigator
import com.a207.runkk.app.domain.run.HeartRateSample
import com.a207.runkk.app.domain.run.RunProgress
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** 자유런 시작 카운트다운(B1)·러닝 측정(B2)·일시정지(B4). 앱 화면 잠금은 없다(D-256). */
@Composable
fun RunRoute(viewModel: RunViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.onRetryStart()
        }
    RunEventEffect(viewModel, navigator)
    // 카운트다운에 들어서면(위치 권한·동의 다음) 진행 알림·신체 활동 권한을 한 번 묻는다.
    RunPermissionEffect(shouldAsk = state.phase == RunPhase.COUNTDOWN)
    // 동의 관리에서 돌아오면 준비 단계를 다시 확인한다.
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
private fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
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
                    navigator.navigate(Routes.RunResult(event.recordId, isJustFinished = true))
                }

                RunEvent.NotSaved -> navigator.restart(Routes.HOME)

                RunEvent.Exit -> navigator.selectTab(Routes.HOME)
            }
        }
    }
}

/** 측정 중 뒤로 가기는 종료가 아니라 "길게 눌러 종료" 안내다(FR-181). */
@Composable
private fun RunBackHandler(phase: RunPhase, viewModel: RunViewModel) {
    BackHandler {
        when (phase) {
            RunPhase.COUNTDOWN, RunPhase.PREPARING -> viewModel.onCancelCountdown()
            RunPhase.RUNNING -> viewModel.onFinishTapped()
            RunPhase.FINISHING -> Unit
        }
    }
}

/** 러닝 화면 사용자 행동. 누름 종류마다 다른 행동이다(D-49). */
data class RunActions(
    val onCancel: () -> Unit = {},
    val onCountdownTapped: () -> Unit = {},
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
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        when (state.phase) {
            RunPhase.PREPARING, RunPhase.COUNTDOWN -> CountdownContent(state, actions)
            RunPhase.RUNNING, RunPhase.FINISHING -> RunningContent(state, actions)
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
}

/**
 * 카운트다운 설정 요약 한 줄(D-264): 목표 거리, 음성·진동 안내가 켜져 있으면 "음성"·"진동", 오늘 코치 계획을 따르면 "코치보이스".
 * 꺼진 항목은 빼고 " · "로 잇는다. 모두 빠지면 빈 줄이다.
 */
@Composable
internal fun countdownSummary(state: RunUiState): String = listOfNotNull(
    state.targetDistance?.let { RunkkFormat.targetLabel(it) },
    stringResource(R.string.run_countdown_voice).takeIf { state.isVoiceOn },
    stringResource(R.string.run_countdown_vibration).takeIf { state.isVibrationOn },
    stringResource(R.string.run_countdown_coach_voice).takeIf { state.isFromCoachPlan }
).joinToString(SUMMARY_SEPARATOR)

/** 카운트다운(B1): 큰 숫자 원, 설정 요약 한 줄. 위치·워치 상태와 눌러 설정 바꾸기는 없다(D-264). */
@Composable
private fun CountdownContent(state: RunUiState, actions: RunActions) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (state.phase == RunPhase.PREPARING) {
                RunkkPillButton(stringResource(R.string.run_countdown_cancel), onClick = actions.onCancel)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            if (state.phase == RunPhase.COUNTDOWN) {
                CountdownBall(state.countdown, actions.onCountdownTapped)
            } else {
                RunkkPillButton(stringResource(R.string.run_countdown_retry), onClick = actions.onRetry)
            }
            Text(countdownSummary(state), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
    }
}

/** 큰 숫자 원(220dp). 고리가 남은 초만큼 줄고 누르면 바로 시작한다(FR-180). 동작 줄이기면 고리가 움직이지 않는다. */
@Composable
private fun CountdownBall(number: Int, onTap: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val sweep = if (reduce) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "countdownRing")
        val value by transition.animateFloat(
            1f,
            0f,
            InfiniteRepeatableSpec(tween(RING_MILLIS), RepeatMode.Restart),
            label = "ring"
        )
        value
    }
    val startNow = stringResource(R.string.run_countdown_start_now)
    // 바깥 원은 채움이라 라이트에서도 라임이다(D85).
    val outer = RunkkColors.LimeFill
    val ring = RunkkColors.LimeContainer
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(outer)
                val stroke = 8.dp.toPx()
                val inset = 14.dp.toPx()
                drawArc(
                    color = ring,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    style = Stroke(width = stroke)
                )
                drawCircle(ring, radius = size.minDimension / 2 - 30.dp.toPx())
            }
            .clickable(role = Role.Button, onClickLabel = startNow, onClick = onTap)
            .semantics {
                contentDescription = "$number"
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$number",
            style = RunkkTextStyles.display(120, lineHeightRatio = 1f).copy(fontStyle = FontStyle.Italic),
            color = RunkkColors.TextPrimary
        )
    }
}

/**
 * 측정(B2)·일시정지(B4): 큰 거리(km), 시간·평균 페이스·심박 세 칸, 경로선과 내 아바타, 아래 버튼.
 * 현재 페이스는 폰 화면에 없다(워치 표시용으로 진행값에만 남는다, D-263). 정지 중에는 반투명 막을 덮고 종료·재개만 받는다.
 */
@Composable
private fun RunningContent(state: RunUiState, actions: RunActions) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RunningDistance(state.progress.distance)
            RunningStats(state.progress)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                RunningRoute(state.route, state.progress, isPaused = state.isPaused)
                state.splitBanner?.takeIf { !state.isPaused }?.let {
                    RunkkBanner(
                        it.asString(),
                        modifier = Modifier.align(Alignment.TopCenter).semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
                state.hint?.let { RunkkHint(it.asString(), modifier = Modifier.align(Alignment.BottomCenter)) }
            }
            if (!state.isPaused) RunningControls(actions)
        }
        if (state.isPaused) PausedOverlay(actions)
    }
}

/** 큰 거리 "2.14"와 오른쪽 아래 작은 "km". */
@Composable
private fun RunningDistance(distance: Distance) {
    val spoken = stringResource(R.string.run_distance_spoken, RunkkFormat.km(distance))
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(RunkkFormat.km(distance), style = RunkkTextStyles.DisplayHuge, color = RunkkColors.TextPrimary)
        Text(
            stringResource(R.string.run_unit_km),
            style = RunkkTextStyles.text(18),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )
    }
}

/** 지표 세 칸: 시간(운동 시간), 평균 페이스, 심박(bpm, 워치가 없으면 "--"). */
@Composable
private fun RunningStats(progress: RunProgress) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        StatColumn(
            RunkkFormat.duration(progress.elapsed),
            stringResource(R.string.run_stat_time),
            RunkkFormat.durationSpoken(progress.elapsed)
        )
        StatColumn(
            RunkkFormat.pace(progress.averagePace),
            stringResource(R.string.run_stat_avg_pace),
            RunkkFormat.paceSpoken(progress.averagePace)
        )
        StatColumn(progress.heartRate?.bpm?.toString() ?: NO_HEART_RATE, stringResource(R.string.run_stat_heart_rate))
    }
}

@Composable
private fun StatColumn(value: String, label: String, spoken: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            spoken?.let { contentDescription = "$label $it" }
        }
    ) {
        Text(value, style = RunkkTextStyles.display(28), color = RunkkColors.TextPrimary)
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/**
 * 경로선과 경로 끝의 내 아바타(D-261). 경로가 아직 없으면 아바타만 가운데에 둔다. 아바타는 경로 전후 같은 그림이다:
 * 움직이는 동안 달리기 동작을 현재 페이스 빠르기로 넘기고, 멈춰 있거나 일시정지면 서 있는다.
 */
@Composable
private fun RunningRoute(route: RouteLine, progress: RunProgress, isPaused: Boolean) {
    val me = route.points.lastOrNull()
    val isMoving = rememberIsMoving(progress.distance) && !isPaused && progress.currentPace != null
    val frameMillis = runFrameMillis(progress.currentPace)
    val isLeft = rememberHeadingLeft(route)
    // Kakao 지도를 띄울 수 있으면(키 + 지도 라이브러리) 지도 위에서 카메라가 나를 따라가고, 아니면 격자 바탕 경로선이다.
    // 실행 중 지도 오류가 나면 격자로 바꾼다(D-340).
    var isMapFailed by remember { mutableStateOf(false) }
    if (canShowKakaoMap(LocalKakaoMapKey.current, isMapFailed)) {
        KakaoRunMap(route = route, modifier = Modifier.fillMaxSize(), onMapError = { isMapFailed = true }) {
            RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft)
        }
        return
    }
    val markers = remember(me, isMoving, frameMillis, isLeft) {
        listOfNotNull(
            me?.let {
                RouteMarker(point = it, figure = { RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft) })
            }
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RouteCanvas(route = route, markers = markers, modifier = Modifier.fillMaxSize())
        if (me == null) Box(Modifier.size(72.dp)) { RunkkRunningFigure(isMoving, frameMillis, isFacingLeft = isLeft) }
    }
}

/**
 * 측정 아래 버튼(FR-181, FR-194): 취소(기록 없이 끝내기, 디자인 요청 중이라 기존 자리 유지, D-263), 길게 눌러 종료, 일시정지.
 */
@Composable
private fun RunningControls(actions: RunActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top
    ) {
        DiscardButton(actions.onDiscard)
        FinishButton(actions)
        RoundButton(
            icon = RunkkIcons.Pause,
            contentDescription = stringResource(R.string.run_pause_desc),
            onClick = actions.onPause,
            background = RunkkColors.LimeFill,
            tint = RunkkColors.OnLimeFill,
            shape = CutCornerShape(percent = OCTAGON_CUT)
        )
    }
}

/** 일시정지(B4): 반투명 막 위에 취소·종료·재개. 보조 기술에는 "일시정지" 화면으로 알린다. */
@Composable
private fun PausedOverlay(actions: RunActions) {
    val paneTitle = stringResource(R.string.run_paused_label)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = PAUSE_SCRIM_ALPHA))
            .semantics { this.paneTitle = paneTitle }
            .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            DiscardButton(actions.onDiscard)
            FinishButton(actions)
            RoundButton(
                icon = RunkkIcons.Play,
                contentDescription = stringResource(R.string.run_resume_desc),
                onClick = actions.onResume,
                background = RunkkColors.Surface,
                tint = RunkkColors.TextPrimary
            )
        }
    }
}

/** 길게 눌러 종료 버튼(짧게 누르면 안내, D-49): 흰 원 안 검은 둥근 사각형, 아래 "길게 눌러 종료". */
@Composable
private fun FinishButton(actions: RunActions) {
    RunkkLongPressButton(
        onTap = actions.onFinishTap,
        onLongPress = actions.onFinishLongPress,
        tapLabel = stringResource(R.string.run_running_finish_tap),
        longPressLabel = stringResource(R.string.run_hold_to_finish),
        contentDescription = stringResource(R.string.run_running_finish_desc),
        caption = stringResource(R.string.run_hold_to_finish),
        size = BIG_BUTTON,
        borderColor = RunkkColors.Surface,
        borderWidth = 0.dp,
        background = RunkkColors.Surface
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(RunkkColors.TextPrimary))
    }
}

/** 취소(기록 없이 끝내기, FR-219). 확인을 거친다. */
@Composable
private fun DiscardButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 20.dp)) {
        RoundButton(
            icon = RunkkIcons.Close,
            contentDescription = stringResource(R.string.run_discard_desc),
            onClick = onClick,
            background = RunkkColors.Surface,
            tint = RunkkColors.TextSecondary,
            size = SMALL_BUTTON
        )
        Text(
            stringResource(R.string.run_discard_caption),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp).clearAndSetSemantics {}
        )
    }
}

/** 둥근(또는 팔각) 아이콘 버튼. */
@Composable
private fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    background: Color,
    tint: Color,
    shape: Shape = CircleShape,
    size: Dp = BIG_BUTTON
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 3))
    }
}

private const val RING_MILLIS = 1_000
private const val OCTAGON_CUT = 30
private const val PAUSE_SCRIM_ALPHA = 0.45f
private const val SUMMARY_SEPARATOR = " · "
private const val NO_HEART_RATE = "--"
private val BIG_BUTTON = 88.dp
private val SMALL_BUTTON = 48.dp

private val previewProgress = RunProgress(
    elapsed = Duration.ofSeconds(712),
    distance = Distance(2_140),
    currentPace = Pace(321),
    averagePace = Pace(332),
    signal = SignalState.NORMAL,
    heartRate = HeartRateSample(Instant.EPOCH, 152)
)

private val previewRoute = RouteLine(
    listOf(
        GeoPoint(37.5000, 127.0000),
        GeoPoint(37.5012, 127.0010),
        GeoPoint(37.5020, 127.0030),
        GeoPoint(37.5035, 127.0042)
    )
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
                route = previewRoute,
                targetDistance = Distance(5_000),
                splitBanner = UiText.Plain("2km, 11:04, 평균 페이스 5:32")
            ),
            RunActions()
        )
    }
}

@Preview(name = "B4 일시정지", widthDp = 390, heightDp = 844)
@Composable
private fun PausedPreview() {
    RunkkTheme(reduceMotion = true) {
        RunScreen(
            RunUiState(
                phase = RunPhase.RUNNING,
                progress = previewProgress.copy(isPaused = true),
                route = previewRoute,
                targetDistance = Distance(5_000)
            ),
            RunActions()
        )
    }
}
