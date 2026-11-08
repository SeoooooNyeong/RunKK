package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.runbody.RunBodyTrend
import com.a207.runkk.app.ui.record.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 요약 탭(G-14·G-33): 주간/월간 토글, 최근 10회 변화(계약 v1.2 8절), 이번 주 요약과 코치 코멘트(FR-066). 토글은 빈도 줄만 바꾼다.
 * 코멘트가 없으면 "코멘트 준비 중", 출처는 표시하지 않는다.
 */
@Composable
internal fun SummaryTab(state: RunBodyUiState, actions: RunBodyActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkSegmentedTabs(
            options = RunBodyPeriod.entries.map { stringResource(it.labelRes) },
            selectedIndex = state.period.ordinal,
            onSelect = { actions.onPeriod(RunBodyPeriod.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        RunBodyLoadable(
            isLoading = state.isTrendLoading && state.trend == null,
            error = state.trendError?.asString(),
            onRetry = actions.onRetryTrend
        ) {
            state.trend?.let { TrendCard(it, state.period) }
        }
        RunBodyLoadable(
            isLoading = state.isReportLoading && state.report == null,
            error = state.reportError?.asString(),
            onRetry = actions.onRetryReport
        ) {
            ReportCards(state)
        }
    }
}

@Composable
private fun ReportCards(state: RunBodyUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkCard(Modifier.fillMaxWidth(), shape = RunkkShapes.ExtraLarge) {
            Text(
                stringResource(R.string.record_report_summary_title),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(state.summaryText.asString(), style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
        }
        val hasComment = state.report?.comment != null
        RunkkCard(
            Modifier.fillMaxWidth(),
            shape = RunkkShapes.ExtraLarge,
            borderColor = if (hasComment) RunkkColors.Lime else RunkkColors.Border
        ) {
            Text(
                stringResource(R.string.record_report_comment_title),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(
                state.commentText.asString(),
                style = RunkkTextStyles.text(16, FontWeight.Bold, lineHeight = 24),
                color = if (hasComment) RunkkColors.TextPrimary else RunkkColors.TextSecondary
            )
        }
    }
}

/** 최근 10회 변화(G-14). 10회 전이면 남은 횟수만, 1회차면 직전 값 없이 최근 10회만 보인다. */
@Composable
private fun TrendCard(trend: RunBodyTrend, period: RunBodyPeriod) {
    val recent = trend.recent
    if (trend.round == 0 || recent == null) {
        RunBodyCard(stringResource(R.string.record_runbody_trend_title)) {
            RunBodyCaption(stringResource(R.string.record_runbody_trend_needs, trend.needs))
        }
        return
    }
    val previous = trend.previous
    RunBodyCard(null) {
        TrendHeader(trend.round, recent)
        PacePanel(recent.pace, previous?.pace, trend.paceChange)
        if (previous != null) {
            SameHeartRateRow(trend.sameHeartRatePaceChange)
        }
        FrequencyRow(period, recent, previous)
        trend.runimalType?.let { RunimalTypeBox(it) }
    }
}

@Composable
private fun TrendHeader(round: Int, recent: RunBodyTrend.Window) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.record_runbody_trend_title),
                style = RunkkTextStyles.text(20, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            RunBodyCaption(stringResource(R.string.record_runbody_trend_range, recent.fromOrdinal, recent.toOrdinal))
        }
        Text(
            stringResource(R.string.record_runbody_trend_round, round),
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = RunkkColors.Violet,
            modifier = Modifier
                .clip(RoundedCornerShape(CHIP_RADIUS))
                .background(RunkkColors.Lavender.copy(alpha = LAVENDER_ALPHA))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/** 평균 페이스: 변화 칩, 직전 10회 → 최근 10회, 같은 축 위 두 점(왼쪽이 느림). 직전이 없으면 최근 값만. */
@Composable
private fun PacePanel(recent: Pace, previous: Pace?, change: Int?) {
    val spoken = if (previous != null) {
        stringResource(
            R.string.record_runbody_trend_pace_spoken,
            runBodyPace(previous),
            runBodyPace(recent),
            paceChangeText(change ?: 0)
        )
    } else {
        stringResource(R.string.record_runbody_trend_pace_recent_spoken, runBodyPace(recent))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PANEL_RADIUS))
            .background(RunkkColors.Background)
            .padding(16.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.record_runbody_trend_pace),
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (previous != null && change != null) {
                Text(
                    paceChangeText(change),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.OnLimeFill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CHIP_RADIUS))
                        .background(RunkkColors.LimeFill)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (previous != null) {
                Column {
                    RunBodyCaption(stringResource(R.string.record_runbody_trend_previous))
                    Text(
                        runBodyPace(previous),
                        style = RunkkTextStyles.display(24),
                        color = RunkkColors.TextSecondary
                    )
                }
                Icon(
                    RunkkIcons.ArrowRight,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp).size(20.dp)
                )
            }
            Column {
                Text(
                    stringResource(R.string.record_runbody_trend_recent),
                    style = RunkkTextStyles.text(12, FontWeight.Bold),
                    color = RunkkColors.Violet
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(runBodyPace(recent), style = RunkkTextStyles.display(44), color = RunkkColors.TextPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.record_runbody_trend_per_km),
                        style = RunkkTextStyles.Body,
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
        if (previous != null) PaceAxis(previous, recent)
    }
}

/** 같은 축(왼쪽 느림, 오른쪽 빠름) 위의 직전·최근 페이스. 축 끝은 두 값에서 30초 바깥을 30초 단위로 맞춘다. */
@Composable
private fun PaceAxis(previous: Pace, recent: Pace) {
    val slowest = roundUp(maxOf(previous.secondsPerKm, recent.secondsPerKm) + AXIS_MARGIN)
    val fastest = roundDown(minOf(previous.secondsPerKm, recent.secondsPerKm) - AXIS_MARGIN).coerceAtLeast(AXIS_STEP)
    val track = RunkkColors.Border
    val fill = RunkkColors.LimeFill
    val ring = RunkkColors.Violet
    val muted = RunkkColors.TextSecondary
    val surface = RunkkColors.Surface
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val y = size.height / 2
            fun x(pace: Pace): Float = size.width * (slowest - pace.secondsPerKm) / (slowest - fastest).toFloat()
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val from = x(previous)
            val to = x(recent)
            drawLine(fill, Offset(from, y), Offset(to, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(from, y))
            drawCircle(muted, radius = 6.dp.toPx(), center = Offset(from, y), style = Stroke(2.dp.toPx()))
            drawCircle(fill, radius = 8.dp.toPx(), center = Offset(to, y))
            drawCircle(ring, radius = 9.dp.toPx(), center = Offset(to, y), style = Stroke(3.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                runBodyPace(Pace(slowest)),
                style = RunkkTextStyles.Caption,
                color = muted,
                modifier = Modifier.weight(1f)
            )
            Text(runBodyPace(Pace(fastest)), style = RunkkTextStyles.Caption, color = muted)
        }
    }
}

@Composable
private fun SameHeartRateRow(change: Int?) {
    if (change == null) {
        TrendRow(
            RunkkIcons.Bolt,
            stringResource(R.string.record_runbody_trend_same_hr),
            NO_VALUE,
            stringResource(R.string.record_runbody_trend_same_hr_none)
        )
        return
    }
    TrendRow(
        RunkkIcons.Bolt,
        stringResource(R.string.record_runbody_trend_same_hr),
        stringResource(R.string.record_runbody_trend_same_hr_value, abs(change)),
        stringResource(
            when {
                change > 0 -> R.string.record_runbody_trend_shorter
                change < 0 -> R.string.record_runbody_trend_longer
                else -> R.string.record_runbody_trend_unchanged
            }
        )
    )
}

@Composable
private fun FrequencyRow(period: RunBodyPeriod, recent: RunBodyTrend.Window, previous: RunBodyTrend.Window?) {
    val label = stringResource(
        if (period ==
            RunBodyPeriod.WEEK
        ) {
            R.string.record_runbody_trend_weekly
        } else {
            R.string.record_runbody_trend_monthly
        }
    )
    val now = frequencyOf(recent, period)
    if (previous == null) {
        TrendRow(RunkkIcons.Calendar, label, stringResource(R.string.record_runbody_trend_freq_value, one(now)), null)
        return
    }
    val before = frequencyOf(previous, period)
    val delta = ((now - before) * TENTHS).roundToInt()
    TrendRow(
        RunkkIcons.Calendar,
        label,
        stringResource(R.string.record_runbody_trend_freq_change, one(before), one(now)),
        when {
            delta > 0 -> stringResource(R.string.record_runbody_trend_freq_more, one(delta / TENTHS))
            delta < 0 -> stringResource(R.string.record_runbody_trend_freq_less, one(-delta / TENTHS))
            else -> stringResource(R.string.record_runbody_trend_unchanged)
        }
    )
}

/** 라벤더 원 아이콘 + 이름 + 오른쪽 값(굵게)과 그 아래 작은 보라 설명. 보조 기술은 한 줄로 읽는다. */
@Composable
private fun TrendRow(icon: ImageVector, label: String, value: String, note: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.Lavender.copy(alpha = LAVENDER_ALPHA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.Violet, modifier = Modifier.size(18.dp))
        }
        Text(label, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = RunkkTextStyles.text(18, FontWeight.Black), color = RunkkColors.TextPrimary)
            note?.let {
                Text(it, style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.Violet)
            }
        }
    }
}

