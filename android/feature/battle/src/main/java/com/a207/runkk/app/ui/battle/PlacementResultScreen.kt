package com.a207.runkk.app.ui.battle

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AchievementCard
import com.a207.runkk.app.core.designsystem.AchievementIcon
import com.a207.runkk.app.core.designsystem.AchievementRow
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.HeartZoneSection
import com.a207.runkk.app.core.designsystem.ResultBottomBar
import com.a207.runkk.app.core.designsystem.ResultDistanceHero
import com.a207.runkk.app.core.designsystem.RunMetricCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.SplitPaceSection
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.record.RecordDetail
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 배치고사 판 결과(FR-280, D-501): 3판째를 세면 완료로 바꿔 열고, 「다음 판 뛰기」는 홈 위에 배치고사 진행 하나만 남긴다(뒤 스택 정리).
 * 「홈으로」·왼쪽 위 뒤로·시스템 뒤로는 홈이다.
 */
@Composable
fun PlacementResultRoute(battleId: Long, viewModel: PlacementResultViewModel = hiltViewModel()) {
    LaunchedEffect(battleId) { viewModel.load(battleId) }
    // 화면에 돌아오면 30초 뒤에도 정리 중이었거나 진행을 못 읽은 판을 다시 읽는다(PL2-A2).
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlacementResultEvent.Replace -> navigator.replace(event.key)

                PlacementResultEvent.NextRound -> {
                    navigator.restart(Routes.HOME)
                    navigator.navigate(Routes.PLACEMENT)
                }

                PlacementResultEvent.Home -> navigator.restart(Routes.HOME)
            }
        }
    }
    BackHandler(onBack = viewModel::onBack)
    PlacementResultScreen(
        state = state,
        actions = PlacementResultActions(
            onBack = viewModel::onBack,
            onNext = viewModel::onNextRound,
            onHome = viewModel::onHome,
            onRetry = { viewModel.load(battleId, isForced = true) }
        )
    )
}

/**
 * 판 결과(FR-280, 피그마 61357:3020): 왼쪽 위 뒤로 + 가운데 「배치고사 결과」, 러닝 결과와 같은 부품(종류 칩 없음: 거리 히어로, 지표 카드 안
 * 구간 페이스·심박 구간, 성과 카드 첫 줄 「승리」·「패배」·「무승부」와 리워드), 진행 카드(판 뒤), 세지 않은 판·정리 중 안내, 하단 「다음 판 뛰기」·
 * 「홈으로」. 기록 상세가 아직 없으면 히어로·지표 카드 없이 성과와 진행만 보인다. 하단 탭은 숨긴다.
 */
@Composable
fun PlacementResultScreen(
    state: PlacementResultUiState,
    actions: PlacementResultActions,
    modifier: Modifier = Modifier
) {
    val result = state.result
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        if (result != null) AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.placement_result_title),
                onBack = actions.onBack,
                isTitleCentered = true
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    result != null -> ResultBody(state, result, actions.onRetry)

                    state.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }

                    else -> RunkkErrorState(
                        state.error?.asString() ?: stringResource(R.string.placement_load_failed),
                        onRetry = actions.onRetry
                    )
                }
            }
            ResultBottomBar(
                primary = stringResource(R.string.placement_result_next),
                onPrimary = actions.onNext,
                secondary = stringResource(R.string.placement_home),
                onSecondary = actions.onHome
            )
        }
    }
}

