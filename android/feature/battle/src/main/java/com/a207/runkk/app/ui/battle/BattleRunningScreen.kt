package com.a207.runkk.app.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLiveDot
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.live.BattleGuidance
import java.time.Duration
import kotlin.math.abs

/** 대결 진행(랜덤·고스트·나와의 싸움·유명인). 끝나면 결과 화면이 이 화면을 대신한다. */
@Composable
fun BattleRunningRoute(key: Routes.BattleRunning, viewModel: BattleRunningViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BattleRunningEvent.Finished -> {
                    navigator.back()
                    navigator.navigate(Routes.BattleResult(event.battleId))
                }
            }
        }
    }
    val close = {
        viewModel.onFailureClosed()
        navigator.back()
    }
    // 측정 중 뒤로가기로 대결을 떠나지 않게 안내만 한다(그만두기는 길게 누름 + 확인).
    BackHandler {
        if (state.phase == BattlePhase.FAILED) close() else viewModel.onForfeitTapped()
    }
    BattleRunningScreen(
        state = state,
        onForfeitTap = viewModel::onForfeitTapped,
        onForfeitLongPress = viewModel::onForfeitLongPressed,
        onForfeitConfirm = viewModel::onForfeitConfirmed,
        onForfeitDismiss = viewModel::onForfeitDismissed,
        onClose = close
    )
}

