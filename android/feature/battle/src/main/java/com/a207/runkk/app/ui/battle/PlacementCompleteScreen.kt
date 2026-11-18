package com.a207.runkk.app.ui.battle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.LocalRunkkTierColors
import com.a207.runkk.app.core.designsystem.ResultBottomBar
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkElevation
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.TierBadgeLarge
import com.a207.runkk.app.core.designsystem.TierCelebration
import com.a207.runkk.app.core.designsystem.TierCelebrationCard
import com.a207.runkk.app.core.designsystem.TierCelebrationCardHeader
import com.a207.runkk.app.core.designsystem.TierCelebrationFrame
import com.a207.runkk.app.core.designsystem.TierCelebrationRunimalCard
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.splitPace
import com.a207.runkk.app.core.designsystem.tierName
import com.a207.runkk.app.core.model.TierDisplay
import com.a207.runkk.app.core.navigation.AvatarStillSlot
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.account.PlacementRound

/** 배치고사 완료(FR-281): 「랜덤 대결 하러 가기」는 홈 위에 랜덤 대결, 「홈으로」·뒤로는 홈이다(D-501, 8절 10). */
@Composable
fun PlacementCompleteRoute(viewModel: PlacementCompleteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            navigator.restart(Routes.HOME)
            if (event == PlacementCompleteEvent.RandomBattle) navigator.navigate(Routes.BATTLE_RANDOM)
        }
    }
    BackHandler(onBack = viewModel::onBack)
    PlacementCompleteScreen(
        state = state,
        actions = PlacementCompleteActions(
            onBack = viewModel::onBack,
            onRandom = viewModel::onRandomBattle,
            onHome = viewModel::onHome,
            onRetry = viewModel::onRetry
        )
    )
}

/**
 * 배치고사 완료(FR-281, 피그마 C-16 v2, 8절 9·10): core/ui 티어 축하 부품(TierCelebration*)으로 왼쪽 위 뒤로 + 가운데 「배치고사 완료」,
 * 축하 히어로와 「○○ 티어가 됐어요」, 「배치고사 3판」 표(판·거리·페이스·승패 알약, 머리 「n승 n패(n무)」, 산정 안내 줄), 확정된 Runimal이
 * 있으면 Runimal 카드, 하단 「랜덤 대결 하러 가기」·「홈으로」. 하단 탭은 숨긴다.
 */
@Composable
fun PlacementCompleteScreen(
    state: PlacementCompleteUiState,
    actions: PlacementCompleteActions,
    modifier: Modifier = Modifier
) {
    val tier = state.tier
    TierCelebrationFrame(
        title = stringResource(R.string.placement_complete_title),
        onBack = actions.onBack,
        hasBackdrop = tier != null,
        bottomBar = {
            ResultBottomBar(
                primary = stringResource(R.string.placement_complete_random),
                onPrimary = actions.onRandom,
                secondary = stringResource(R.string.placement_home),
                onSecondary = actions.onHome
            )
        },
        modifier = modifier
    ) {
        when {
            tier != null -> CompleteBody(tier, state)

            state.isLoading -> RunkkLoading(height = 240.dp)

            else -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.placement_load_failed),
                onRetry = actions.onRetry
            )
        }
    }
}

/** 완료 본문: 축하 히어로와 「○○ 티어가 됐어요」, 세 판 카드, 확정 Runimal이 있으면 Runimal 카드(C-16 v2). */
@Composable
private fun CompleteBody(tier: String, state: PlacementCompleteUiState) {
    TierCelebration(
        title = stringResource(R.string.placement_complete_headline, tierName(tier).orEmpty()),
        subtitle = null,
        tier = tier
    )
    RoundsCard(state)
    state.runimalType?.let { TierCelebrationRunimalCard(it, state.runimalDescription) }
}

/** 「배치고사 3판」 표(축하 요약 카드): 머리, 판 행, 산정 안내 줄. */
@Composable
private fun RoundsCard(state: PlacementCompleteUiState) {
    TierCelebrationCard {
        TierCelebrationCardHeader(
            stringResource(R.string.placement_complete_rounds, state.rounds.size),
            state.recordLabel.asString()
        )
        state.rounds.forEach { round ->
            HorizontalDivider(color = RunkkColors.Border)
            RoundRow(round)
        }
        HorizontalDivider(color = RunkkColors.Border)
        Text(
            stringResource(R.string.placement_complete_basis),
            style = RunkkTextStyles.text(12, FontWeight.Normal),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp).padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun RoundRow(round: PlacementRound) {
    val verdict = Verdict.of(round.verdict)
    val km = RunkkFormat.km(round.distance)
    val pace = round.averagePace?.let(::splitPace) ?: RunkkFormat.EMPTY
    val verdictLabel = verdict?.let { stringResource(shortVerdictRes(it)) }.orEmpty()
    val spoken = stringResource(
        R.string.placement_complete_row_desc,
        round.round,
        km,
        round.averagePace?.let { RunkkFormat.paceSpoken(it) } ?: RunkkFormat.EMPTY,
        verdict?.let { stringResource(verdictLabelRes(it)) }.orEmpty()
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clearAndSetSemantics {
            contentDescription = spoken
        },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.placement_complete_round, round.round),
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        ValueWithUnit(km, stringResource(R.string.placement_complete_km), DISTANCE_WIDTH)
        ValueWithUnit(pace, stringResource(R.string.placement_complete_pace_unit), PACE_WIDTH)
        val isWin = verdict == Verdict.WIN
        Box(
            Modifier
                .size(width = 32.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isWin) RunkkColors.LimeFill else RunkkColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                verdictLabel,
                style = RunkkTextStyles.text(12, FontWeight.Bold),
                color = if (isWin) RunkkColors.OnLimeFill else RunkkColors.TextSecondary
            )
        }
    }
}

@Composable
private fun ValueWithUnit(value: String, unit: String, width: Dp) {
    Row(Modifier.width(width), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.End)) {
        Text(
            value,
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            unit,
            style = RunkkTextStyles.text(11, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

private fun shortVerdictRes(verdict: Verdict): Int = when (verdict) {
    Verdict.WIN -> R.string.placement_complete_short_win
    Verdict.LOSE -> R.string.placement_complete_short_lose
    Verdict.DRAW -> R.string.placement_complete_short_draw
}

private val DISTANCE_WIDTH = 72.dp
private val PACE_WIDTH = 80.dp

@Preview(name = "P-3 배치고사 완료", widthDp = 390, heightDp = 844)
@Composable
private fun PlacementCompletePreview() {
    RunkkTheme(reduceMotion = true) {
        PlacementCompleteScreen(
            PlacementCompleteUiState(isLoading = false, tier = "GOLD", wins = 2, losses = 1),
            PlacementCompleteActions()
        )
    }
}
