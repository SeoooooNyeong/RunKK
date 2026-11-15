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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
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
 * 요약 탭(G-14·G-33): 최근 10회 변화(계약 v1.2 8절), 주간 러닝 기록, 최근 10회 평균, 러닝 코멘트(FR-066). 빈도는 주간만 보인다(주간/월간
 * 토글 없음, D-649). 코멘트가 없으면 "코멘트 준비 중", 출처는 표시하지 않는다.
 */
@Composable
internal fun SummaryTab(state: RunBodyUiState, actions: RunBodyActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val recent = state.trend?.recent
        RunBodyLoadable(
            isLoading = state.isTrendLoading && state.trend == null,
            error = state.trendError?.asString(),
            onRetry = actions.onRetryTrend
        ) {
            state.trend?.let { TrendCard(it) }
        }
        WeeklyRecordCard(state, actions)
        if (recent != null) RecentAverageCards(recent)
        RunBodyLoadable(
            isLoading = state.isReportLoading && state.report == null,
            error = state.reportError?.asString(),
            onRetry = actions.onRetryReport
        ) {
            CommentCard(state)
        }
    }
}

/** 최근 10회 변화(G-14). 10회 전이면 남은 횟수만, 1회차면 직전 값 없이 최근 10회만 보인다. */
@Composable
private fun TrendCard(trend: RunBodyTrend) {
    val recent = trend.recent
    if (trend.round == 0 || recent == null) {
        RunBodyCard(stringResource(R.string.record_runbody_trend_title)) {
            RunBodyCaption(stringResource(R.string.record_runbody_trend_needs, trend.needs))
        }
        return
    }
    val previous = trend.previous
    RunBodyCard(null, spacing = 18.dp) {
        TrendHeader(trend.round, recent)
        PacePanel(recent.pace, previous?.pace, trend.paceChange)
        if (previous != null) {
            SameHeartRateRow(trend.sameHeartRatePaceChange)
        }
        FrequencyRow(recent, previous)
        trend.runimalType?.let { RunimalTypeBox(it) }
    }
}

