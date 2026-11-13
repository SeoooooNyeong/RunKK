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
 * 배치고사 완료(FR-281, 피그마 61357:3270·축하 61361:3142~3974, 8절 9·10): 왼쪽 위 뒤로 + 가운데 「배치고사 완료」, 축하 히어로(흰 원 빛,
 * 기뻐하는 아바타, 티어 배지 크게, 축하 조각 10개), 「○○ 티어가 됐어요」, 「배치고사 3판」 표(판·거리·페이스·승패 알약, 머리 「n승 n패(n무)」,
 * 산정 안내 줄), 하단 「랜덤 대결 하러 가기」·「홈으로」. 하단 탭은 숨긴다.
 */
@Composable
fun PlacementCompleteScreen(
    state: PlacementCompleteUiState,
    actions: PlacementCompleteActions,
    modifier: Modifier = Modifier
) {
    val tier = state.tier
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (tier != null) AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.placement_complete_title),
                onBack = actions.onBack,
                isTitleCentered = true
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
            ResultBottomBar(
                primary = stringResource(R.string.placement_complete_random),
                onPrimary = actions.onRandom,
                secondary = stringResource(R.string.placement_home),
                onSecondary = actions.onHome
            )
        }
    }
}

/** 완료 본문: 축하 히어로, 「○○ 티어가 됐어요」 제목, 세 판 카드. */
@Composable
private fun CompleteBody(tier: String, state: PlacementCompleteUiState) {
    CelebrationHero(tier)
    Text(
        stringResource(R.string.placement_complete_headline, tierName(tier).orEmpty()),
        style = RunkkTextStyles.text(24, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    RoundsCard(state)
}

/**
 * 축하 히어로 350×212(피그마 61361 축하 5장): 흰 원 188(75%) 위 티어 배지 크게(엠블럼 112 + 이름), 왼쪽 내 전신 아바타 110×176, 축하 조각 10개.
 * 조각 위치·크기·각도는 피그마 7-5 그대로, 색은 라임·보라·금(podiumFirst #EBC66A). 그림은 장식이라 배지 낭독만 남긴다.
 */
@Composable
private fun CelebrationHero(tier: String) {
    val gold = LocalRunkkTierColors.current.podiumFirst
    Box(Modifier.size(width = HERO_WIDTH, height = HERO_HEIGHT)) {
        Box(
            Modifier
                .offset(x = 126.dp, y = 12.dp)
                .size(188.dp)
                .alpha(GLOW_ALPHA)
                .clip(CircleShape)
                // 라이트는 흰 빛, 다크는 표면색 빛(흰 빛 위 밝은 금색 티어 글자는 대비가 낮다, 8절 17). 둘 다 표면색이다.
                .background(RunkkColors.Surface)
        )
        CONFETTI.forEach { piece ->
            val color = when (piece.tone) {
                Confetti.Tone.LIME -> RunkkColors.LimeFill
                Confetti.Tone.VIOLET -> RunkkColors.Violet
                Confetti.Tone.GOLD -> gold
            }
            Box(
                Modifier
                    .offset(x = piece.x, y = piece.y)
                    .size(width = piece.width, height = piece.width * CONFETTI_RATIO)
                    .rotate(piece.degrees)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
        // 축하 아바타는 내 전신 정지 그림(S15P31A207-300). 칸이 비면 기쁜 기본 아바타다.
        Box(Modifier.offset(x = 30.dp, y = 30.dp).clearAndSetSemantics {}) {
            AvatarStillSlot(MY_STILL, Modifier.size(width = 110.dp, height = 176.dp)) {
                RunkkAvatar(null, AvatarExpression.JOY, Modifier.size(width = 110.dp, height = 176.dp))
            }
        }
        TierBadgeLarge(TierDisplay.Confirmed(tier), Modifier.offset(x = 152.dp, y = 50.dp).width(136.dp))
    }
}

/** 「배치고사 3판」 표(표면 r18 px16 py6, 그림자): 머리, 판 행, 산정 안내 줄. */
@Composable
private fun RoundsCard(state: PlacementCompleteUiState) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(RunkkElevation.Medium, shape, ambientColor = RunkkColors.Shadow, spotColor = RunkkColors.Shadow)
            .clip(shape)
            .background(RunkkColors.Surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp).semantics(mergeDescendants = true) {
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.placement_complete_rounds, state.rounds.size),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                state.recordLabel.asString(),
                style = RunkkTextStyles.text(12, FontWeight.Medium),
                color = RunkkColors.TextSecondary
            )
        }
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

/** 축하 조각 하나(피그마 7-5: 위치, 폭(높이 = 폭 × 1.6), 각도, 색). */
private data class Confetti(val x: Dp, val y: Dp, val width: Dp, val degrees: Float, val tone: Tone) {
    enum class Tone { LIME, VIOLET, GOLD }
}

private val CONFETTI = listOf(
    Confetti(60.dp, 15.3.dp, 8.dp, -20f, Confetti.Tone.LIME),
    Confetti(295.9.dp, 26.dp, 10.dp, 15f, Confetti.Tone.VIOLET),
    Confetti(326.dp, 93.dp, 6.dp, -30f, Confetti.Tone.GOLD),
    Confetti(22.dp, 115.dp, 7.dp, -45f, Confetti.Tone.VIOLET),
    Confetti(263.6.dp, 170.dp, 8.dp, 30f, Confetti.Tone.LIME),
    Confetti(96.dp, 189.dp, 6.dp, -10f, Confetti.Tone.GOLD),
    Confetti(248.dp, 2.8.dp, 6.dp, -60f, Confetti.Tone.GOLD),
    Confetti(14.7.dp, 60.dp, 6.dp, 20f, Confetti.Tone.LIME),
    Confetti(332.dp, 148.2.dp, 7.dp, -15f, Confetti.Tone.LIME),
    Confetti(150.dp, 2.6.dp, 6.dp, -35f, Confetti.Tone.VIOLET)
)

private const val CONFETTI_RATIO = 1.6f
private const val GLOW_ALPHA = 0.75f
private val HERO_WIDTH = 350.dp
private val HERO_HEIGHT = 212.dp
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
