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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
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
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
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
    loading: Boolean,
    error: UiText?,
    onBack: () -> Unit,
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
                loading -> Box(Modifier.fillMaxWidth().padding(vertical = 48.dp)) { RunkkLoading() }
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
        loading = state.loading && state.growth == null,
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
        growth.bests.forEach { (distance, time) ->
            Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    distance,
                    style = RunkkTextStyles.display(18),
                    color = RunkkColors.Lime,
                    modifier = Modifier.weight(1f)
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
        loading = state.loading && state.report == null,
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
    val navigator = LocalRunkkNavigator.current
    RankingScreen(
        state,
        onBack = navigator::back,
        onScope = viewModel::onScopeSelected,
        onPeriod = viewModel::onPeriodSelected,
        onRetry = viewModel::refresh
    )
}

@Composable
fun RankingScreen(
    state: RankingUiState,
    onBack: () -> Unit,
    onScope: (RankingScope) -> Unit,
    onPeriod: (AggregationPeriod) -> Unit,
    onRetry: () -> Unit
) {
    AnalysisScaffold(
        title = stringResource(R.string.record_ranking_title),
        loading = state.loading && state.ranking == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry,
        header = { RankingTabs(state.scope, state.period, onScope, onPeriod) }
    ) {
        val ranking = state.ranking ?: return@AnalysisScaffold
        ranking.mine?.let { RankingMineCard(state.scope, ranking, it) }
        val aggregatedFormat = rememberDateFormatter(R.string.record_ranking_aggregated_pattern)
        RunkkSectionTitle(
            ranking.aggregatedAt?.let {
                stringResource(
                    R.string.record_ranking_aggregated_at,
                    aggregatedFormat.format(it.atZone(ZoneId.systemDefault()))
                )
            } ?: stringResource(R.string.record_ranking_aggregating)
        )
        ranking.notice?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.Lavender) }
        if (ranking.entries.isEmpty()) {
            RunkkEmptyState(
                stringResource(
                    if (ranking.aggregatedAt == null) R.string.record_ranking_waiting else R.string.record_ranking_empty
                )
            )
        }
        ranking.entries.forEach { entry -> RankingEntryRow(entry, isMine = entry == ranking.mine) }
    }
}

/** 랭킹 위쪽 탭 두 줄: 개인·크루, 주간·월간. */
@Composable
private fun RankingTabs(
    scope: RankingScope,
    period: AggregationPeriod,
    onScope: (RankingScope) -> Unit,
    onPeriod: (AggregationPeriod) -> Unit
) {
    RunkkSegmentedTabs(
        RankingScope.entries.map { stringResource(it.labelRes) },
        scope.ordinal,
        onSelect = {
            onScope(RankingScope.entries[it])
        },
        modifier = Modifier.fillMaxWidth()
    )
    RunkkSegmentedTabs(
        periodLabels(),
        PERIODS.indexOfFirst {
            it.first == period
        },
        onSelect = { onPeriod(PERIODS[it].first) },
        compact = true
    )
}

/** 내 순위(또는 우리 크루 순위) 카드. 범위·기간을 바꾸면 순위가 바뀐 것을 보조 기술에 알린다. */
@Composable
private fun RankingMineCard(scope: RankingScope, ranking: Ranking, mine: Ranking.Entry) {
    RunkkCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        borderColor = RunkkColors.Lime,
        background = RunkkColors.LimeContainer
    ) {
        Text(
            stringResource(
                if (scope == RankingScope.CREW) R.string.record_ranking_mine_crew else R.string.record_ranking_mine
            ),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.Lime
        )
        Row(verticalAlignment = Alignment.Bottom) {
            RunkkNumberText(
                "${mine.rank}",
                unit = stringResource(R.string.record_ranking_unit_rank),
                style = RunkkTextStyles.display(40),
                color = RunkkColors.Lime,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.record_km_value, RunkkFormat.km(mine.distance)),
                style = RunkkTextStyles.display(24),
                color = RunkkColors.TextPrimary
            )
        }
        ranking.gapToNext?.let {
            Text(
                stringResource(R.string.record_ranking_gap, RunkkFormat.km(it)),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 랭킹 한 줄: 순위, 이름, 거리. 보조 기술에는 한 줄로 읽어 준다. */
@Composable
private fun RankingEntryRow(entry: Ranking.Entry, isMine: Boolean) {
    val spoken = rankingSpoken(entry, isMine)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "${entry.rank}",
            style = RunkkTextStyles.display(20),
            color = if (entry.rank <=
                PODIUM
            ) {
                RunkkColors.Lime
            } else {
                RunkkColors.TextSecondary
            },
            modifier = Modifier.width(32.dp)
        )
        Text(
            entry.name,
            style = RunkkTextStyles.text(15, if (isMine) FontWeight.Black else FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.record_km_value, RunkkFormat.km(entry.distance)),
            style = RunkkTextStyles.display(18),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 랭킹 한 줄 읽기: "2위 달리는곰, 41.20킬로미터, 나". */
@Composable
internal fun rankingSpoken(entry: Ranking.Entry, mine: Boolean): String = listOfNotNull(
    stringResource(R.string.record_ranking_spoken_entry, entry.rank, entry.name),
    stringResource(R.string.record_distance_spoken, RunkkFormat.km(entry.distance)),
    if (mine) stringResource(R.string.record_ranking_spoken_me) else null
).joinToString(", ")

// ---------- 목표·러닝 코치 ----------

/** 코치 화면 행동 묶음. 1초마다 바뀌지 않는 화면이지만 콜백이 많아 묶는다. */
class CoachActions(
    val onBack: () -> Unit = {},
    val onTodayPlan: (Distance) -> Unit = {},
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
            loading = state.loading && state.progress == null,
            error = state.error,
            onBack = actions.onBack,
            onRetry = actions.onRetry
        ) {
            CoachWeekCard(state)
            TodayPlanCard(saving = state.saving, onTodayPlan = actions.onTodayPlan)
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

/** 오늘 계획 정하기 카드: 거리 알약 버튼. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayPlanCard(saving: Boolean, onTodayPlan: (Distance) -> Unit) {
    RunkkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.record_coach_today_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PLAN_CHOICES.forEach { km ->
                RunkkPillButton(stringResource(R.string.record_km_int_value, km), onClick = {
                    onTodayPlan(Distance.ofKm(km.toDouble()))
                }, enabled = !saving)
            }
        }
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
            enabled = state.canSaveGoal,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private const val PODIUM = 3
private val PLAN_CHOICES = listOf(3, 5, 7, 10)
private val GOAL_CHOICES = listOf(10, 20, 30, 40)
private val PERIOD_DATE = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