@Composable
private fun ResultBody(state: PlacementResultUiState, result: BattleResult, onRetry: () -> Unit) {
    state.detail?.let { detail ->
        val summary = detail.summary
        ResultDistanceHero(
            distance = summary.distance,
            dateLine = dateLine(summary.startedAt, stringResource(R.string.placement_result_date_pattern)),
            kindLabel = null
        )
        RunMetricCard(
            elapsed = summary.elapsed,
            paused = detail.paused,
            averagePace = summary.averagePace ?: averagePace(detail),
            averageHeartRate = detail.averageHeartRate
        ) {
            SplitPaceSection(detail.splitPaces)
            HeartZoneSection(
                detail.heartRateZones,
                source = detail.averageHeartRate?.let { HEART_SOURCE_WATCH },
                hasBirthYear = detail.averageHeartRate == null || detail.heartRateZones.isNotEmpty()
            )
        }
    }
    AchievementCard(achievementsOf(result, state.verdict))
    val progress = state.progress
    // 30초 뒤에도 정리 중이면 진행 카드 자리에 안내와 「다시 확인」만 둔다(D-501, PL2-A2). 진행을 끝내 못 읽으면 오류와 「다시 시도」.
    when {
        state.isStillPending -> Unit

        state.isPending -> Checking()

        state.progressError != null -> Notice(
            state.progressError,
            retryLabel = R.string.placement_retry,
            onRetry = onRetry
        )

        progress != null -> PlacementProgressAfterRound(progress)
    }
    state.notice?.let {
        Notice(it, retryLabel = R.string.placement_result_recheck.takeIf { state.isStillPending }, onRetry = onRetry)
    }
}

/** 성과 줄: 첫 줄 승패(이긴 시간 차, 있으면), 리워드 합계. 판정 전이면 승패 줄을 넣지 않는다(빈 행을 그리지 않는다, 8절 9). */
private fun achievementsOf(result: BattleResult, verdict: Verdict?): List<AchievementRow> = listOfNotNull(
    verdict?.let { AchievementRow(AchievementIcon.STAR, UiText.Res(verdictLabelRes(it)), reachGap(result).orEmpty()) },
    (result.totalRewardPoints ?: result.rewardPoints)?.takeIf { it > 0 }?.let {
        AchievementRow(AchievementIcon.REWARD, UiText.Res(R.string.placement_result_reward), "+$it")
    }
)

/** 도착 시간 차(상대 − 나, 「+0:42」·「-0:12」). 둘 다 있을 때만. */
private fun reachGap(result: BattleResult): String? {
    val mine = result.myReach ?: return null
    val theirs = result.opponentReach ?: return null
    val gap = theirs.minus(mine)
    return (if (gap.isNegative) "-" else "+") + RunkkFormat.duration(gap.abs())
}

@Composable
private fun Checking() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = RunkkColors.Violet, strokeWidth = 2.dp)
        Text(
            stringResource(R.string.placement_result_checking),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary
        )
    }
}

/**
 * 진행 카드 아래 안내(세지 않은 판, 정리 중, 진행 읽기 실패). 나타나면 읽어 준다. [retryLabel]이 있으면 아래에 다시 읽는 글자 단추를 둔다
 * (정리 중 「다시 확인」, 진행 읽기 실패 「다시 시도」, PL2-A2).
 */
@Composable
private fun Notice(text: UiText, @StringRes retryLabel: Int? = null, onRetry: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RunkkColors.Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text.asString(),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
        )
        retryLabel?.let {
            RunkkPillButton(stringResource(it), onClick = onRetry, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun averagePace(detail: RecordDetail): Pace? {
    val summary = detail.summary
    return if (summary.distance.meters > 0 &&
        !summary.elapsed.isZero
    ) {
        Pace.of(summary.elapsed, summary.distance)
    } else {
        null
    }
}

private fun dateLine(at: Instant, pattern: String): String =
    DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(at.atZone(ZoneId.systemDefault()))

/** 심박 출처(서버는 워치 심박만 둔다, 러닝 결과와 같다). */
private const val HEART_SOURCE_WATCH = "WATCH"

@Preview(name = "P-2 판 결과", widthDp = 390, heightDp = 844)
@Composable
private fun PlacementResultPreview() {
    RunkkTheme(reduceMotion = true) {
        PlacementResultScreen(
            PlacementResultUiState(
                isLoading = false,
                result = BattleResult(
                    battleId = 1,
                    isPending = false,
                    verdict = "WIN",
                    rows = emptyList(),
                    rewardPoints = 52,
                    isNewRecord = false,
                    myReach = Duration.ofSeconds(1_668),
                    opponentReach = Duration.ofSeconds(1_710)
                )
            ),
            PlacementResultActions()
        )
    }
}
