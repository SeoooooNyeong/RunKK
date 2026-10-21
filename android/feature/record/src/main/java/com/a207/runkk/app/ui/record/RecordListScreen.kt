package com.a207.runkk.app.ui.record

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.RunSummary
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 기록 목록·달력(C1). */
@Composable
fun RecordListRoute(viewModel: RecordListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RecordListScreen(
        state = state,
        onBack = navigator::back,
        onViewSelected = viewModel::onViewSelected,
        onPreviousMonth = viewModel::onPreviousMonth,
        onNextMonth = viewModel::onNextMonth,
        onRun = { navigator.navigate(Routes.RecordDetail(it)) },
        onRetry = viewModel::refresh
    )
}

@Composable
fun RecordListScreen(
    state: RecordListUiState,
    onBack: () -> Unit,
    onViewSelected: (RecordView) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRun: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.record_list_title), onBack = onBack) {
            RunkkSegmentedTabs(
                listOf(
                    stringResource(R.string.record_list_tab_calendar),
                    stringResource(R.string.record_list_tab_list)
                ),
                selectedIndex = state.view.ordinal,
                onSelect = { onViewSelected(RecordView.entries[it]) },
                compact = true
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "month") {
                MonthCard(
                    state,
                    showCalendar = state.view == RecordView.CALENDAR,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth
                )
            }
            runListItems(state, onRun, onRetry)
            if (state.pendingCount > 0) {
                item(key = "pending") { PendingCard(state.pendingCount) }
            }
        }
    }
}

