package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPeriodNavigator
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.PastCrewRun
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CrewPastRunsRoute(key: Routes.CrewPastRuns, viewModel: CrewPastRunsViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key.crewId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CrewPastRunsScreen(
        state,
        CrewPastRunsActions(
            onBack = navigator::back,
            onRetry = viewModel::load,
            onPrev = viewModel::onPrevMonth,
            onNext = viewModel::onNextMonth,
            onDate = viewModel::onDate,
            onPeriodOpen = viewModel::onPeriodOpen,
            onPeriodClose = viewModel::onPeriodClose,
            onPeriodApply = viewModel::onPeriodApply,
            // 지난 줄도 W6 일정 상세(종료된 일정 모양)로 간다(설계 05-29 [임시:D-성-32]).
            onRun = { run -> navigator.navigate(Routes.CrewRunDetail(run.postId)) }
        )
    )
}

/** 지난 크루런 화면 입력 묶음. */
data class CrewPastRunsActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onPeriodOpen: () -> Unit = {},
    val onPeriodClose: () -> Unit = {},
    val onPeriodApply: (YearMonth) -> Unit = {},
    val onRun: (PastCrewRun) -> Unit = {}
)

/**
 * 지난 크루런(피그마 F-20): 월 이동 줄(‹ "2026년 10월 ˅" ›, 이번 달이면 › 흐림), 달력 카드(요일 줄, 크루런 있는 날 점, 오늘 원, 고른 날
 * 라임 원), 목록 제목("10월 크루런 10" / "10월 13일 크루런 1"), 크루런 줄(제목 16, 일시·거리·인원). 월 제목을 누르면 기간 선택(F-20b).
 */
@Composable
fun CrewPastRunsScreen(state: CrewPastRunsUiState, actions: CrewPastRunsActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_past_runs_title), onBack = actions.onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "month") { MonthBar(state, actions) }
            item(key = "calendar") { CalendarCard(state, actions.onDate) }
            val error = state.error
            when {
                error != null -> item(key = "error") { RunkkErrorState(error.asString(), onRetry = actions.onRetry) }

                state.isLoading -> item(key = "loading") {
                    RunkkLoading(height = 120.dp)
                }

                else -> {
                    item(key = "header") { ListHeader(state) }
                    if (state.shownRuns.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.crew_past_runs_empty),
                                style = RunkkTextStyles.Label,
                                color = RunkkColors.TextSecondary
                            )
                        }
                    }
                    items(state.shownRuns, key = { it.postId }) { PastRunRow(it, actions.onRun) }
                }
            }
        }
    }
    if (state.isPeriodSheetOpen) PeriodSheet(state, actions)
}

/** 월 이동 줄: ‹, 가운데 "2026년 10월" 16 Bold + 꺾쇠(누르면 기간 선택), ›(첫 달·이번 달 끝이면 흐리고 누를 수 없다). */
@Composable
private fun MonthBar(state: CrewPastRunsUiState, actions: CrewPastRunsActions) {
    val title = stringResource(R.string.crew_past_runs_month, state.month.year, state.month.monthValue)
    RunkkPeriodNavigator(
        label = title,
        onPrevious = actions.onPrev.takeIf { state.canGoPrev },
        onNext = actions.onNext.takeIf { state.canGoNext },
        onLabelClick = actions.onPeriodOpen,
        labelClickLabel = stringResource(R.string.crew_past_runs_month_pick, state.month.year, state.month.monthValue),
        previousDescription = stringResource(R.string.crew_past_runs_prev),
        nextDescription = stringResource(R.string.crew_past_runs_next)
    )
}

/** 달력 카드(흰 바탕, 모서리 16, 줄 간격 4): 요일 줄(일~토, 14 보조색) + 날짜 칸(높이 36). */
@Composable
private fun CalendarCard(state: CrewPastRunsUiState, onDate: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    val lead = first.dayOfWeek.value % DAYS_IN_WEEK
    val days = state.month.lengthOfMonth()
    val cells: List<LocalDate?> = List(lead) { null } + (1..days).map { state.month.atDay(it) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Surface)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            WEEK.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    style = RunkkTextStyles.text(14),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.weight(1f).height(36.dp).padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until DAYS_IN_WEEK) {
                    val date = week.getOrNull(i)
                    Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, state, onDate)
                    }
                }
            }
        }
    }
}

/** 날짜 칸: 오늘 지름 36 원(todayMark), 고른 날 라임 원, 크루런 있는 날 숫자 아래 점 4, 오늘 뒤 날짜는 보조색. */
@Composable
private fun DayCell(date: LocalDate, state: CrewPastRunsUiState, onDate: (LocalDate) -> Unit) {
    val isSelected = date == state.selectedDate
    val isToday = date == state.today
    val hasRun = date in state.runDays
    val runLabel = stringResource(R.string.crew_past_runs_day_run)
    Box(
        Modifier
            .size(48.dp)
            .clickable(role = Role.Button) { onDate(date) }
            .semantics {
                selected = isSelected
                if (hasRun) stateDescription = runLabel
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> RunkkColors.LimeFill
                        isToday -> RunkkColors.TodayMark
                        else -> Color.Transparent
                    }
                )
        )
        Text(
            date.dayOfMonth.toString(),
            style = RunkkTextStyles.text(14),
            color = when {
                isSelected -> RunkkColors.OnLimeFill
                date.isAfter(state.today) -> RunkkColors.TextSecondary
                else -> RunkkColors.TextPrimary
            }
        )
        if (hasRun) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 9.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) RunkkColors.OnLimeFill else RunkkColors.TextPrimary)
            )
        }
    }
}

