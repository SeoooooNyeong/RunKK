package com.a207.runkk.app.ui.record

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
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
        RunkkTopBar(title = "내 기록", onBack = onBack) {
            RunkkSegmentedTabs(listOf("달력", "목록"), selectedIndex = state.view.ordinal, onSelect = {
                onViewSelected(RecordView.entries[it])
            }, compact = true)
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
            when {
                state.loading && state.runs.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
                }

                state.error != null -> item(key = "error") { RunkkErrorState(state.error, onRetry = onRetry) }

                state.runs.isEmpty() -> item(key = "empty") {
                    RunkkEmptyState("이 달에는 기록이 없어요", message = "홈에서 자유런을 시작해 보세요")
                }

                else -> {
                    item(key = "title") { RunkkSectionTitle("최신순") }
                    items(state.runs, key = {
                        it.recordId
                    }) { run -> RunRow(run, state.zone, onClick = { onRun(run.recordId) }) }
                }
            }
            if (state.pendingCount > 0) {
                item(key = "pending") {
                    RunkkCard(dashed = true, background = RunkkColors.Background, shape = RunkkShapes.Medium) {
                        Text(
                            "미전송 기록 ${state.pendingCount}건 · 연결되면 자동으로 보내요",
                            style = RunkkTextStyles.Caption,
                            color = RunkkColors.TextSecondary
                        )
                    }
                }
            }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunkkIconButton(
                RunkkIcons.Back,
                contentDescription = "이전 달",
                onClick = onPreviousMonth,
                borderColor = RunkkColors.Surface
            )
            Text(
                MONTH.format(state.month),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            RunkkIconButton(
                RunkkIcons.ChevronRight,
                contentDescription = "다음 달",
                onClick = onNextMonth,
                borderColor = RunkkColors.Surface
            )
        }
        if (showCalendar) Calendar(state.month, state.dailyDistance)
        Row {
            Text(
                buildAnnotatedString {
                    append("이번 달 ")
                    withStyle(SpanStyle(color = RunkkColors.TextPrimary, fontWeight = FontWeight.Bold)) {
                        append("${state.validCount}")
                    }
                    append("회")
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
                        append(RunkkFormat.kmShort(state.validDistance))
                    }
                    append(" km")
                },
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
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
            WEEK.forEach {
                Text(
                    it,
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
    Column(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (ran) RunkkColors.Lime else RunkkColors.Background)
            .semantics(mergeDescendants = true) {
                if (day != null) contentDescription = "${day.dayOfMonth}일" + if (ran) " ${km}킬로미터" else ""
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
    Row(
        modifier = Modifier
            // E2E(android/e2e/maestro) 선택자. 화면에는 보이지 않는다.
            .testTag("record_row")
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
            .clickable(role = Role.Button, onClickLabel = "기록 상세 열기", onClick = onClick)
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
                        append(kindLabel(run.cardKind))
                    }
                },
                style = RunkkTextStyles.text(15, FontWeight.Black),
                color = RunkkColors.TextPrimary
            )
            Text(
                "${DAY.format(
                    run.startedAt.atZone(zone)
                )} · ${RunkkFormat.duration(run.elapsed)} · ${RunkkFormat.pace(run.averagePace)}/km",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
        if (!run.valid) Text("집계 제외", style = RunkkTextStyles.text(11, FontWeight.Black), color = RunkkColors.Lavender)
    }
}

internal fun kindLabel(kind: CardKind): String = when (kind) {
    CardKind.FREE_RUN -> "자유런"
    CardKind.BATTLE_RUN -> "배틀런"
    CardKind.SELF_RUN -> "나와의 싸움"
    CardKind.CELEB_RUN -> "셀럽런"
    CardKind.CREW_RUN -> "크루런"
}

private fun shortLabel(kind: CardKind): String = when (kind) {
    CardKind.FREE_RUN -> "FREE"
    CardKind.BATTLE_RUN -> "VS"
    CardKind.SELF_RUN -> "SELF"
    CardKind.CELEB_RUN -> "CELEB"
    CardKind.CREW_RUN -> "CREW"
}

private const val DAYS_IN_WEEK = 7
private val WEEK = listOf("일", "월", "화", "수", "목", "금", "토")
private val MONTH = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

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
