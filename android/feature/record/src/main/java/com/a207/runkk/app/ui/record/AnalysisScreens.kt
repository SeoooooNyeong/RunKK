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
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.record.AggregationPeriod
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PERIODS = listOf(AggregationPeriod.WEEKLY to "주간", AggregationPeriod.MONTHLY to "월간")

/** 분석 화면 공통 틀: 상단 바 + 스크롤 본문 + 불러오는 중·오류. */
@Composable
private fun AnalysisScaffold(
    title: String,
    loading: Boolean,
    error: String?,
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
                error != null -> RunkkErrorState(error, onRetry = onRetry)
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
        title = "성장 분석",
        loading = state.loading && state.growth == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry,
        header = {
            RunkkSegmentedTabs(
                PERIODS.map {
                    it.second
                },
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
            RunkkEmptyState("아직 분석할 기록이 없어요", message = "몇 번 더 달리면 변화가 보여요")
            return@AnalysisScaffold
        }
        val max = growth.periods.maxOf { it.distance.meters }.coerceAtLeast(1)
        RunkkCard(Modifier.fillMaxWidth()) {
            Text("기간별 거리", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
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
                        "${RunkkFormat.km(p.distance)}km",
                        style = RunkkTextStyles.text(13, FontWeight.Bold),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.width(72.dp)
                    )
                }
            }
        }
        val latest = growth.periods.last()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkStatTile(
                "최근 횟수",
                "${latest.runs}",
                unit = "회",
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "평균 페이스",
                RunkkFormat.pace(latest.averagePace),
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "최장 거리",
                growth.longest?.let(RunkkFormat::km) ?: RunkkFormat.EMPTY,
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
        }
        if (growth.bests.isNotEmpty()) {
            RunkkCard(Modifier.fillMaxWidth()) {
                Text("거리별 최고 기록", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
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
        title = "리포트",
        loading = state.loading && state.report == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry
    ) {
        val report = state.report ?: return@AnalysisScaffold
        RunkkCard(Modifier.fillMaxWidth(), shape = RunkkShapes.ExtraLarge) {
            Text("이번 주 요약", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            Text(report.summary ?: "아직 요약할 기록이 없어요", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
        }
        RunkkCard(
            Modifier.fillMaxWidth(),
            borderColor = if (report.comment !=
                null
            ) {
                RunkkColors.Lime
            } else {
                RunkkColors.Border
            }
        ) {
            Text("코치 코멘트", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            Text(
                state.commentText,
                style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 24),
                color = if (report.comment !=
                    null
                ) {
                    RunkkColors.TextPrimary
                } else {
                    RunkkColors.TextSecondary
                }
            )
            if (report.commentSource == "RULE" || report.commentSource == "FALLBACK") {
                Text("규칙 기반 문구예요", style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary)
            }
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
        title = "랭킹",
        loading = state.loading && state.ranking == null,
        error = state.error,
        onBack = onBack,
        onRetry = onRetry,
        header = {
            RunkkSegmentedTabs(
                RankingScope.entries.map {
                    it.label
                },
                state.scope.ordinal,
                onSelect = {
                    onScope(RankingScope.entries[it])
                },
                modifier = Modifier.fillMaxWidth()
            )
            RunkkSegmentedTabs(
                PERIODS.map {
                    it.second
                },
                PERIODS.indexOfFirst {
                    it.first == state.period
                },
                onSelect = { onPeriod(PERIODS[it].first) },
                compact = true
            )
        }
    ) {
        val ranking = state.ranking ?: return@AnalysisScaffold
        ranking.mine?.let { mine ->
            RunkkCard(Modifier.fillMaxWidth(), borderColor = RunkkColors.Lime, background = RunkkColors.LimeContainer) {
                Text(
                    if (state.scope ==
                        RankingScope.CREW
                    ) {
                        "우리 크루"
                    } else {
                        "내 순위"
                    },
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.Lime
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    RunkkNumberText(
                        "${mine.rank}",
                        unit = "위",
                        style = RunkkTextStyles.display(40),
                        color = RunkkColors.Lime,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${RunkkFormat.km(mine.distance)}km",
                        style = RunkkTextStyles.display(24),
                        color = RunkkColors.TextPrimary
                    )
                }
                ranking.gapToNext?.let {
                    Text(
                        "한 단계 위까지 ${RunkkFormat.km(it)}km",
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                }
            }
        }
        RunkkSectionTitle("매일 0시 집계 · ${AGGREGATED.format(ranking.aggregatedAt.atZone(ZoneId.systemDefault()))}")
        ranking.notice?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.Lavender) }
        if (ranking.entries.isEmpty()) RunkkEmptyState("아직 순위가 없어요")
        ranking.entries.forEach { entry ->
            val isMine = entry == ranking.mine
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
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
                    "${RunkkFormat.km(entry.distance)}km",
                    style = RunkkTextStyles.display(18),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

// ---------- 목표·러닝 코치 ----------

@Composable
fun CoachRoute(viewModel: CoachViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CoachScreen(
        state,
        onBack = navigator::back,
        onTodayPlan = viewModel::onTodayPlan,
        onWeeklyGoal = viewModel::onWeeklyGoal,
        onMessageShown = viewModel::onMessageShown,
        onRetry = viewModel::refresh
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    state: CoachUiState,
    onBack: () -> Unit,
    onTodayPlan: (Distance) -> Unit,
    onWeeklyGoal: (Distance) -> Unit,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnalysisScaffold(
            title = "목표·러닝 코치",
            loading = state.loading && state.progress == null,
            error = state.error,
            onBack = onBack,
            onRetry = onRetry
        ) {
            RunkkCard(Modifier.fillMaxWidth()) {
                Text("이번 주 계획", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
                val days = state.progress?.days.orEmpty()
                if (days.isEmpty()) {
                    Text(
                        "아직 계획이 없어요. 오늘 계획부터 정해 보세요",
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                }
                days.forEach { day ->
                    val planned = day.planned
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) {
                        }
                    ) {
                        Row {
                            Text(
                                DAY.format(day.date),
                                style = RunkkTextStyles.text(
                                    13,
                                    if (day.date ==
                                        state.today
                                    ) {
                                        FontWeight.Black
                                    } else {
                                        FontWeight.Normal
                                    }
                                ),
                                color = RunkkColors.TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${RunkkFormat.km(day.done)}" + (planned?.let { " / ${RunkkFormat.km(it)}km" } ?: "km"),
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
                                "보충 거리 ${RunkkFormat.km(it)}km 추천",
                                style = RunkkTextStyles.Micro,
                                color = RunkkColors.Lavender
                            )
                        }
                    }
                }
            }
            RunkkCard(Modifier.fillMaxWidth()) {
                Text("오늘 계획 정하기", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLAN_CHOICES.forEach { km ->
                        RunkkPillButton("${km}km", onClick = {
                            onTodayPlan(Distance.ofKm(km.toDouble()))
                        }, enabled = !state.saving)
                    }
                }
            }
            RunkkCard(Modifier.fillMaxWidth()) {
                Text("이번 주 목표 거리", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GOAL_CHOICES.forEach { km ->
                        RunkkPillButton("${km}km", onClick = {
                            onWeeklyGoal(Distance.ofKm(km.toDouble()))
                        }, enabled = !state.saving)
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}

private const val PODIUM = 3
private val PLAN_CHOICES = listOf(3, 5, 7, 10)
private val GOAL_CHOICES = listOf(10, 20, 30, 40)
private val PERIOD_DATE = DateTimeFormatter.ofPattern("M/d", Locale.KOREAN)
private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val AGGREGATED = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)
