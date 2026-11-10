package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.crew.CrewRunRepeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---------- 크루런 모집하기·수정(피그마 F-12·F-12b~f·F-13d·F-13e, S15P31A207-297) ----------

/** 모집하기(크루 ID)·수정(모집글 ID) 경로. 올리거나 저장하면 앞 화면으로 돌아간다. */
@Composable
fun CrewRunWriteRoute(mode: CrewRunWriteMode, viewModel: CrewRunWriteViewModel = hiltViewModel()) {
    LaunchedEffect(mode) { viewModel.bind(mode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewRunWriteEvent.Posted -> navigator.back()
            }
        }
    }
    CrewRunWriteScreen(
        state = state,
        actions = CrewRunWriteActions(
            onBack = navigator::back,
            onTitle = viewModel::onTitle,
            onBody = viewModel::onBody,
            onSheet = viewModel::onSheet,
            onSheetDismiss = viewModel::onSheetDismiss,
            onDate = viewModel::onDate,
            onTime = viewModel::onTime,
            onDistance = viewModel::onDistance,
            onRepeat = viewModel::onRepeat,
            onSubmit = viewModel::onSubmit,
            onRetry = viewModel::onRetry
        )
    )
}

/** 모집하기 화면 입력 묶음(시험·미리보기에서 상태 없는 화면을 그대로 쓴다). */
data class CrewRunWriteActions(
    val onBack: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onBody: (String) -> Unit = {},
    val onSheet: (CrewRunWriteSheet) -> Unit = {},
    val onSheetDismiss: () -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onTime: (LocalTime) -> Unit = {},
    val onDistance: (CrewRunDistanceChoice) -> Unit = {},
    val onRepeat: (CrewRunRepeat) -> Unit = {},
    val onSubmit: () -> Unit = {},
    val onRetry: () -> Unit = {}
)

/**
 * 크루런 모집하기(F-12)·모집글 수정(F-13d): 공통 헤더(가운데 제목), 칸 제목 → 내용(72) → 날짜 → 시간 → 거리 → 반복, 아래 Lime Large
 * "올리기"/"저장". 날짜·시간·거리·반복은 누르면 바텀시트가 열린다. 수정이면 날짜·시간 칸이 잠긴다.
 */
@Composable
fun CrewRunWriteScreen(state: CrewRunWriteUiState, actions: CrewRunWriteActions, modifier: Modifier = Modifier) {
    val title = stringResource(if (state.isEdit) R.string.crew_run_edit_title else R.string.crew_run_write_title)
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = title, onBack = actions.onBack)
        when {
            state.isLoading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RunkkLoading()
            }

            state.loadError != null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RunkkErrorState(state.loadError.asString(), onRetry = actions.onRetry)
            }

            else -> Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WriteFields(state, actions)
                state.error?.let {
                    Text(
                        it.asString(),
                        style = RunkkTextStyles.text(13, FontWeight.Medium),
                        color = RunkkColors.Violet,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
        }
        RunkkButton(
            stringResource(if (state.isEdit) R.string.crew_run_edit_submit else R.string.crew_run_write_submit),
            onClick = actions.onSubmit,
            isEnabled = state.isValid,
            isLoading = state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp)
        )
    }
    WriteSheet(state, actions)
}

