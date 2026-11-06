package com.a207.runkk.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarCharacter
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalAvatarCharacter
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkAvatar
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkNumberText
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.SelectLabel
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PERIODS = listOf(
    AggregationPeriod.WEEKLY to R.string.record_period_weekly,
    AggregationPeriod.MONTHLY to R.string.record_period_monthly
)

/** 기간 탭 글자(주간·월간). */
@Composable
private fun periodLabels(): List<String> = PERIODS.map { stringResource(it.second) }

/** 분석 화면 공통 틀: 상단 바 + 스크롤 본문 + 불러오는 중·오류. */
@Composable
private fun AnalysisScaffold(
    title: String,
    isLoading: Boolean,
    error: UiText?,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier.weight(
                1f
            ).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            header()
            when {
                isLoading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) { RunkkLoading() }
                error != null -> RunkkErrorState(error.asString(), onRetry = onRetry)
                else -> content()
            }
        }
    }
}

// ---------- 성장 분석 ----------

@Composable
fun GrowthRoute(viewModel: GrowthViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    GrowthScreen(state, onBack = navigator::back, onPeriod = viewModel::onPeriodSelected, onRetry = viewModel::refresh)
}

@Composable
fun GrowthScreen(state: GrowthUiState, onBack: () -> Unit, onPeriod: (AggregationPeriod) -> Unit, onRetry: () -> Unit) {
    AnalysisScaffold(
        title = stringResource(R.string.record_growth_title),
        isLoading = state.isLoading && state.growth == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry,
        header = {
            RunkkSegmentedTabs(
                periodLabels(),
                PERIODS.indexOfFirst {
                    it.first == state.period
                },
                onSelect = { onPeriod(PERIODS[it].first) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        val growth = state.growth ?: return@AnalysisScaffold
        if (growth.periods.isEmpty()) {
            RunkkEmptyState(
                stringResource(R.string.record_growth_empty_title),
                message = stringResource(R.string.record_growth_empty_message)
            )
            return@AnalysisScaffold
        }
        GrowthPeriodCard(growth)
        GrowthStats(growth)
        if (growth.bests.isNotEmpty()) GrowthBestsCard(growth)
    }
}

/** 기간별 거리 막대 카드. */
@Composable
private fun GrowthPeriodCard(growth: Growth) {
    val max = growth.periods.maxOf { it.distance.meters }.coerceAtLeast(1)
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.record_growth_period_distance),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        growth.periods.forEach { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                }
            ) {
                Text(
                    PERIOD_DATE.format(p.start),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.width(56.dp)
                )
                RunkkProgressBar(p.distance.meters.toFloat() / max, height = 16.dp, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.record_km_value, RunkkFormat.km(p.distance)),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.width(72.dp)
                )
            }
        }
    }
}

/** 가장 최근 기간의 횟수·평균 페이스와 최장 거리 세 칸. */
@Composable
private fun GrowthStats(growth: Growth) {
    val latest = growth.periods.last()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.record_growth_stat_runs),
            StatTileValue("${latest.runs}", unit = stringResource(R.string.record_growth_unit_runs)),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.record_stat_avg_pace),
            StatTileValue(RunkkFormat.pace(latest.averagePace)),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            stringResource(R.string.record_growth_stat_longest),
            StatTileValue(growth.longest?.let(RunkkFormat::km) ?: RunkkFormat.EMPTY),
            style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 거리별 최고 기록 카드. */
