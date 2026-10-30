package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RouteThumbnail
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.record.RecordBattleType
import com.a207.runkk.app.domain.record.RecordVerdict
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.record.ShapePoint
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 기록 화면(G-7 달력, G-8 목록, G-10 대결). [initialView]는 다른 화면이 고른 첫 탭(Routes.RECORDS_BATTLES면 대결). */
@Composable
fun RecordListRoute(initialView: RecordView = RecordView.CALENDAR, viewModel: RecordListViewModel = hiltViewModel()) {
    LaunchedEffect(initialView) { if (initialView != RecordView.CALENDAR) viewModel.openView(initialView) }
    // 상세에서 기록을 지우고 돌아오면 다시 읽는다(처음 표시는 init이 읽는다, D-280).
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    val actions = remember(viewModel, navigator) {
        RecordListActions(
            onBack = navigator::back,
            onViewSelected = viewModel::onViewSelected,
            onPreviousMonth = viewModel::onPreviousMonth,
            onNextMonth = viewModel::onNextMonth,
            onDateSelected = viewModel::onDateSelected,
            onRun = { navigator.navigate(Routes.RecordDetail(it)) },
            onRetry = viewModel::refresh,
            onRetryRecent = viewModel::loadRecent
        )
    }
    RecordListScreen(state = state, actions = actions)
}

@Composable
fun RecordListScreen(state: RecordListUiState, actions: RecordListActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.record_list_title), onBack = actions.onBack, isTitleCentered = true)
        RunkkSegmentedTabs(
            VIEW_LABELS.map { stringResource(it) },
            selectedIndex = state.view.ordinal,
            onSelect = { actions.onViewSelected(RecordView.entries[it]) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        when (state.view) {
            RecordView.CALENDAR -> CalendarPane(state, actions, Modifier.weight(1f))

            RecordView.LIST -> ListPane(state, actions, Modifier.weight(1f))

            RecordView.BATTLE -> Box(Modifier.weight(1f).fillMaxWidth()) {
                LocalSlots.current[SlotKey.RECORD_BATTLES].orEmpty().forEach { it() }
            }
        }
    }
}

private val VIEW_LABELS =
    listOf(R.string.record_list_tab_calendar, R.string.record_list_tab_list, R.string.record_list_tab_battle)

// ---------- 달력 탭(G-7) ----------

@Composable
private fun CalendarPane(state: RecordListUiState, actions: RecordListActions, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "month") { MonthHeader(state.month, actions.onPreviousMonth, actions.onNextMonth) }
        item(key = "summary") { MonthSummaryRow(state.summary) }
        item(key = "calendar") {
            Calendar(state.month, state.dailyDistance, state.selectedDate, actions.onDateSelected)
        }
        when {
            state.isLoading && state.runs.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(120.dp)) { RunkkLoading() }
            }

            state.error != null -> item(key = "error") {
                RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
            }

            state.runs.isEmpty() -> item(key = "empty") {
                RunkkEmptyState(
                    stringResource(R.string.record_list_empty_title),
                    message = stringResource(R.string.record_list_empty_message)
                )
            }

            else -> items(state.dayRuns, key = { "day-" + it.recordId }) { run ->
                DayRunRow(run, state.zone, onClick = { actions.onRun(run.recordId) })
            }
        }
        if (state.pendingCount > 0) item(key = "pending") { PendingCard(state.pendingCount) }
    }
}