@Composable
private fun WriteFields(state: CrewRunWriteUiState, actions: CrewRunWriteActions) {
    val titleLabel = stringResource(R.string.crew_run_write_title_label)
    CrewFormField(titleLabel) {
        CrewFormInput(
            state.title,
            actions.onTitle,
            placeholder = stringResource(R.string.crew_run_write_title_placeholder)
        )
    }
    val bodyLabel = stringResource(R.string.crew_run_write_body_label)
    CrewFormField(bodyLabel) {
        CrewFormInput(
            state.body,
            actions.onBody,
            placeholder = stringResource(R.string.crew_run_write_body_placeholder),
            minHeight = 72.dp
        )
    }
    val dateLabel = stringResource(R.string.crew_run_write_date_label)
    CrewFormField(dateLabel) {
        CrewFormSelect(
            value = state.date?.let { writeDateLabel(it) },
            placeholder = stringResource(R.string.crew_run_write_date_placeholder),
            label = dateLabel,
            onClick = { actions.onSheet(CrewRunWriteSheet.DATE) },
            isLocked = state.isEdit
        )
    }
    val timeFieldLabel = stringResource(R.string.crew_run_write_time_label)
    CrewFormField(timeFieldLabel) {
        CrewFormSelect(
            value = state.time?.let { timeLabel(it) },
            placeholder = stringResource(R.string.crew_run_write_time_placeholder),
            label = timeFieldLabel,
            onClick = { actions.onSheet(CrewRunWriteSheet.TIME) },
            isLocked = state.isEdit
        )
    }
    val distanceLabel = stringResource(R.string.crew_run_write_distance_label)
    CrewFormField(distanceLabel) {
        CrewFormSelect(
            value = if (state.hasDistance) crewRunDistanceLabel(state.distanceToSend) else null,
            placeholder = stringResource(R.string.crew_run_write_distance_placeholder),
            label = distanceLabel,
            onClick = { actions.onSheet(CrewRunWriteSheet.DISTANCE) }
        )
    }
    val repeatFieldLabel = stringResource(R.string.crew_run_write_repeat_label)
    CrewFormField(repeatFieldLabel) {
        CrewFormSelect(
            value = repeatLabel(state.repeat, state.date?.dayOfWeek),
            placeholder = stringResource(R.string.crew_run_repeat_none),
            label = repeatFieldLabel,
            onClick = { actions.onSheet(CrewRunWriteSheet.REPEAT) }
        )
    }
}

/** 날짜 칸 값 "10.24 (토)". */
@Composable
internal fun writeDateLabel(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern(stringResource(R.string.crew_run_write_date_pattern), Locale.KOREAN))

/** 시간 칸 값 "오전 07:00"(12시간제, 피그마 F-12d). */
@Composable
internal fun timeLabel(time: LocalTime): String {
    val half = stringResource(if (time.hour < NOON) R.string.crew_run_time_am else R.string.crew_run_time_pm)
    val hour12 = (time.hour % NOON).let { if (it == 0) NOON else it }
    return stringResource(
        R.string.crew_run_time_value,
        half,
        String.format(Locale.US, "%02d:%02d", hour12, time.minute)
    )
}

/** 반복 칸 값: "반복 안 함" 또는 "매주 토요일"(날짜를 안 골랐으면 "매주"). */
@Composable
internal fun repeatLabel(repeat: CrewRunRepeat, day: DayOfWeek?): String = when (repeat) {
    CrewRunRepeat.NONE -> stringResource(R.string.crew_run_repeat_none)

    CrewRunRepeat.WEEKLY ->
        day?.let { stringResource(R.string.crew_run_repeat_weekly, it.getDisplayName(TextStyle.FULL, Locale.KOREAN)) }
            ?: stringResource(R.string.crew_run_detail_weekly)
}

@Composable
private fun WriteSheet(state: CrewRunWriteUiState, actions: CrewRunWriteActions) {
    when (state.sheet) {
        CrewRunWriteSheet.DATE -> DateSheet(state, actions)

        CrewRunWriteSheet.TIME -> TimeSheet(state.time ?: CrewRunWriteUiState.DEFAULT_TIME, actions)

        CrewRunWriteSheet.DISTANCE ->
            DistanceSheet(state.distance ?: CrewRunDistanceChoice.KM5, isLegacy = state.legacyDistance != null, actions)

        CrewRunWriteSheet.REPEAT -> RepeatSheet(state.repeat, state.date?.dayOfWeek, actions)

        null -> Unit
    }
}