@Composable
fun BattleRunningScreen(
    state: BattleRunningUiState,
    onForfeitTap: () -> Unit,
    onForfeitLongPress: () -> Unit,
    onForfeitConfirm: () -> Unit,
    onForfeitDismiss: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header(state)
        when (state.phase) {
            BattlePhase.STARTING, BattlePhase.COUNTDOWN -> Countdown(state)

            BattlePhase.FAILED -> RunkkErrorState(
                state.error ?: "대결을 시작하지 못했어요",
                onRetry = onClose,
                retryLabel = "돌아가기",
                modifier = Modifier.weight(1f)
            )

            else -> Running(state)
        }
        if (state.phase != BattlePhase.FAILED) {
            state.hint?.let { RunkkHint(it, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            RunkkLongPressButton(
                onTap = onForfeitTap,
                onLongPress = onForfeitLongPress,
                tapLabel = "그만두기 안내",
                longPressLabel = "길게 눌러 그만두기",
                contentDescription = "대결 그만두기",
                caption = "길게 눌러 그만두기",
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
    }
    if (state.confirmingForfeit) {
        RunkkConfirmDialog(
            title = "대결을 그만둘까요?",
            message = "그만두면 이 대결은 패로 기록돼요. 지금까지 달린 기록은 저장돼요.",
            confirmLabel = "그만두기",
            onConfirm = onForfeitConfirm,
            onDismiss = onForfeitDismiss,
            dismissLabel = "계속 달리기",
            destructive = true
        )
    }
}

@Composable
private fun Header(state: BattleRunningUiState) {
    val label = when (state.kind) {
        OpponentKind.RANDOM -> "LIVE BATTLE"
        OpponentKind.SELF -> "SELF BATTLE"
        OpponentKind.CELEBRITY -> "CELEB BATTLE"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkLiveDot(color = RunkkColors.Lavender)
        Text(
            "$label · ${distanceLabel(state.distance)}",
            style = RunkkTextStyles.Overline,
            color = RunkkColors.Lavender,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.weight(1f))
        RunkkStatusPill(opponentLabel(state))
    }
}

private fun opponentLabel(state: BattleRunningUiState): String = when (state.kind) {
    OpponentKind.SELF -> "과거의 나"
    else -> state.opponentName.ifBlank { "상대" }
}

@Composable
private fun ColumnScope.Countdown(state: BattleRunningUiState) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(
            state.countdown?.toString() ?: "READY",
            style = RunkkTextStyles.display(state.countdown?.let { 160 } ?: 72),
            color = RunkkColors.Lime
        )
        Text(
            if (state.countdown != null) "초 뒤 함께 출발해요" else "대결을 준비하고 있어요",
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary
        )
        Text(
            "${opponentLabel(state)}와 ${distanceLabel(state.distance)} 대결",
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

@Composable
private fun ColumnScope.Running(state: BattleRunningUiState) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GuidanceBanner(state)
        Gap(state)
        if (state.kind == OpponentKind.CELEBRITY) CharacterTrack(state)
        ProgressCard(state)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkStatTile(
                "경과",
                RunkkFormat.duration(state.elapsed),
                spoken = RunkkFormat.durationSpoken(state.elapsed),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "남은 거리",
                RunkkFormat.km(state.distance.minus(state.myDistance)),
                unit = "KM",
                spoken = "${RunkkFormat.km(state.distance.minus(state.myDistance))}킬로미터",
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "평균 페이스",
                RunkkFormat.pace(state.averagePace),
                spoken = RunkkFormat.paceSpoken(state.averagePace),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (state.error != null) {
            Text(
                state.error,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun GuidanceBanner(state: BattleRunningUiState) {
    if (state.phase == BattlePhase.CLOSING) {
        // 바뀌는 초는 보이기만 하고, 보조 기술에는 고정 문구를 한 번 알린다(매초 끊어 읽지 않게).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Medium)
                .background(RunkkColors.Lavender)
                .padding(16.dp)
                .clearAndSetSemantics {
                    contentDescription = "${state.arrivedName ?: "상대"} 도착. 제한 시간 안에 도착하지 못하면 대결이 끝나요"
                    liveRegion = LiveRegionMode.Assertive
                }
        ) {
            Text(
                "${state.arrivedName ?: "상대"} 도착! ${state.closingSeconds ?: 0}초 안에 도착하지 못하면 대결이 끝나요",
                style = RunkkTextStyles.text(15, FontWeight.Black),
                color = RunkkColors.OnLime
            )
        }
        return
    }
    when (state.guidance) {
        BattleGuidance.Kind.OVERTOOK -> RunkkBanner(
            if (state.kind == OpponentKind.SELF) "과거의 나를 앞질렀어요" else "추월했어요!",
            strong = true,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        )

        BattleGuidance.Kind.OVERTAKEN -> Text(
            "역전당했어요",
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

@Composable
private fun Gap(state: BattleRunningUiState) {
    val gap = state.gapMeters
    val ahead = (gap ?: 0) >= 0
    val spoken = when {
        gap == null -> "상대 진행 기다리는 중"
        gap >= 0 -> "${gap}미터 앞서는 중"
        else -> "${abs(gap)}미터 뒤처지는 중"
    }
    Column(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "상대와 거리 차이 $spoken" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("상대와 거리 차이", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                gap?.let { if (it >= 0) "+$it" else "$it" } ?: RunkkFormat.EMPTY,
                style = RunkkTextStyles.DisplayLarge,
                color = if (ahead) RunkkColors.Lime else RunkkColors.Lavender
            )
            Text(
                "M",
                style = RunkkTextStyles.display(36),
                color = if (ahead) RunkkColors.Lime else RunkkColors.Lavender,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            listOfNotNull(
                when {
                    gap == null -> null
                    ahead -> "앞서는 중"
                    else -> "뒤처지는 중"
                },
                "추월 ${state.overtakes}회"
            ).joinToString(" · "),
            style = RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 유명인 대결의 캐릭터 트랙(FR-099): 두 캐릭터 사이 간격이 거리 차에 비례한다. */
@Composable
private fun CharacterTrack(state: BattleRunningUiState) {
    val total = state.distance.meters.coerceAtLeast(1).toFloat()
    val mine = (state.myDistance.meters / total).coerceIn(0f, 1f)
    val theirs = ((state.opponentDistance?.meters ?: 0L) / total).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .clearAndSetSemantics {
                contentDescription = "캐릭터 트랙, 나 ${RunkkFormat.km(state.myDistance)}킬로미터, " +
                    "${state.opponentName} ${RunkkFormat.km(state.opponentDistance ?: Distance.ZERO)}킬로미터"
            }
    ) {
        val usable = maxWidth - CHARACTER.dp - 16.dp
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(2.dp)
                .background(RunkkColors.BorderStrong)
        )
        SdCharacter(
            size = CHARACTER,
            label = "SD",
            modifier = Modifier.align(Alignment.TopStart).offset(x = 8.dp + usable * theirs, y = 8.dp)
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = 8.dp + usable * mine, y = (-8).dp)
                .size(CHARACTER.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(RunkkColors.LimeContainer)
                .border(2.dp, RunkkColors.Lime, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("나", style = RunkkTextStyles.text(12, FontWeight.Black), color = RunkkColors.Lime)
        }
    }
}

private const val CHARACTER = 36

@Composable
private fun ProgressCard(state: BattleRunningUiState) {
    val total = state.distance.meters.coerceAtLeast(1).toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.ExtraLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ProgressRow("나", state.myDistance, state.myDistance.meters / total, mine = true)
        ProgressRow(
            opponentLabel(state),
            state.opponentDistance ?: Distance.ZERO,
            (state.opponentDistance?.meters ?: 0L) / total,
            mine = false
        )
    }
}

@Composable
private fun ProgressRow(name: String, distance: Distance, progress: Float, mine: Boolean) {
    val color = if (mine) RunkkColors.Lime else RunkkColors.Lavender
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "$name ${RunkkFormat.km(distance)}킬로미터"
        }
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                name,
                style = if (mine) RunkkTextStyles.text(14, FontWeight.Black) else RunkkTextStyles.Body,
                color = if (mine) RunkkColors.TextPrimary else RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text("${RunkkFormat.km(distance)} KM", style = RunkkTextStyles.display(22), color = color)
        }
        RunkkProgressBar(progress, height = 12.dp, color = color)
    }
}

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
                elapsed = Duration.ofSeconds(940),
                guidance = BattleGuidance.Kind.OVERTOOK,
                overtakes = 2
            ),
            {},
            {},
            {},
            {},
            {}
        )
    }
}
