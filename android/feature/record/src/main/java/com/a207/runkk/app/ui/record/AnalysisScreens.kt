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
import com.a207.runkk.app.core.designsystem.AvatarExpression
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
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
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
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
 * 랭킹 탭(D-286, FR-070): 분절 "개인 | 크루"(크루는 CREW가 닫혀 있으면 분절째 숨겨 개인만), 기간 칩 "주간"·"월간", 기준
 * "누적 유효 거리", 내 순위 카드, 행(순위·아바타·닉네임·거리). 다음 순위까지 남은 거리·집계 시각·티어는 그리지 않는다.
 * 서버 응답에 아바타 키가 없어 모든 행이 기본 아바타다.
 */
@Composable
fun RankingScreen(
    state: RankingUiState,
    onScope: (RankingScope) -> Unit,
    onPeriod: (AggregationPeriod) -> Unit,
    onRetry: () -> Unit
) {
    val isCrewOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW)
    AnalysisScaffold(
        title = stringResource(R.string.record_ranking_title),
        isLoading = state.isLoading && state.ranking == null,
        error = state.error,
        onBack = null,
        onRetry = onRetry,
        header = { RankingTabs(state.scope, state.period, isCrewOpen, onScope, onPeriod) }
    ) {
        val ranking = state.ranking ?: return@AnalysisScaffold
        ranking.mine?.let { RankingMineCard(it) }
        if (ranking.entries.isEmpty()) RunkkEmptyState(stringResource(R.string.record_ranking_empty))
        ranking.entries.forEach { entry -> RankingEntryRow(entry, isMine = entry == ranking.mine) }
    }
}

/** 랭킹 위쪽: 개인·크루 분절(크루가 열려 있을 때만), 주간·월간 칩과 기준 "누적 유효 거리". */
@Composable
private fun RankingTabs(
    scope: RankingScope,
    period: AggregationPeriod,
    isCrewOpen: Boolean,
    onScope: (RankingScope) -> Unit,
    onPeriod: (AggregationPeriod) -> Unit
) {
    if (isCrewOpen) {
        RunkkSegmentedTabs(
            RankingScope.entries.map { stringResource(it.labelRes) },
            scope.ordinal,
            onSelect = { onScope(RankingScope.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val labels = periodLabels()
        PERIODS.forEachIndexed { index, (value, _) ->
            RunkkChoiceChip(labels[index], isSelected = value == period, onSelectedChange = { onPeriod(value) })
        }
        Text(
            stringResource(R.string.record_ranking_metric),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 내 순위 고정 카드: "[128] (아바타) 나 [18.4]km". 기간을 바꾸면 바뀐 순위를 보조 기술에 알린다. */
@Composable
private fun RankingMineCard(mine: Ranking.Entry) {
    val me = stringResource(R.string.record_ranking_me)
    RunkkCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        borderColor = RunkkColors.BorderStrong,
        shape = RunkkShapes.ExtraLarge
    ) {
        RankingLine(mine.copy(name = me), isMine = true)
    }
}

/** 랭킹 한 줄: 순위, 기본 아바타, 이름, 거리. 보조 기술에는 한 줄로 읽어 준다. */
@Composable
private fun RankingEntryRow(entry: Ranking.Entry, isMine: Boolean) {
    Column {
        RankingLine(entry, isMine, Modifier.padding(vertical = 8.dp))
        HorizontalDivider(color = RunkkColors.Border)
    }
}

@Composable
private fun RankingLine(entry: Ranking.Entry, isMine: Boolean, modifier: Modifier = Modifier) {
    val spoken = rankingSpoken(entry, isMine)
    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "${entry.rank}",
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp)
        )
        RunkkAvatar(
            faceKey = null,
            expression = AvatarExpression.FACE,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.Surface)
        )
        Text(
            entry.name,
            style = RunkkTextStyles.text(15, if (isMine) FontWeight.Bold else FontWeight.Normal),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.record_ranking_km, RunkkFormat.kmShort(entry.distance)),
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 랭킹 한 줄 읽기: "2위 달리는곰, 41.20킬로미터, 나". */
@Composable
internal fun rankingSpoken(entry: Ranking.Entry, isMine: Boolean): String = listOfNotNull(
    stringResource(R.string.record_ranking_spoken_entry, entry.rank, entry.name),
    stringResource(R.string.record_distance_spoken, RunkkFormat.km(entry.distance)),
    if (isMine) stringResource(R.string.record_ranking_spoken_me) else null
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
            isLoading = state.isLoading && state.progress == null,
            error = state.error,
            onBack = actions.onBack,
            onRetry = actions.onRetry
        ) {
            CoachWeekCard(state)
            TodayPlanCard(
                planned = state.progress?.days?.firstOrNull { it.date == state.today }?.planned,
                onTodayPlan = actions.onTodayPlan
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

/** 오늘 계획 정하기 카드: 주간 목표 거리와 같은 고르기 줄. 오늘 정해 둔 거리([planned])가 선택돼 보인다. */
@Composable
private fun TodayPlanCard(planned: Distance?, onTodayPlan: (Distance) -> Unit) {
    RunkkCard(Modifier.fillMaxWidth()) {
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
            spokenLabels = PLAN_CHOICES.map { stringResource(R.string.record_goal_distance_spoken, it) }
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
private val GOAL_CHOICES = listOf(10, 20, 30, 40)
private val PERIOD_DATE = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
