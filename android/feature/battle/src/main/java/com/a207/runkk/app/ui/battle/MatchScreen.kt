package com.a207.runkk.app.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalReduceMotion
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatusPill
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.live.MatchFound
import java.time.Duration
import java.util.Locale

/** 랜덤 매칭: 거리 고르기 → 대기(취소) → 성사(VS·양쪽 최고 기록·자동 출발). 측정이 시작되면 대결 진행으로 바뀐다. */
@Composable
fun MatchRoute(viewModel: MatchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MatchEvent.Started -> {
                    navigator.back()
                    navigator.navigate(event.key)
                }

                MatchEvent.Exit -> navigator.back()
            }
        }
    }
    // 대기 중 뒤로가기는 매칭 취소, 성사 뒤에는 자동 출발이라 막는다(FR-087).
    BackHandler(enabled = state.phase != MatchPhase.CHOOSING) { viewModel.onCancel() }
    MatchScreen(
        state = state,
        onDistanceSelected = viewModel::onDistanceSelected,
        onStart = viewModel::onStartMatching,
        onCancel = viewModel::onCancel,
        onBack = viewModel::onCancel
    )
}

@Composable
fun MatchScreen(
    state: MatchUiState,
    onDistanceSelected: (Distance) -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)
    ) {
        when (state.phase) {
            MatchPhase.CHOOSING, MatchPhase.REQUESTING -> Choosing(state, onDistanceSelected, onStart, onBack)
            MatchPhase.WAITING, MatchPhase.CANCELLING -> Waiting(state, onCancel)
            MatchPhase.MATCHED -> state.match?.let { Matched(it, state.startsInSeconds) }
        }
    }
}