/** 기기에 남은 미전송 기록 수 안내 카드. */
@Composable
private fun PendingCard(count: Int) {
    RunkkCard(dashed = true, background = RunkkColors.Background, shape = RunkkShapes.Medium) {
        Text(
            stringResource(R.string.record_list_pending, count),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 월 카드 아래 목록 칸: 불러오는 중, 오류, 빈 달, 최신순 기록 줄. */
private fun LazyListScope.runListItems(state: RecordListUiState, onRun: (String) -> Unit, onRetry: () -> Unit) {
    when {
        state.loading && state.runs.isEmpty() -> item(key = "loading") {
            Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
        }

        state.error != null -> item(key = "error") { RunkkErrorState(state.error.asString(), onRetry = onRetry) }

        state.runs.isEmpty() -> item(key = "empty") {
            RunkkEmptyState(
                stringResource(R.string.record_list_empty_title),
                message = stringResource(R.string.record_list_empty_message)
            )
        }

        else -> {
            item(key = "title") { RunkkSectionTitle(stringResource(R.string.record_list_sort_latest)) }
            items(state.runs, key = {
                it.recordId
            }) { run -> RunRow(run, state.zone, onClick = { onRun(run.recordId) }) }
        }
    }
}

@Composable
private fun MonthCard(
    state: RecordListUiState,
    showCalendar: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    RunkkCard(shape = RunkkShapes.ExtraLarge, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MonthHeader(state.month, onPreviousMonth, onNextMonth)
        if (showCalendar) Calendar(state.month, state.dailyDistance)
        MonthSummary(state.validCount, state.validDistance)
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
            borderColor = RunkkColors.Surface
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
            borderColor = RunkkColors.Surface
        )
    }
}

/** 달 요약 줄: 이번 달 횟수(숫자만 굵게)와 총 거리. */
@Composable
private fun MonthSummary(count: Int, distance: Distance) {
    val countText = stringResource(R.string.record_list_month_count, count)
    Row {
        Text(
            buildAnnotatedString {
                append(countText)
                val start = countText.indexOf("$count")
                if (start >= 0) {
                    addStyle(
                        SpanStyle(color = RunkkColors.TextPrimary, fontWeight = FontWeight.Bold),
                        start,
                        start + "$count".length
                    )
                }
            },
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = RunkkColors.Lime,
                        fontFamily = RunkkTextStyles.display(16).fontFamily,
                        fontSize = RunkkTextStyles.display(16).fontSize
                    )
                ) {
                    append(RunkkFormat.kmShort(distance))
                }
                append(" km")
            },
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 달력: 일요일 시작 7열. 달린 날은 라임 칸에 날짜와 km. */
@Composable
private fun Calendar(month: YearMonth, daily: Map<LocalDate, Distance>) {
    val lead = month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val cells: List<LocalDate?> = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val rows = cells.chunked(DAYS_IN_WEEK).map {
        if (it.size <
            DAYS_IN_WEEK
        ) {
            it + List(DAYS_IN_WEEK - it.size) { null }
        } else {
            it
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAYS.forEach {
                Text(
                    stringResource(it),
                    style = RunkkTextStyles.Micro,
                    color = RunkkColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        rows.forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day -> DayCell(day, day?.let(daily::get), Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, distance: Distance?, modifier: Modifier) {
    val km = distance?.takeIf { it.meters > 0 }?.let(RunkkFormat::kmShort)
    val ran = km != null
    val spoken = when {
        day == null -> null
        km != null -> stringResource(R.string.record_calendar_day_distance_spoken, day.dayOfMonth, km)
        else -> stringResource(R.string.record_calendar_day_spoken, day.dayOfMonth)
    }
    Column(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (ran) RunkkColors.Lime else RunkkColors.Background)
            .semantics(mergeDescendants = true) {
                if (spoken != null) contentDescription = spoken
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (day != null) {
            Text(
                "${day.dayOfMonth}",
                style = RunkkTextStyles.text(if (ran) 10 else 11, if (ran) FontWeight.Bold else FontWeight.Normal),
                color = if (ran) RunkkColors.OnLime else RunkkColors.TextSecondary
            )
            if (km !=
                null
            ) {
                Text(km, style = RunkkTextStyles.display(13, lineHeightRatio = 1f), color = RunkkColors.OnLime)
            }
        }
    }
}

@Composable
private fun RunRow(run: RunSummary, zone: ZoneId, onClick: () -> Unit) {
    val dayFormat = rememberDateFormatter(R.string.record_day_pattern)
    val openLabel = stringResource(R.string.record_list_open_detail)
    Row(
        modifier = Modifier
            // E2E(android/e2e/maestro) 선택자. 화면에는 보이지 않는다.
            .testTag("record_row")
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RunkkShapes.Small).background(RunkkColors.Border),
            contentAlignment = Alignment.Center
        ) {
            Text(shortLabel(run.cardKind), style = RunkkTextStyles.display(14), color = RunkkColors.Lime)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RunRowTitle(run)
            Text(
                "${dayFormat.format(
                    run.startedAt.atZone(zone)
                )} · ${RunkkFormat.duration(run.elapsed)} · ${RunkkFormat.pace(run.averagePace)}/km",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        if (!run.valid) {
            Text(
                stringResource(R.string.record_excluded),
                style = RunkkTextStyles.text(11, FontWeight.Black),
                color = RunkkColors.Lavender
            )
        }
    }
}

/** 기록 줄 제목: 거리와 작은 글씨의 종류 이름. */
@Composable
private fun RunRowTitle(run: RunSummary) {
    val kind = kindLabel(run.cardKind)
    Text(
        buildAnnotatedString {
            append("${RunkkFormat.km(run.distance)}km ")
            withStyle(
                SpanStyle(
                    fontSize = RunkkTextStyles.Caption.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = RunkkColors.TextSecondary
                )
            ) {
                append(kind)
            }
        },
        style = RunkkTextStyles.text(15, FontWeight.Black),
        color = RunkkColors.TextPrimary
    )
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

private fun shortLabel(kind: CardKind): String = when (kind) {
    CardKind.FREE_RUN -> "FREE"
    CardKind.BATTLE_RUN -> "VS"
    CardKind.SELF_RUN -> "SELF"
    CardKind.CELEB_RUN -> "CELEB"
    CardKind.CREW_RUN -> "CREW"
}

private const val DAYS_IN_WEEK = 7
private val WEEKDAYS = listOf(
    R.string.record_weekday_sun,
    R.string.record_weekday_mon,
    R.string.record_weekday_tue,
    R.string.record_weekday_wed,
    R.string.record_weekday_thu,
    R.string.record_weekday_fri,
    R.string.record_weekday_sat
)

@Preview(name = "C1 기록 목록·달력", widthDp = 390, heightDp = 1000)
@Composable
private fun RecordListPreview() {
    val zone = ZoneId.of("Asia/Seoul")
    fun run(id: String, day: Int, meters: Long, kind: CardKind = CardKind.FREE_RUN, valid: Boolean = true) = RunSummary(
        id,
        LocalDate.of(2026, 10, day).atTime(7, 0).atZone(zone).toInstant(),
        Distance(meters),
        Duration.ofSeconds(meters * 332 / 1_000),
        Pace(332),
        valid,
        RunMode.FREE,
        kind
    )
    val runs =
        listOf(
            run("a", 20, 5_020),
            run("b", 17, 5_000, CardKind.BATTLE_RUN),
            run("c", 14, 10_200),
            run("d", 12, 2_000, valid = false)
        )
    RunkkTheme(reduceMotion = true) {
        RecordListScreen(
            state = RecordListUiState(
                month = YearMonth.of(2026, 10),
                loading = false,
                runs = runs,
                dailyDistance = runs.associate { it.startedAt.atZone(zone).toLocalDate() to it.distance },
                pendingCount = 1,
                zone = zone
            ),
            onBack = {},
            onViewSelected = {},
            onPreviousMonth = {},
            onNextMonth = {},
            onRun = {},
            onRetry = {}
        )
    }
}
