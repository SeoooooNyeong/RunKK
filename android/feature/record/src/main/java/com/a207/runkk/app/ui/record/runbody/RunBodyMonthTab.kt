package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.runbody.RunBodyMonth
import com.a207.runkk.app.ui.record.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 월간 탭(G-22·G-23·G-32, FR-267): 달 넘기기(첫 러닝 달부터 이번 달까지), 월간 축 평균, 개인 기준선, 훈련 부하, 개인 최고, 주간 거리. 분석된
 * 러닝이 없는 달은 축·최고·주간 카드 대신 "이번 달의 러닝을 아직 분석하지 않았어요"를 보이고, 기준선·부하는 각자 상태대로 보인다. 지난 달은
 * "이번 달" 대신 "이 달"이라 부른다.
 */
@Composable
internal fun MonthTab(state: RunBodyUiState, actions: RunBodyActions) {
    MonthHeader(state, actions)
    RunBodyLoadable(
        isLoading = state.isMonthLoading && state.monthData == null,
        error = state.monthError?.asString(),
        onRetry = actions.onRetryMonth
    ) {
        val month = state.monthData ?: return@RunBodyLoadable
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (month.sessionCount > 0 &&
                month.averages.isNotEmpty()
            ) {
                AxesCard(month)
            } else {
                EmptyMonthCard(
                    state.isCurrentMonth,
                    actions.onPreviousMonth.takeIf { state.canGoPreviousMonth }
                )
            }
            BaselineCard(month.baseline)
            LoadCard(month.load, loadBasisDate(month.asOf, state.zone, state.today))
            if (month.sessionCount > 0) {
                BestsCard(month.bests, state.isCurrentMonth)
                WeeksCard(month)
            }
        }
    }
}

@Composable
private fun MonthHeader(state: RunBodyUiState, actions: RunBodyActions) {
    val label = DateTimeFormatter.ofPattern(
        stringResource(R.string.record_runbody_month_label),
        Locale.KOREAN
    ).format(state.month)
    // 피그마 "‹  2026년 10월  ›"(16 Bold, 왼쪽 정렬). 화살표는 누름 칸 40을 두고 꺾쇠가 본문 왼쪽 끝에 오게 당긴다.
    Row(Modifier.offset(x = -ARROW_INSET), verticalAlignment = Alignment.CenterVertically) {
        MonthArrow(
            RunkkIcons.Back,
            stringResource(R.string.record_runbody_month_previous),
            isEnabled = state.canGoPreviousMonth,
            actions.onPreviousMonth
        )
        Text(label, style = RunkkTextStyles.text(16, FontWeight.Bold), color = RunkkColors.TextPrimary)
        MonthArrow(
            RunkkIcons.ChevronRight,
            stringResource(R.string.record_runbody_month_next),
            isEnabled = state.canGoNextMonth,
            actions.onNextMonth
        )
    }
}