@Composable
private fun TrendHeader(round: Int, recent: RunBodyTrend.Window) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.record_runbody_trend_title),
                style = RunkkTextStyles.text(20, FontWeight.Bold, lineHeight = 26),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.record_runbody_trend_range, recent.fromOrdinal, recent.toOrdinal),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextSecondary
            )
        }
        Text(
            stringResource(R.string.record_runbody_trend_round, round),
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = RunkkColors.Violet,
            modifier = Modifier
                .clip(RunkkShapes.Pill)
                .background(RunkkColors.SecondaryContainer)
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
            RunkkFormat.paceSpoken(previous),
            RunkkFormat.paceSpoken(recent),
            paceChangeText(change ?: 0)
        )
    } else {
        stringResource(R.string.record_runbody_trend_pace_recent_spoken, RunkkFormat.paceSpoken(recent))
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
            if (previous != null && change != null) PaceChangeChip(change)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (previous != null) {
                Column {
                    Text(
                        stringResource(R.string.record_runbody_trend_previous),
                        style = RunkkTextStyles.text(12, FontWeight.Medium),
                        color = RunkkColors.TextSecondary
                    )
                    Text(
                        runBodyPace(previous),
                        style = RunkkTextStyles.display(22, letterSpacingEm = -0.01f),
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
                Row {
                    Text(
                        runBodyPace(recent),
                        style = RunkkTextStyles.display(40, letterSpacingEm = -0.02f, lineHeightRatio = 1.1f),
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.alignByBaseline()
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        stringResource(R.string.record_runbody_trend_per_km),
                        style = RunkkTextStyles.text(14, FontWeight.Medium),
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
        }
        if (previous != null) PaceAxis(previous, recent)
    }
}

/** 같은 축(왼쪽 느림, 오른쪽 빠름) 위의 직전·최근 페이스. 축 끝은 [paceAxisRange]. */
@Composable
private fun PaceAxis(previous: Pace, recent: Pace) {
    val (slowest, fastest) = paceAxisRange(previous, recent)
    val track = RunkkColors.SurfaceHigh
    val fill = RunkkColors.LimeFill
    val ring = RunkkColors.Violet
    val muted = RunkkColors.TextSecondary
    val surface = RunkkColors.Surface
    val tick = RunkkTextStyles.text(11, FontWeight.Medium)
    // 피그마: 트랙 4(끝 둥글게), 단축 구간 6(끝 각짐), 직전 점 지름 14(흰 바탕 + 보조색 테 2), 최근 점 지름 20(라임 + 보라 테 3)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val y = size.height / 2
            fun x(pace: Pace): Float = size.width * (slowest - pace.secondsPerKm) / (slowest - fastest).toFloat()
            drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            val from = x(previous)
            val to = x(recent)
            drawLine(fill, Offset(from, y), Offset(to, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Butt)
            drawCircle(surface, radius = 7.dp.toPx(), center = Offset(from, y))
            drawCircle(muted, radius = 6.dp.toPx(), center = Offset(from, y), style = Stroke(2.dp.toPx()))
            drawCircle(fill, radius = 10.dp.toPx(), center = Offset(to, y))
            drawCircle(ring, radius = 8.5.dp.toPx(), center = Offset(to, y), style = Stroke(3.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(runBodyPace(Pace(slowest)), style = tick, color = muted, modifier = Modifier.weight(1f))
            Text(runBodyPace(Pace(fastest)), style = tick, color = muted)
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

/** 주간 러닝 빈도(회/주). 서버 runsPerMonth는 계약에 남아 있으나 읽지 않는다(D-649). */
@Composable
private fun FrequencyRow(recent: RunBodyTrend.Window, previous: RunBodyTrend.Window?) {
    val label = stringResource(R.string.record_runbody_trend_weekly)
    val now = recent.runsPerWeek
    if (previous == null) {
        TrendRow(
            RunkkIcons.CalendarToday,
            label,
            stringResource(R.string.record_runbody_trend_freq_value, one(now)),
            null
        )
        return
    }
    val before = previous.runsPerWeek
    val delta = ((now - before) * TENTHS).roundToInt()
    TrendRow(
        RunkkIcons.CalendarToday,
        label,
        stringResource(R.string.record_runbody_trend_freq_change, one(before), one(now)),
        when {
            delta > 0 -> stringResource(R.string.record_runbody_trend_freq_more, one(delta / TENTHS))
            delta < 0 -> stringResource(R.string.record_runbody_trend_freq_less, one(-delta / TENTHS))
            else -> stringResource(R.string.record_runbody_trend_unchanged)
        }
    )
}

/**
 * 보라 컨테이너 원(36) 안 아이콘(20) + 이름(15 Medium) + 오른쪽 값(17 Bold)과 그 아래 작은 보라 설명(12 Bold). 보조 기술은 한 줄로
 * 읽는다.
 */
@Composable
private fun TrendRow(icon: ImageVector, label: String, value: String, note: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(RunkkColors.SecondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.Violet, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = RunkkTextStyles.text(15, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(value, style = RunkkTextStyles.text(17, FontWeight.Bold), color = RunkkColors.TextPrimary)
            note?.let {
                Text(it, style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.Violet)
            }
        }
    }
}

/**
 * 나의 러닝 유형(피그마 바탕 accent container, 모서리 16): 흰 원(안쪽 2) 안 러니멀 그림 44 + 이름. 피그마는 얼굴 그림이지만 러니멀 그림을
 * 그대로 쓴다(사용자 결정 B2, D-650).
 */
@Composable
private fun RunimalTypeBox(type: String) {
    val art = RunimalArt.of(type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TYPE_BOX_RADIUS))
            .background(RunkkColors.AccentContainer)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painterResource(art.drawableRes),
            contentDescription = null,
            modifier = Modifier.clip(CircleShape).background(RunkkColors.Surface).padding(2.dp).size(44.dp)
        )
        Column {
            Text(
                stringResource(R.string.record_runbody_trend_type),
                style = RunkkTextStyles.text(12, FontWeight.Medium),
                color = RunkkColors.TextSecondary
            )
            Text(
                stringResource(art.nameRes),
                style = RunkkTextStyles.text(17, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
        }
    }
}

/**
 * 평균 페이스 변화 칩(라임 알약): 빨라졌으면 trending_down 16 + 3 + "14초 단축", 느려졌으면 trending_up, 같으면 글자만. 아이콘은 장식이다.
 * 피그마 "Icon / 빨라짐"(trending_down)은 앱 아이콘에 없어 [RunkkIcons.TrendingUp]을 위아래로 뒤집어 그린다.
 */
@Composable
private fun PaceChangeChip(change: Int) {
    Row(
        modifier = Modifier
            .clip(RunkkShapes.Pill)
            .background(RunkkColors.LimeFill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (change != 0) {
            Icon(
                RunkkIcons.TrendingUp,
                contentDescription = null,
                tint = RunkkColors.OnLimeFill,
                modifier = Modifier.size(16.dp).graphicsLayer { if (change > 0) scaleY = -1f }
            )
        }
        Text(paceChangeText(change), style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.OnLimeFill)
    }
}

/** 페이스 변화 칩 문구: 양수면 "14초 단축", 음수면 "9초 늘어남", 0이면 "변화 없음". */
@Composable
private fun paceChangeText(change: Int): String = when {
    change > 0 -> stringResource(R.string.record_runbody_trend_faster, change)
    change < 0 -> stringResource(R.string.record_runbody_trend_slower, -change)
    else -> stringResource(R.string.record_runbody_trend_unchanged)
}

/** 소수 첫째 자리(2.8). */
private fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * 페이스 축 끝(느린 쪽, 빠른 쪽, 초/km): 두 값을 감싸는 가장 가까운 30초 눈금(피그마 6'02"·5'48" → 6'30"~5'30"). 값이 눈금에 딱 맞으면
 * 점이 축 끝에 붙지 않게 한 칸 바깥으로 둔다.
 */
internal fun paceAxisRange(previous: Pace, recent: Pace): Pair<Int, Int> {
    val slowest = maxOf(previous.secondsPerKm, recent.secondsPerKm)
    val fastest = minOf(previous.secondsPerKm, recent.secondsPerKm)
    val top = (slowest / AXIS_STEP + 1) * AXIS_STEP
    val bottom = ((fastest - 1).coerceAtLeast(0) / AXIS_STEP * AXIS_STEP).coerceAtLeast(AXIS_STEP)
    return top to minOf(bottom, top - AXIS_STEP)
}

private const val AXIS_STEP = 30
private const val TENTHS = 10.0
private const val NO_VALUE = "-"
private val PANEL_RADIUS = 20.dp
private val TYPE_BOX_RADIUS = 16.dp