@Composable
private fun GrowthBestsCard(growth: Growth) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.record_growth_bests_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        val resources = LocalContext.current.resources
        growth.bests.forEach { (distance, time) ->
            val label = bestDistanceLabel(resources, distance)
            val spoken = RunkkFormat.spokenLabel(resources, label)
            Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    label,
                    style = RunkkTextStyles.display(18),
                    color = RunkkColors.Lime,
                    modifier = Modifier.weight(1f).semantics { contentDescription = spoken }
                )
                Text(
                    RunkkFormat.duration(time),
                    style = RunkkTextStyles.display(18),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

// ---------- 리포트 ----------

@Composable
fun ReportRoute(viewModel: ReportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    ReportScreen(state, onBack = navigator::back, onRetry = viewModel::refresh)
}

@Composable
fun ReportScreen(state: ReportUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    AnalysisScaffold(
        title = stringResource(R.string.record_report_title),
        isLoading = state.isLoading && state.report == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry
    ) {
        val report = state.report ?: return@AnalysisScaffold
        RunkkCard(Modifier.fillMaxWidth(), shape = RunkkShapes.ExtraLarge) {
            Text(
                stringResource(R.string.record_report_summary_title),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(state.summaryText.asString(), style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
        }
        ReportCommentCard(state.commentText.asString(), report.comment != null, report.commentSource)
    }
}

/** 코치 코멘트 카드. 코멘트가 있으면 라임 테두리, 규칙 기반 문구면 안내를 덧붙인다. */
@Composable
private fun ReportCommentCard(comment: String, hasComment: Boolean, source: String?) {
    RunkkCard(
        Modifier.fillMaxWidth(),
        borderColor = if (hasComment) RunkkColors.Lime else RunkkColors.Border
    ) {
        Text(
            stringResource(R.string.record_report_comment_title),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Text(
            comment,
            style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 24),
            color = if (hasComment) RunkkColors.TextPrimary else RunkkColors.TextSecondary
        )
        if (source == "RULE" || source == "FALLBACK") {
            Text(
                stringResource(R.string.record_report_rule_based),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

// ---------- 랭킹 ----------

@Composable
fun RankingRoute(viewModel: RankingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RankingScreen(
        state,
        onScope = viewModel::onScopeSelected,
        onPeriod = viewModel::onPeriodSelected,
        onRetry = viewModel::refresh
    )
}

/**
 * 랭킹 탭(D-286, FR-070, 시상대 FR-290, 랭킹 v3 피그마 「랭킹 (v3 2026-11-05) 메인 탭, TOP3 시상대」): 상단 바 오른쪽 「개인 | 크루」
 * (크루가 닫혀 있으면 크루 칸은 자물쇠로 잠긴다), 주간·월간 칩과 집계 기간·마감까지 남은 시간, TOP3 시상대 카드, 4위부터 목록 카드,
 * 하단 탭 바로 위에 고정한 내 순위 카드(바로 위 순위까지 남은 거리). 이름 옆에 아주 작은 공개 티어 배지(J24-14R)를 둔다.
 * 순위 변동·집계 시각은 그리지 않는다(서버에 지난 주기 순위가 없다). 서버 응답에 아바타 키가 없어 모든 행이 기본 아바타다.
 */
@Composable
fun RankingScreen(
    state: RankingUiState,
    onScope: (RankingScope) -> Unit,
    onPeriod: (AggregationPeriod) -> Unit,
    onRetry: () -> Unit
) {
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    val error = state.error
    val ranking = state.ranking
    val mine = ranking?.mine?.takeIf { error == null }
    Box(Modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(
                title = stringResource(R.string.record_ranking_title),
                actions = { RankingScopeToggle(state.scope, isCrewOpen, onScope) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = if (mine !=
                            null
                        ) {
                            MINE_CARD_SPACE
                        } else {
                            32.dp
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val labels = periodLabels()
                RankingPeriodRow(
                    PERIODS.mapIndexed { index, (value, _) -> value to labels[index] },
                    state.period,
                    state.window,
                    onPeriod
                )
                when {
                    state.isLoading && ranking == null -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                        RunkkLoading()
                    }

                    error != null -> RunkkErrorState(error.asString(), onRetry = onRetry)

                    ranking != null -> RankingBody(state, ranking)
                }
            }
        }
        if (mine != null && ranking != null) {
            // 고정 카드 뒤로 목록이 이어짐을 알리는 아래 흐림
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(MINE_FADE_HEIGHT)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, RunkkColors.Background)))
            )
            RankingMineCard(
                mine,
                ranking.gapToNext,
                rankAbove(ranking.entries, mine),
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = RUNKK_BOTTOM_BAR_RISE + 8.dp)
            )
        }
    }
}

/** TOP3 시상대 카드와 4위부터 목록. 순위가 없으면 빈 안내만. */
@Composable
private fun RankingBody(state: RankingUiState, ranking: Ranking) {
    val podium = state.podium ?: return
    if (ranking.entries.isEmpty()) {
        RunkkEmptyState(stringResource(R.string.record_ranking_empty))
        return
    }
    RankingTop3Card(podium, ranking.mine, state.period)
    RankingListCard(podium.rest, ranking.mine)
}

/** 고정한 내 순위 카드에 가리지 않게 목록 아래에 비워 둘 높이와, 카드 뒤 아래 흐림 높이. */
private val MINE_CARD_SPACE = 120.dp
private val MINE_FADE_HEIGHT = 120.dp

// ---------- 목표·러닝 코치 ----------

/** 코치 화면 행동 묶음. 1초마다 바뀌지 않는 화면이지만 콜백이 많아 묶는다. */
class CoachActions(
    val onBack: () -> Unit = {},
    val onTodayPlan: (Distance) -> Unit = {},
    val onTodayPace: (Pace?) -> Unit = {},
    val onGoalRuns: (Int) -> Unit = {},
    val onGoalDistance: (Distance) -> Unit = {},
    val onSaveGoal: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onRetry: () -> Unit = {}
)

@Composable
fun CoachRoute(viewModel: CoachViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val actions = remember(viewModel, navigator) {
        CoachActions(
            onBack = navigator::back,
            onTodayPlan = viewModel::onTodayPlan,
            onTodayPace = viewModel::onTodayPace,
            onGoalRuns = viewModel::onGoalRunsSelected,
            onGoalDistance = viewModel::onGoalDistanceSelected,
            onSaveGoal = viewModel::onSaveWeeklyGoal,
            onMessageShown = viewModel::onMessageShown,
            onRetry = viewModel::refresh
        )
    }
    CoachScreen(state, actions)
}

@Composable
fun CoachScreen(state: CoachUiState, actions: CoachActions) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.asString()
    LaunchedEffect(state.message) {
        messageText?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnalysisScaffold(
            title = stringResource(R.string.record_coach_title),
            isLoading = state.isLoading && state.progress == null,
            error = state.error,
            onBack = actions.onBack,
            onRetry = actions.onRetry
        ) {
            CoachWeekCard(state)
            TodayPlanCard(
                planned = state.progress?.days?.firstOrNull { it.date == state.today }?.planned,
                pace = state.todayPace,
                onTodayPlan = actions.onTodayPlan,
                onTodayPace = actions.onTodayPace
            )
            WeeklyGoalCard(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

/** 이번 주 계획 카드: 요일마다 계획 대비 달린 거리와 보충 추천. */
@Composable
private fun CoachWeekCard(state: CoachUiState) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.record_coach_week_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        val days = state.progress?.days.orEmpty()
        if (days.isEmpty()) {
            Text(
                stringResource(R.string.record_coach_week_empty),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        days.forEach { day -> CoachDayRow(day, isToday = day.date == state.today) }
    }
}

/** 요일 한 줄: 날짜, 달린 거리/계획 거리, 진행 막대, 보충 거리 추천. */
@Composable
private fun CoachDayRow(day: PlanProgress.Day, isToday: Boolean) {
    val dayFormat = rememberDateFormatter(R.string.record_day_pattern)
    val planned = day.planned
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {
        }
    ) {
        Row {
            Text(
                dayFormat.format(day.date),
                style = RunkkTextStyles.text(13, if (isToday) FontWeight.Black else FontWeight.Normal),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (planned != null) {
                    stringResource(R.string.record_coach_day_planned, RunkkFormat.km(day.done), RunkkFormat.km(planned))
                } else {
                    stringResource(R.string.record_coach_day_unplanned, RunkkFormat.km(day.done))
                },
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        if (planned != null &&
            planned.meters > 0
        ) {
            RunkkProgressBar(day.done.meters.toFloat() / planned.meters, height = 4.dp)
        }
        day.supplement?.takeIf { it.meters > 0 }?.let {
            Text(
                stringResource(R.string.record_coach_supplement, RunkkFormat.km(it)),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.Lavender
            )
        }
    }
}

/**
 * 오늘 계획 정하기 카드: 거리와 목표 페이스 고르기 줄. 오늘 정해 둔 거리([planned])·페이스([pace])가 선택돼 보인다.
 * 목표 페이스는 코치 계획 따르기가 켜져 있을 때 1km마다 느리면 알려 준다(FR-044, 사용자 요청 2026-10-31).
 */
@Composable
private fun TodayPlanCard(
    planned: Distance?,
    pace: Pace?,
    onTodayPlan: (Distance) -> Unit,
    onTodayPace: (Pace?) -> Unit
) {
    RunkkCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.record_coach_today_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        val distances = PLAN_CHOICES.map { Distance.ofKm(it.toDouble()) }
        RunkkSegmentRow(
            options = PLAN_CHOICES.map { stringResource(R.string.record_km_int_value, it) },
            selectedIndex = distances.indexOf(planned),
            onSelect = { onTodayPlan(distances[it]) },
            groupLabel = stringResource(R.string.record_coach_today_distance),
            spokenLabels = PLAN_CHOICES.map { stringResource(R.string.record_goal_distance_spoken, it) }
        )
        // 드롭다운(사용자 요청 2026-10-31: 선택지가 적다). 맨 앞은 페이스 없음(거리만 따른다)
        val none = stringResource(R.string.record_coach_pace_none)
        val resources = LocalContext.current.resources
        RunkkSelectField(
            label = stringResource(R.string.record_coach_today_pace),
            options = listOf<Pace?>(null) + PACE_CHOICES.map(::Pace),
            selected = pace,
            // 화면은 "5:30", 보조 기술은 "1킬로미터당 5분 30초"로 읽는다(D-364, 리뷰 AN11-7: 페이스 표기는 RunkkFormat 하나로)
            optionLabel = { p ->
                p?.let { SelectLabel(RunkkFormat.pace(it), RunkkFormat.paceSpoken(resources, it)) } ?: SelectLabel(none)
            },
            onSelect = onTodayPace,
            placeholder = none
        )
    }
}

/** 주간 목표(FR-072): 횟수(1~7)와 거리를 모두 고른 뒤 저장한다. */
@Composable
private fun WeeklyGoalCard(state: CoachUiState, actions: CoachActions) {
    RunkkCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            stringResource(R.string.record_coach_goal_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        val runs = CoachUiState.GOAL_RUNS.toList()
        RunkkSegmentRow(
            options = runs.map { stringResource(R.string.record_goal_runs_option, it) },
            selectedIndex = runs.indexOf(state.goalRuns),
            onSelect = { actions.onGoalRuns(runs[it]) },
            groupLabel = stringResource(R.string.record_goal_runs_group),
            spokenLabels = runs.map { stringResource(R.string.record_goal_runs_spoken, it) }
        )
        val distances = GOAL_CHOICES.map { Distance.ofKm(it.toDouble()) }
        RunkkSegmentRow(
            options = GOAL_CHOICES.map { stringResource(R.string.record_km_int_value, it) },
            selectedIndex = distances.indexOf(state.goalDistance),
            onSelect = { actions.onGoalDistance(distances[it]) },
            groupLabel = stringResource(R.string.record_goal_distance_group),
            spokenLabels = GOAL_CHOICES.map { stringResource(R.string.record_goal_distance_spoken, it) }
        )
        RunkkPrimaryButton(
            stringResource(R.string.record_goal_save),
            onClick = actions.onSaveGoal,
            isEnabled = state.canSaveGoal,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val PLAN_CHOICES = listOf(3, 5, 7, 10)

/** 오늘 계획 목표 페이스 선택지(초/km): 3:00부터 10:00까지 10초 간격. */
private val PACE_CHOICES = (180..600 step 10).toList()
private val GOAL_CHOICES = listOf(10, 20, 30, 40)
private val PERIOD_DATE = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