/** 월 이동 줄: 이전 달 버튼, 가운데 연월, 다음 달 버튼. */
@Composable
private fun MonthHeader(month: YearMonth, onPreviousMonth: () -> Unit, onNextMonth: () -> Unit) {
    val monthFormat = rememberDateFormatter(R.string.record_month_pattern)
    Row(verticalAlignment = Alignment.CenterVertically) {
        RunkkIconButton(
            RunkkIcons.Back,
            contentDescription = stringResource(R.string.record_list_previous_month),
            onClick = onPreviousMonth,
            borderColor = RunkkColors.Background
        )
        Text(
            monthFormat.format(month),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkIconButton(
            RunkkIcons.ChevronRight,
            contentDescription = stringResource(R.string.record_list_next_month),
            onClick = onNextMonth,
            borderColor = RunkkColors.Lime
        )
    }
}

/** 월 요약 세 칸: 횟수 n번, 거리 n km, 연속 n일째(서버 값을 그대로 쓴다, D-279). 모르면 "-". */
@Composable
private fun MonthSummaryRow(summary: RecordListUiState.MonthSummary?) {
    val unknown = stringResource(R.string.record_summary_unknown)
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryCell(
            R.string.record_summary_runs_label,
            summary?.let { stringResource(R.string.record_summary_runs_value, it.runs) } ?: unknown
        )
        SummaryCell(
            R.string.record_summary_distance_label,
            summary?.let { stringResource(R.string.record_summary_distance_value, RunkkFormat.kmShort(it.distance)) }
                ?: unknown
        )
        SummaryCell(
            R.string.record_summary_streak_label,
            summary?.streakDays?.let { stringResource(R.string.record_summary_streak_value, it) } ?: unknown
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryCell(@StringRes label: Int, value: String) {
    Column(
        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(label), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Text(value, style = RunkkTextStyles.text(20, FontWeight.Black), color = RunkkColors.TextPrimary)
    }
}

/** 달력: 일요일 시작 7열. 유효 기록이 있는 날은 칸에 날짜와 km를 보인다. 어느 날이든 누르면 그날 기록 줄을 보인다. */
@Composable
private fun Calendar(
    month: YearMonth,
    daily: Map<LocalDate, Distance>,
    selected: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val rows = remember(month) {
        val lead = month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
        val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(DAYS_IN_WEEK).map { it + List(DAYS_IN_WEEK - it.size) { null } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAYS.forEach {
                Text(
                    stringResource(it),
                    style = RunkkTextStyles.Micro,
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        rows.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    DayCell(day, day?.let(daily::get), day == selected, onDateSelected, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate?,
    distance: Distance?,
    isSelected: Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier
) {
    val km = distance?.takeIf { it.meters > 0 }?.let(RunkkFormat::kmShort)
    val shape = RoundedCornerShape(12.dp)
    val spoken = when {
        day == null -> null
        km != null -> stringResource(R.string.record_calendar_day_distance_spoken, day.dayOfMonth, km)
        else -> stringResource(R.string.record_calendar_day_spoken, day.dayOfMonth)
    }
    val selectedLabel = stringResource(R.string.record_calendar_day_selected)
    var cell = modifier.height(48.dp).clip(shape)
    if (day != null) {
        cell = cell
            .background(if (isSelected) RunkkColors.LimeContainer else RunkkColors.Background)
            .then(if (km != null || isSelected) Modifier.border(1.dp, RunkkColors.Border, shape) else Modifier)
            .clickable(role = Role.Button) { onDateSelected(day) }
            .semantics(mergeDescendants = true) {
                if (spoken != null) contentDescription = spoken
                selected = isSelected
                if (isSelected) stateDescription = selectedLabel
            }
    }
    Column(
        modifier = cell,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (day != null) {
            Text(
                "${day.dayOfMonth}",
                style = RunkkTextStyles.text(11, if (km != null) FontWeight.Bold else FontWeight.Normal),
                color = if (km != null) RunkkColors.TextPrimary else RunkkColors.TextSecondary
            )
            if (km != null) {
                Text(
                    stringResource(R.string.record_km_value, km),
                    style = RunkkTextStyles.text(10, FontWeight.Bold),
                    color = RunkkColors.TextPrimary
                )
            }
        }
    }
}

/** 고른 날 기록 줄: 경로 썸네일, "10월 16일 (목) · 오전 7:12", "5.21km · 32:04 · 6'09"". */
@Composable
private fun DayRunRow(run: RunSummary, zone: ZoneId, onClick: () -> Unit) {
    val dateFormat = rememberDateFormatter(R.string.record_day_row_pattern)
    RunRowFrame(run, onClick) {
        Text(
            dateFormat.format(run.startedAt.atZone(zone)),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        Text(
            stringResource(
                R.string.record_row_stats,
                RunkkFormat.km(run.distance),
                RunkkFormat.duration(run.elapsed),
                paceMarks(run.averagePace)
            ),
            style = RunkkTextStyles.text(16, FontWeight.Black),
            color = RunkkColors.TextPrimary
        )
    }
}

// ---------- 목록 탭(G-8) ----------

@Composable
private fun ListPane(state: RecordListUiState, actions: RecordListActions, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            state.isRecentLoading && state.recentRuns.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
            }

            state.recentError != null -> item(key = "error") {
                RunkkErrorState(state.recentError.asString(), onRetry = actions.onRetryRecent)
            }

            state.recentRuns.isEmpty() -> item(key = "empty") {
                RunkkEmptyState(
                    stringResource(R.string.record_list_recent_empty),
                    message = stringResource(R.string.record_list_empty_message)
                )
            }

            else -> {
                runGroup("week", R.string.record_list_this_week, state.thisWeekRuns, state.zone, actions.onRun)
                runGroup("earlier", R.string.record_list_earlier, state.earlierRuns, state.zone, actions.onRun)
            }
        }
        if (state.pendingCount > 0) item(key = "pending") { PendingCard(state.pendingCount) }
    }
}

/** 묶음 하나("이번 주"·"이전"): 제목과 행들. 비면 그리지 않는다. */
private fun LazyListScope.runGroup(
    key: String,
    @StringRes title: Int,
    runs: List<RunSummary>,
    zone: ZoneId,
    onRun: (String) -> Unit
) {
    if (runs.isEmpty()) return
    item(key = "title-$key") {
        Text(
            stringResource(title),
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() }
        )
    }
    items(runs, key = { "$key-" + it.recordId }) { run -> ListRunRow(run, zone, onClick = { onRun(run.recordId) }) }
}

/** 목록 행: 경로 썸네일, 거리와 태그, "10/16 목 · 32:04 · 6'09"". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListRunRow(run: RunSummary, zone: ZoneId, onClick: () -> Unit) {
    val dateFormat = rememberDateFormatter(R.string.record_list_row_date_pattern)
    val tags = RecordTag.textsOf(run, LocalReleaseScope.current.isOpen(UnreleasedFeature.CREW))
    RunRowFrame(run, onClick) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                stringResource(R.string.record_km_value, RunkkFormat.km(run.distance)),
                style = RunkkTextStyles.text(17, FontWeight.Black),
                color = RunkkColors.TextPrimary
            )
            tags.forEach { RecordTagChip(it.asString()) }
        }
        Text(
            stringResource(
                R.string.record_list_row_caption,
                dateFormat.format(run.startedAt.atZone(zone)),
                RunkkFormat.duration(run.elapsed),
                paceMarks(run.averagePace)
            ),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

@Composable
private fun RecordTagChip(label: String) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = RunkkTextStyles.text(11, FontWeight.Medium),
        color = RunkkColors.TextPrimary,
        modifier = Modifier
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// ---------- 공용 ----------

/** 기록 줄 틀: 왼쪽 경로 썸네일(없으면 빈 자리), 가운데 [content], 오른쪽 열기 표시. 누르면 기록 상세. */
@Composable
private fun RunRowFrame(run: RunSummary, onClick: () -> Unit, content: @Composable () -> Unit) {
    val openLabel = stringResource(R.string.record_list_open_detail)
    val shape = remember(run.routeShape) { run.routeShape.map(ShapePoint::toOffset) }
    Column {
        Row(
            modifier = Modifier
                // E2E(android/e2e/maestro) 선택자. 화면에는 보이지 않는다.
                .testTag("record_row")
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RouteThumbnail(shape, size = THUMBNAIL_SIZE)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
            Icon(
                RunkkIcons.ChevronRight,
                contentDescription = null,
                tint = RunkkColors.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(color = RunkkColors.Border)
    }
}

private fun ShapePoint.toOffset(): Offset = Offset(x, y)

/** 기기에 남은 미전송 기록 수 안내 카드. */
@Composable
private fun PendingCard(count: Int) {
    RunkkCard(isDashed = true, background = RunkkColors.Background, shape = RunkkShapes.Medium) {
        Text(
            stringResource(R.string.record_list_pending, count),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

@StringRes
internal fun kindLabelRes(kind: CardKind): Int = when (kind) {
    CardKind.FREE_RUN -> R.string.record_kind_free
    CardKind.BATTLE_RUN -> R.string.record_kind_battle
    CardKind.SELF_RUN -> R.string.record_kind_self
    CardKind.CELEB_RUN -> R.string.record_kind_celeb
    CardKind.CREW_RUN -> R.string.record_kind_crew
}

@Composable
internal fun kindLabel(kind: CardKind): String = stringResource(kindLabelRes(kind))

private const val DAYS_IN_WEEK = 7
private val THUMBNAIL_SIZE = 40.dp
private val WEEKDAYS = listOf(
    R.string.record_weekday_sun,
    R.string.record_weekday_mon,
    R.string.record_weekday_tue,
    R.string.record_weekday_wed,
    R.string.record_weekday_thu,
    R.string.record_weekday_fri,
    R.string.record_weekday_sat
)

@Preview(name = "G-8 기록 목록", widthDp = 390, heightDp = 900)
@Composable
private fun RecordListPreview() {
    val zone = ZoneId.of("Asia/Seoul")
    fun run(id: String, day: Int, meters: Long) = RunSummary(
        id,
        LocalDate.of(2026, 10, day).atTime(7, 12).atZone(zone).toInstant(),
        Distance(meters),
        Duration.ofSeconds(meters * 369 / 1_000),
        Pace(369),
        true,
        RunMode.FREE,
        CardKind.FREE_RUN,
        routeShape = listOf(ShapePoint(0f, 0f), ShapePoint(0.4f, 0.8f), ShapePoint(1f, 1f))
    )
    RunkkTheme(reduceMotion = true) {
        RecordListScreen(
            state = RecordListUiState(
                month = YearMonth.of(2026, 10),
                today = LocalDate.of(2026, 10, 20),
                zone = zone,
                view = RecordView.LIST,
                isLoading = false,
                recentRuns = listOf(
                    run("a", 20, 5_210),
                    run("b", 19, 2_170).copy(battleType = RecordBattleType.RANDOM, verdict = RecordVerdict.WIN),
                    run("c", 11, 12_100).copy(battleType = RecordBattleType.SELF)
                )
            ),
            actions = RecordListActions()
        )
    }
}