/** 시트 아래 "확인"(Lime Medium 48). */
@Composable
private fun SheetConfirm(onClick: () -> Unit, isEnabled: Boolean = true) {
    RunkkButton(
        stringResource(R.string.crew_run_sheet_confirm),
        onClick = onClick,
        size = RunkkButtonSize.Medium,
        isEnabled = isEnabled,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- 날짜 시트(F-12b·F-12c) ----------

/**
 * 날짜 선택 시트: 월 이동(‹ 10월 ›, 범위 밖 달은 흐림), 일~토 머리, 7열 날짜 칸(36). 오늘~14일 뒤만 Bold로 고를 수 있고 나머지는 흐리다.
 * 오늘은 todayMark 원, 고른 날은 primary 원이다. "확인"을 누르면 고른 날을 칸에 넣는다.
 */
@Composable
private fun DateSheet(state: CrewRunWriteUiState, actions: CrewRunWriteActions) {
    val first = YearMonth.from(state.today)
    val last = YearMonth.from(state.lastDate)
    var month by remember { mutableStateOf(state.date?.let(YearMonth::from) ?: first) }
    var picked by remember { mutableStateOf(state.date) }
    RunkkBottomSheet(onDismissRequest = actions.onSheetDismiss, title = stringResource(R.string.crew_run_sheet_date)) {
        MonthBar(month, canPrev = month > first, canNext = month < last, onMonth = { month = it })
        CalendarGrid(
            month = month,
            today = state.today,
            selectable = state.today..state.lastDate,
            picked = picked,
            onPick = { picked = it }
        )
        SheetConfirm(onClick = { picked?.let(actions.onDate) }, isEnabled = picked != null)
    }
}

@Composable
private fun MonthBar(month: YearMonth, canPrev: Boolean, canNext: Boolean, onMonth: (YearMonth) -> Unit) {
    val spokenMonth = stringResource(R.string.crew_run_calendar_month_spoken, month.year, month.monthValue)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow("‹", stringResource(R.string.crew_run_calendar_prev), canPrev) { onMonth(month.minusMonths(1)) }
        Text(
            stringResource(R.string.crew_run_calendar_month, month.monthValue),
            style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 19),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics {
                heading()
                contentDescription = spokenMonth
            }
        )
        MonthArrow("›", stringResource(R.string.crew_run_calendar_next), canNext) { onMonth(month.plusMonths(1)) }
    }
}

/** 달력 이동 화살표(피그마 RunKK/Calendar/이동 화살표 32, 글자 28). 범위 밖이면 흐리고 누를 수 없다. */
@Composable
private fun MonthArrow(glyph: String, label: String, isEnabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = isEnabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = RunkkTextStyles.text(28, lineHeight = 26),
            color = if (isEnabled) {
                RunkkColors.TextPrimary
            } else {
                RunkkColors.TextSecondary.copy(alpha = MONTH_ARROW_DISABLED_ALPHA)
            }
        )
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selectable: ClosedRange<LocalDate>,
    picked: LocalDate?,
    onPick: (LocalDate) -> Unit
) {
    // 일요일 시작(피그마 F-12b): 첫 날 앞의 빈칸 수는 요일 값(월 1 … 일 7) % 7이다.
    val lead = month.atDay(1).dayOfWeek.value % DAYS_IN_WEEK
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val weekdays = listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    style = RunkkTextStyles.text(14, lineHeight = 17),
                    color = RunkkColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).height(36.dp).padding(top = 9.dp)
                )
            }
        }
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).height(36.dp), contentAlignment = Alignment.Center) {
                        if (date != null) DayCell(date, date == today, date in selectable, date == picked, onPick)
                    }
                }
                repeat(DAYS_IN_WEEK - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelectable: Boolean,
    isPicked: Boolean,
    onPick: (LocalDate) -> Unit
) {
    val spoken = listOfNotNull(
        date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
        stringResource(R.string.crew_run_calendar_today).takeIf { isToday }
    ).joinToString(", ")
    val background = when {
        isPicked -> RunkkColors.LimeFill
        isToday -> RunkkColors.TodayMark
        else -> null
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(background?.let { Modifier.background(it) } ?: Modifier)
            .clickable(enabled = isSelectable, role = Role.Button) { onPick(date) }
            .semantics {
                contentDescription = spoken
                selected = isPicked
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = RunkkTextStyles.text(14, if (isSelectable) FontWeight.Bold else FontWeight.Normal, lineHeight = 17),
            color = when {
                isPicked -> RunkkColors.OnLimeFill
                isSelectable -> RunkkColors.TextPrimary
                else -> RunkkColors.TextSecondary.copy(alpha = PAST_DAY_ALPHA)
            }
        )
    }
}

// ---------- 시간 시트(F-12d) ----------