@Composable
private fun Overline(text: String, accent: Boolean = false, trailing: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text,
            style = RunkkTextStyles.display(20, letterSpacingEm = 0.08f),
            color = if (accent) RunkkColors.Lime else RunkkColors.TextPrimary,
            modifier = Modifier.semantics {
                heading()
                contentDescription = RunkkFormat.spokenLabel(text)
            }
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Choosing(
    state: MatchUiState,
    onDistanceSelected: (Distance) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkIconButton(RunkkIcons.Back, contentDescription = "뒤로", onClick = onBack)
        Overline("BATTLE RUN")
    }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(
            "같은 티어·같은 거리 상대와 동시에 달려요",
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary
        )
        Text(
            "15초 안에 상대가 없으면 다른 러너의 기록과 바로 대결해요. 고른 거리는 다음 배틀런에도 그대로 써요.",
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary
        )
        Text("대결 거리", style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.TextSecondary)
        RunkkSegmentRow(
            options = MatchUiState.DISTANCES.map(RunkkFormat::targetLabel),
            selectedIndex = MatchUiState.DISTANCES.indexOf(state.distance),
            onSelect = { onDistanceSelected(MatchUiState.DISTANCES[it]) },
            modifier = Modifier.semantics { contentDescription = "대결 거리" }
        )
        if (state.error != null) {
            Text(
                state.error,
                style = RunkkTextStyles.text(13, FontWeight.Bold),
                color = RunkkColors.Lavender,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    RunkkPrimaryButton(
        text = "${RunkkFormat.targetLabel(state.distance)} 매칭 시작",
        onClick = onStart,
        loading = state.phase == MatchPhase.REQUESTING,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Waiting(state: MatchUiState, onCancel: () -> Unit) {
    Overline("BATTLE RUN") { RunkkStatusPill(RunkkFormat.targetLabel(state.distance)) }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically)
    ) {
        Radar()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {
                contentDescription = "상대를 찾고 있어요, ${state.waitingSeconds}초 지남"
            }
        ) {
            Text("상대를 찾고 있어요", style = RunkkTextStyles.Headline, color = RunkkColors.TextPrimary)
            Text(
                waitLabel(state.waitingSeconds),
                style = RunkkTextStyles.display(56),
                color = RunkkColors.Lime,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        if (state.error != null) {
            Text(
                state.error,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
    RunkkSecondaryButton(
        text = if (state.phase == MatchPhase.CANCELLING) "취소하는 중" else "매칭 취소",
        onClick = onCancel,
        enabled = state.phase == MatchPhase.WAITING,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 대기 레이더(시안 radar 2.4초). 동작 줄이기면 멈춘 고리로 그린다. */
@Composable
private fun Radar() {
    val reduce = LocalReduceMotion.current
    val wave = if (reduce) 0f else radarWave()
    Box(modifier = Modifier.size(240.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        listOf(0f, 0.5f).forEach { offset ->
            val t = if (reduce) offset + 0.25f else (wave + offset) % 1f
            Box(
                Modifier
                    .size(240.dp)
                    .scale(RADAR_MIN + (RADAR_MAX - RADAR_MIN) * t)
                    .graphicsLayer { alpha = if (reduce) REDUCED_ALPHA else (1f - t) * RADAR_ALPHA }
                    .border(2.dp, RunkkColors.Lime, CircleShape)
            )
        }
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(RunkkColors.Lime),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Swords,
                contentDescription = null,
                tint = RunkkColors.OnLime,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/** 0 → 1 반복 값(동작 줄이기가 아닐 때만 부른다). */
@Composable
private fun radarWave(): Float {
    val transition = rememberInfiniteTransition(label = "radar")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RADAR_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    return wave
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.Matched(match: MatchFound, startsIn: Long?) {
    Overline("MATCHED", accent = true) { RunkkStatusPill(RunkkFormat.targetLabel(match.distance)) }
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 성사 순간 한 번 알린다(고정 문구).
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = "상대를 찾았어요. ${match.opponentName}, ${match.opponentTier}"
                liveRegion = LiveRegionMode.Polite
            }
        ) {
            Contender("나", tier = null, accent = true, modifier = Modifier.weight(1f))
            Text(
                "VS",
                style = RunkkTextStyles.display(48),
                color = RunkkColors.Lime,
                modifier = Modifier.clearAndSetSemantics {
                }
            )
            Contender(match.opponentName, tier = match.opponentTier, accent = false, modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Large)
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
                .padding(16.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${RunkkFormat.targetLabel(match.distance)} 최고 기록, 나 " +
                        "${bestSpoken(match.myBest)}, 상대 ${bestSpoken(match.opponentBest)}"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            BestTime(match.myBest, Modifier.weight(1f))
            Text(
                "${RunkkFormat.targetLabel(match.distance)} 최고 기록",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            BestTime(match.opponentBest, Modifier.weight(1f))
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (startsIn != null) {
            Text(
                startsIn.toString(),
                style = RunkkTextStyles.display(96, lineHeightRatio = 0.9f),
                color = RunkkColors.Lime
            )
            Text("초 후 자동 출발", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        } else {
            Text("곧 자동으로 출발해요", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        }
    }
}

@Composable
private fun Contender(name: String, tier: String?, accent: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(width = 120.dp, height = 152.dp)
                .clip(RunkkShapes.ExtraLarge)
                .background(RunkkColors.Surface)
                .border(2.dp, if (accent) RunkkColors.Lime else RunkkColors.Lavender, RunkkShapes.ExtraLarge)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Text("아바타", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        Text(
            name,
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(tier ?: " ", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

@Composable
private fun BestTime(best: Duration?, modifier: Modifier) {
    Text(
        best?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY,
        style = RunkkTextStyles.display(32),
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

private fun bestSpoken(best: Duration?): String = best?.let(RunkkFormat::durationSpoken) ?: "기록 없음"

internal fun waitLabel(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

private const val SECONDS_PER_MINUTE = 60
private const val RADAR_MILLIS = 2_400
private const val RADAR_MIN = 0.4f
private const val RADAR_MAX = 1.4f
private const val RADAR_ALPHA = 0.9f
private const val REDUCED_ALPHA = 0.3f

@Preview(name = "매칭 대기", widthDp = 390, heightDp = 844)
@Composable
private fun MatchWaitingPreview() {
    RunkkTheme(reduceMotion = true) {
        MatchScreen(MatchUiState(phase = MatchPhase.WAITING, waitingSeconds = 7), {}, {}, {}, {})
    }
}