/** 나의 러닝 유형: 러니멀 그림 + 이름(서버 RunimalType 이름을 RunimalArt로). */
@Composable
private fun RunimalTypeBox(type: String) {
    val art = RunimalArt.of(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PANEL_RADIUS))
            .background(RunkkColors.Lavender.copy(alpha = LAVENDER_ALPHA))
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(art.drawableRes),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(RunkkColors.Surface)
        )
        Column {
            Text(
                stringResource(R.string.record_runbody_trend_type),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Text(
                stringResource(art.nameRes),
                style = RunkkTextStyles.text(18, FontWeight.Black),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/** 페이스 변화 칩 문구: 양수면 "14초 단축", 음수면 "9초 늘어남", 0이면 "변화 없음". */
@Composable
private fun paceChangeText(change: Int): String = when {
    change > 0 -> stringResource(R.string.record_runbody_trend_faster, change)
    change < 0 -> stringResource(R.string.record_runbody_trend_slower, -change)
    else -> stringResource(R.string.record_runbody_trend_unchanged)
}

private fun frequencyOf(window: RunBodyTrend.Window, period: RunBodyPeriod): Double =
    if (period == RunBodyPeriod.WEEK) window.runsPerWeek else window.runsPerMonth

/** 소수 첫째 자리(2.8). */
private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun roundUp(seconds: Int): Int = (seconds + AXIS_STEP - 1) / AXIS_STEP * AXIS_STEP

private fun roundDown(seconds: Int): Int = seconds / AXIS_STEP * AXIS_STEP

private const val AXIS_MARGIN = 30
private const val AXIS_STEP = 30
private const val TENTHS = 10.0
private const val LAVENDER_ALPHA = 0.3f
private const val NO_VALUE = "-"
private val CHIP_RADIUS = 14.dp
private val PANEL_RADIUS = 20.dp