@Composable
private fun MonthArrow(icon: ImageVector, label: String, isEnabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clickable(enabled = isEnabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .alpha(if (isEnabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = RunkkColors.TextPrimary, modifier = Modifier.size(16.dp))
    }
}

/** 월간 러닝 분석(간격 14): 점수가 있는 축마다 이름(13 Medium)·점수(17 Bold)·막대(6, 트랙 surfaceHigh). 가장 높은 축은 라임, 나머지는 보라. */
@Composable
private fun AxesCard(month: RunBodyMonth) {
    val best = month.averages.maxByOrNull { it.value }?.key
    RunBodyCard(
        stringResource(R.string.record_runbody_month_title),
        titleSize = PROFILE_TITLE_SIZE,
        spacing = 14.dp
    ) {
        RunBodyCaption(stringResource(R.string.record_runbody_month_caption))
        month.averages.forEach { (axis, average) ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row {
                    Text(
                        stringResource(axis.labelRes()),
                        style = RunkkTextStyles.text(13, FontWeight.Medium),
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                    Text(
                        stringResource(R.string.record_runbody_points, average),
                        style = RunkkTextStyles.text(17, FontWeight.Bold),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                RunkkProgressBar(
                    average / MAX_SCORE,
                    height = BAR_HEIGHT,
                    color = if (axis == best) RunkkColors.LimeFill else RunkkColors.Violet,
                    trackColor = RunkkColors.SurfaceHigh
                )
            }
        }
    }
}

/** G-32: 이 달에 분석된 러닝이 없다. "이전 달 보기"로 한 달 앞으로 간다. 첫 러닝 달이면([onPreviousMonth]가 null) 단추를 그리지 않는다. */
@Composable
private fun EmptyMonthCard(isCurrentMonth: Boolean, onPreviousMonth: (() -> Unit)?) {
    val title = if (isCurrentMonth) {
        R.string.record_runbody_month_empty_title
    } else {
        R.string.record_runbody_month_empty_title_past
    }
    RunBodyCard(stringResource(title)) {
        RunBodyCaption(stringResource(R.string.record_runbody_month_empty_message))
        onPreviousMonth?.let {
            RunkkPrimaryButton(
                stringResource(R.string.record_runbody_month_empty_action),
                onClick = it,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BaselineCard(baseline: RunBodyMonth.Baseline) {
    RunBodyCard(stringResource(R.string.record_runbody_baseline)) {
        if (baseline.isAvailable) {
            RunBodyCaption(stringResource(R.string.record_runbody_baseline_caption, baseline.sessionCount))
            baseline.pace?.let {
                RunBodyValueRow(
                    stringResource(R.string.record_stat_avg_pace),
                    stringResource(R.string.record_runbody_pace_per_km, runBodyPace(it))
                )
            }
            baseline.averageHeartRate?.let {
                RunBodyValueRow(
                    stringResource(R.string.record_runbody_avg_heart),
                    stringResource(R.string.record_runbody_bpm, it)
                )
            }
        } else {
            RunBodyCaption(stringResource(R.string.record_runbody_baseline_pending))
            RunBodyCaption(
                stringResource(
                    R.string.record_runbody_baseline_needs,
                    baseline.sessionCount,
                    RunBodyMonth.BASELINE_RUNS,
                    baseline.needs
                )
            )
        }
    }
}

/**
 * 훈련 부하. 준비 중이면 첫 러닝 뒤 지난 날 수(켜지는 날 − 28일부터)를 [basis](그 달을 셈한 날, [loadBasisDate]) 기준으로 보인다.
 */
@Composable
private fun LoadCard(load: RunBodyMonth.Load, basis: LocalDate) {
    RunBodyCard(stringResource(R.string.record_runbody_load)) {
        val ratio = load.ratio
        val level = load.level
        if (load.isAvailable && ratio != null && level != null) {
            RunBodyValueRow(stringResource(R.string.record_runbody_load_acute), kmSpaced(load.acute))
            RunBodyValueRow(stringResource(R.string.record_runbody_load_chronic), kmSpaced(load.chronicWeekly))
            RunBodyValueRow(
                stringResource(R.string.record_runbody_load_ratio),
                stringResource(
                    R.string.record_runbody_load_ratio_value,
                    runBodyRatio(ratio),
                    stringResource(level.labelRes())
                )
            )
            RunBodyCaption(stringResource(R.string.record_runbody_load_caption))
        } else {
            RunBodyCaption(stringResource(R.string.record_runbody_load_pending))
            load.availableFrom?.let { from ->
                RunBodyCaption(
                    stringResource(
                        R.string.record_runbody_load_days,
                        loadDaysPassed(from, basis),
                        RunBodyMonth.LOAD_DAYS
                    )
                )
            }
            RunBodyCaption(stringResource(R.string.record_runbody_load_pending_message))
        }
    }
}

/** 부하 지난 날 수의 기준일: ai가 그 달을 셈한 시점(asOf, 지난달이면 월말)의 기기 날짜, 모르면 오늘(MR !91 e2e 6). */
internal fun loadBasisDate(asOf: Instant?, zone: ZoneId, today: LocalDate): LocalDate =
    asOf?.atZone(zone)?.toLocalDate() ?: today

/** 부하가 켜지는 날 [from]까지 [basis] 기준으로 지난 날 수(28 − 남은 날, 0~28). */
internal fun loadDaysPassed(from: LocalDate, basis: LocalDate): Int {
    val left = ChronoUnit.DAYS.between(basis, from)
    return (RunBodyMonth.LOAD_DAYS - left).coerceIn(0, RunBodyMonth.LOAD_DAYS.toLong()).toInt()
}

/** 개인 최고(이번 달/이 달): 있는 항목만. 하나도 없으면 카드를 그리지 않는다. */
@Composable
private fun BestsCard(bests: RunBodyMonth.Bests, isCurrentMonth: Boolean) {
    if (bests.fastestKm == null && bests.fastest2Km == null && bests.longest == null &&
        bests.recoveryHeartRate1m == null
    ) {
        return
    }
    RunBodyCard(
        stringResource(if (isCurrentMonth) R.string.record_runbody_bests else R.string.record_runbody_bests_past)
    ) {
        bests.fastestKm?.let { RunBodyValueRow(stringResource(R.string.record_runbody_best_1km), runBodyShortTime(it)) }
        bests.fastest2Km?.let {
            RunBodyValueRow(stringResource(R.string.record_runbody_best_2km), runBodyShortTime(it))
        }
        bests.longest?.let {
            RunBodyValueRow(
                stringResource(R.string.record_runbody_best_longest),
                stringResource(R.string.record_km_value, RunkkFormat.kmShort(it))
            )
        }
        bests.recoveryHeartRate1m?.let {
            RunBodyValueRow(
                stringResource(R.string.record_runbody_best_recovery),
                stringResource(R.string.record_runbody_bpm_signed, runBodySigned(it))
            )
        }
    }
}

/** 주간 거리(1주부터, 주마다 같은 축의 막대, 가장 긴 주만 라임) + 밤에 달린 비율. */
@Composable
private fun WeeksCard(month: RunBodyMonth) {
    val meters = month.weeks.map { it.distance.meters }
    val axis = weekAxisMeters(meters.maxOrNull() ?: 0L)
    val longest = longestWeekIndex(meters)
    RunBodyCard(stringResource(R.string.record_runbody_weeks)) {
        month.weeks.forEachIndexed { index, week ->
            WeekBarRow(
                stringResource(R.string.record_runbody_week, index + 1),
                week.distance,
                axis,
                isLongest = index == longest
            )
        }
        val total = month.nightRuns + month.dayRuns
        if (total > 0) {
            RunBodyValueRow(
                stringResource(R.string.record_runbody_night_share),
                stringResource(
                    R.string.record_runbody_percent,
                    (month.nightRuns * PERCENT / total.toFloat()).roundToInt()
                )
            )
        }
    }
}

/** 주 이름(1주) + 공통 축 막대(트랙 surfaceHigh, 가장 긴 주는 라임) + 거리(칸 54). */
@Composable
private fun WeekBarRow(label: String, distance: Distance, axisMeters: Long, isLongest: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = RunkkTextStyles.text(13, FontWeight.Medium), color = RunkkColors.TextSecondary)
        RunkkProgressBar(
            distance.meters / axisMeters.toFloat(),
            modifier = Modifier.weight(1f),
            height = BAR_HEIGHT,
            color = if (isLongest) RunkkColors.LimeFill else RunkkColors.Violet,
            trackColor = RunkkColors.SurfaceHigh
        )
        Text(
            km(distance),
            style = RunkkTextStyles.text(15, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(WEEK_VALUE_WIDTH)
        )
    }
}

/** 주간 거리 막대의 공통 축 끝(m): 가장 긴 주를 5km 단위로 올린다(피그마 20.2km → 25km). 최소 5km. */
internal fun weekAxisMeters(longestMeters: Long): Long {
    val step = WEEK_AXIS_STEP_METERS
    return ((longestMeters + step - 1) / step * step).coerceAtLeast(step)
}

@Composable
private fun km(distance: Distance): String = stringResource(R.string.record_km_value, RunkkFormat.kmShort(distance))

/** 훈련 부하 거리는 피그마대로 숫자와 km를 띄운다(18.4 km). */
@Composable
private fun kmSpaced(distance: Distance): String =
    stringResource(R.string.record_runbody_km_spaced, RunkkFormat.kmShort(distance))

private const val MAX_SCORE = 100f
private const val PERCENT = 100
private const val DISABLED_ALPHA = 0.3f
private const val WEEK_AXIS_STEP_METERS = 5_000L
private val WEEK_VALUE_WIDTH = 54.dp
private val BAR_HEIGHT = 6.dp
private val ARROW_INSET = 12.dp