/** 목록 제목(높이 30): "10월 크루런" + 개수, 날짜를 골랐으면 "10월 13일 크루런" + 개수. */
@Composable
private fun ListHeader(state: CrewPastRunsUiState) {
    val title = state.selectedDate?.let {
        stringResource(R.string.crew_past_runs_day_header, it.monthValue, it.dayOfMonth)
    } ?: stringResource(R.string.crew_past_runs_month_header, state.month.monthValue)
    RunkkSectionHeader(
        title,
        modifier = Modifier.heightIn(min = 30.dp),
        count = stringResource(R.string.crew_past_runs_count, state.shownRuns.size),
        countColor = RunkkColors.TextPrimary
    )
}

/** 크루런 한 줄(높이 58, 아래 divider): 제목 16, 아래 일시·거리·인원(취소면 "취소됨"). 누르면 일정 상세(W6). */
@Composable
private fun PastRunRow(run: PastCrewRun, onRun: (PastCrewRun) -> Unit) {
    val isFinished = run.status == CrewRunStatus.FINISHED
    val at = run.scheduledAt.atZone(CrewPastRunsUiState.ZONE)
    val items = listOfNotNull(
        MetaItem(RunkkIcons.CalendarToday, ROW_TIME.format(at)),
        MetaItem(RunkkIcons.DirectionsRun, distanceLabel(run.distance)),
        if (isFinished) {
            MetaItem(RunkkIcons.Group, stringResource(R.string.crew_past_runs_people, run.runnerCount))
        } else {
            MetaItem(RunkkIcons.CheckCircle, stringResource(R.string.crew_past_runs_cancelled))
        }
    )
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(role = Role.Button) { onRun(run) }
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
        ) {
            Text(
                run.title,
                style = RunkkTextStyles.text(16, lineHeight = 19),
                color = RunkkColors.TextPrimary,
                maxLines = 1
            )
            MetaRow(items)
        }
        HorizontalDivider(color = RunkkColors.Border, thickness = 1.dp)
    }
}

/** 거리 표시: 없으면 "제한 없음", 하프(21.0975km)면 "하프", 정수 km면 "5km", 아니면 소수 첫째 자리. */
@Composable
private fun distanceLabel(distance: Distance?): String = when {
    distance == null -> stringResource(R.string.crew_past_runs_unlimited)
    distance.meters in HALF_RANGE -> stringResource(R.string.crew_past_runs_half)
    else -> stringResource(R.string.crew_contribution_km, targetKm(distance))
}

/**
 * 기간 선택 시트(피그마 F-20b): 머리 "기간 선택" + ✕, 년·월 두 열(높이 180, 줄 36, 고른 줄 18 Bold + surfaceHigh 막대, 다른 줄 16
 * Regular 흐림), 라임 "적용". 년은 가장 이른 달의 해부터 올해까지, 달은 그 범위 안만 고른다(결정: 현재 연도까지).
 */
@Composable
private fun PeriodSheet(state: CrewPastRunsUiState, actions: CrewPastRunsActions) {
    var year by remember { mutableIntStateOf(state.month.year) }
    var month by remember { mutableIntStateOf(state.month.monthValue) }
    val years = (state.firstMonth.year..state.currentMonth.year).toList()
    val months = (1..MONTHS).filter { m ->
        val ym = YearMonth.of(year, m)
        !ym.isBefore(state.firstMonth) && !ym.isAfter(state.currentMonth)
    }
    val shownMonth = if (month in months) month else months.lastOrNull() ?: month
    RunkkBottomSheet(onDismissRequest = actions.onPeriodClose, title = stringResource(R.string.crew_past_runs_period)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WheelColumn(
                years.map {
                    it to stringResource(R.string.crew_past_runs_year, it)
                },
                year,
                Modifier.weight(1f)
            ) {
                year = it
            }
            WheelColumn(
                months.map {
                    it to stringResource(R.string.crew_past_runs_month_only, it)
                },
                shownMonth,
                Modifier.weight(1f)
            ) {
                month = it
            }
        }
        RunkkButton(
            stringResource(R.string.crew_past_runs_apply),
            onClick = { actions.onPeriodApply(YearMonth.of(year, shownMonth)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 고르기 열(높이 180): 고른 줄은 surfaceHigh 막대(모서리 12) + 18 Bold, 다른 줄은 16 Regular 45%. */
@Composable
private fun WheelColumn(options: List<Pair<Int, String>>, current: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    Column(modifier.height(180.dp).verticalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            val isSelected = value == current
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) RunkkColors.SurfaceHigh else Color.Transparent)
                    .clickable(role = Role.Button) { onSelect(value) }
                    .semantics { selected = isSelected },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = RunkkTextStyles.text(
                        if (isSelected) 18 else 16,
                        if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.alpha(if (isSelected) 1f else DIM_ALPHA)
                )
            }
        }
    }
}

private const val DAYS_IN_WEEK = 7
private const val MONTHS = 12
private const val DIM_ALPHA = 0.45f

/** 하프 마라톤 거리(21,097.5m)를 서버가 반올림해 줄 수 있는 범위. */
private val HALF_RANGE = HALF_METERS..HALF_METERS + 1

/** 일요일부터(피그마 "일 월 화 수 목 금 토"). */
private val WEEK = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY
)

/** "10.20(화) 19:00". */
private val ROW_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M.d(E) HH:mm", Locale.KOREAN)