/** 시간 선택 시트: 휠 3열(오전·오후 / 시 01~12 / 분 00~55, 5분 단위). */
@Composable
private fun TimeSheet(initial: LocalTime, actions: CrewRunWriteActions) {
    val halves = listOf(stringResource(R.string.crew_run_time_am), stringResource(R.string.crew_run_time_pm))
    val hours = (1..NOON).map { String.format(Locale.US, "%02d", it) }
    val minutes = (0 until MINUTES_IN_HOUR step CrewRunWriteUiState.MINUTE_STEP).map {
        String.format(Locale.US, "%02d", it)
    }
    var half by remember { mutableIntStateOf(if (initial.hour < NOON) 0 else 1) }
    var hour by remember { mutableIntStateOf(((initial.hour % NOON).let { if (it == 0) NOON else it }) - 1) }
    var minute by remember { mutableIntStateOf(initial.minute / CrewRunWriteUiState.MINUTE_STEP) }
    val hourSpoken = hours.map { stringResource(R.string.crew_run_time_hour, it) }
    val minuteSpoken = minutes.map { stringResource(R.string.crew_run_time_minute, it) }
    RunkkBottomSheet(onDismissRequest = actions.onSheetDismiss, title = stringResource(R.string.crew_run_sheet_time)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrewWheel(halves, half, onSelect = { half = it }, modifier = Modifier.weight(1f))
            CrewWheel(hours, hour, onSelect = { hour = it }, modifier = Modifier.weight(1f), spokenItems = hourSpoken)
            CrewWheel(minutes, minute, onSelect = {
                minute = it
            }, modifier = Modifier.weight(1f), spokenItems = minuteSpoken)
        }
        SheetConfirm(onClick = {
            val hour24 = (hour + 1) % NOON + if (half == 1) NOON else 0
            actions.onTime(LocalTime.of(hour24, minute * CrewRunWriteUiState.MINUTE_STEP))
        })
    }
}

// ---------- 거리 시트(F-12e) ----------

/**
 * 거리 선택 시트: 휠 1열(제한 없음, 3km, 5km, 10km, 하프). [isLegacy]면 기존 1km 글이라 휠을 돌리지 않고 "확인"을 누르면 1km를 그대로 둔다.
 */
@Composable
private fun DistanceSheet(initial: CrewRunDistanceChoice, isLegacy: Boolean, actions: CrewRunWriteActions) {
    val resources = LocalContext.current.resources
    val choices = CrewRunDistanceChoice.entries
    val labels = choices.map { crewRunDistanceLabel(resources, it.distance) }
    var index by remember { mutableIntStateOf(choices.indexOf(initial)) }
    RunkkBottomSheet(
        onDismissRequest = actions.onSheetDismiss,
        title = stringResource(R.string.crew_run_sheet_distance)
    ) {
        CrewWheel(labels, index, onSelect = { index = it }, modifier = Modifier.fillMaxWidth())
        SheetConfirm(onClick = {
            if (isLegacy &&
                index == choices.indexOf(initial)
            ) {
                actions.onSheetDismiss()
            } else {
                actions.onDistance(choices[index])
            }
        })
    }
}

// ---------- 반복 시트(F-12f·F-13e) ----------

/** 반복 설정 시트: "반복 안 함" / "매주 토요일" 라디오, 고른 줄 아래 안내 문구(13 textSecondary), "확인". */
@Composable
private fun RepeatSheet(initial: CrewRunRepeat, day: DayOfWeek?, actions: CrewRunWriteActions) {
    var picked by remember { mutableStateOf(initial) }
    RunkkBottomSheet(
        onDismissRequest = actions.onSheetDismiss,
        title = stringResource(R.string.crew_run_sheet_repeat)
    ) {
        Column {
            CrewRunRepeat.entries.forEachIndexed { index, repeat ->
                CrewRadioRow(
                    repeatLabel(repeat, day),
                    isSelected = picked == repeat,
                    onClick = { picked = repeat },
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 14.dp)
                )
                if (picked == repeat) {
                    Text(
                        stringResource(
                            if (repeat == CrewRunRepeat.WEEKLY) {
                                R.string.crew_run_repeat_hint_weekly
                            } else {
                                R.string.crew_run_repeat_hint_none
                            }
                        ),
                        style = RunkkTextStyles.text(13, lineHeight = 16),
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.padding(top = 3.dp, bottom = 14.dp)
                    )
                }
                if (index == 0) HorizontalDivider(color = RunkkColors.Border)
            }
        }
        SheetConfirm(onClick = { actions.onRepeat(picked) })
    }
}

private const val NOON = 12
private const val MINUTES_IN_HOUR = 60
private const val DAYS_IN_WEEK = 7
private const val PAST_DAY_ALPHA = 0.6f
