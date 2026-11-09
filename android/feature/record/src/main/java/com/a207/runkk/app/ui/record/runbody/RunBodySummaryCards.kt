package com.a207.runkk.app.ui.record.runbody

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.runbody.RunBodyTrend
import com.a207.runkk.app.ui.record.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * 요약 주간 러닝 기록(G-14): 제목과 주 넘기기(‹ 10월 3주 ›), 이번 주 거리 합계와 횟수·시간, 월~일 원(달린 날은 라임으로 채우고 거리를 적는다).
 */
@Composable
internal fun WeeklyRecordCard(state: RunBodyUiState, actions: RunBodyActions) {
    RunBodyCard(null, spacing = 18.dp) {
        WeekHeader(state, actions)
        RunBodyLoadable(
            isLoading = state.isWeekLoading && state.weekRuns == null,
            error = state.weekError?.asString(),
            onRetry = actions.onRetryWeek
        ) {
            state.weekRecord?.let { WeekBody(it) }
        }
    }
}

@Composable
private fun WeekHeader(state: RunBodyUiState, actions: RunBodyActions) {
    val (month, week) = weekOfMonth(state.week)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.record_runbody_weekly_title),
            style = RunkkTextStyles.text(18, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        WeekArrow(
            RunkkIcons.ArrowBack,
            stringResource(R.string.record_runbody_week_previous),
            state.canGoPreviousWeek,
            actions.onPreviousWeek
        )
        Text(
            stringResource(R.string.record_runbody_week_label, month, week),
            style = RunkkTextStyles.text(14, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        WeekArrow(
            RunkkIcons.ArrowBack,
            stringResource(R.string.record_runbody_week_next),
            state.canGoNextWeek,
            actions.onNextWeek,
            isMirrored = true
        )
    }
}

/** 주 넘기기 화살표(피그마 arrow_back·arrow_forward 24, 누름 칸 48). arrow_forward는 앱 아이콘에 없어 [isMirrored]로 ←를 뒤집는다. */
@Composable
private fun WeekArrow(
    icon: ImageVector,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    isMirrored: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = isEnabled, onClickLabel = label, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (isEnabled) 1f else DISABLED_ALPHA },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(24.dp).graphicsLayer { if (isMirrored) scaleX = -1f }
        )
    }
}

@Composable
private fun WeekBody(week: RunBodyWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    RunkkFormat.kmShort(week.distance),
                    style = RunkkTextStyles.text(40, FontWeight.Bold, lineHeight = 44, letterSpacingEm = -0.02f),
                    color = RunkkColors.TextPrimary
                )
                Text(
                    stringResource(R.string.record_runbody_unit_km),
                    style = RunkkTextStyles.text(15, FontWeight.Medium),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekChip(RunkkIcons.DirectionsRun, stringResource(R.string.record_runbody_week_runs, week.runs))
                WeekChip(RunkkIcons.Schedule, RunkkFormat.duration(week.elapsed))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.days.forEachIndexed { index, distance ->
                DayCell(DayOfWeek.MONDAY.plus(index.toLong()), distance)
            }
        }
    }
}

@Composable
private fun WeekChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CHIP_RADIUS))
            .background(RunkkColors.SurfaceHigh)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = RunkkColors.TextPrimary, modifier = Modifier.size(16.dp))
        Text(text, style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.TextPrimary)
    }
}

/** 요일 이름 + 원(달린 날은 라임 채움에 거리, 쉰 날은 빈 원). 보조 기술은 "월요일 4.2km"처럼 한 번에 읽는다. */
@Composable
private fun DayCell(day: DayOfWeek, distance: Distance?) {
    val name = day.getDisplayName(TextStyle.NARROW, Locale.KOREAN)
    val km = distance?.let(::dayKm)
    val spoken = if (km != null) {
        stringResource(R.string.record_runbody_week_day_run, name, km)
    } else {
        stringResource(R.string.record_runbody_week_day_rest, name)
    }
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            name,
            style = RunkkTextStyles.text(12, FontWeight.Medium),
            color = if (km != null) RunkkColors.TextPrimary else RunkkColors.TextSecondary
        )
        Box(
            Modifier
                .size(DAY_SIZE)
                .clip(CircleShape)
                .background(if (km != null) RunkkColors.LimeFill else RunkkColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            km?.let {
                Text(it, style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.OnLimeFill)
            }
        }
    }
}

/** 요일 원의 거리: 소수 첫째 자리, 정수면 소수점 없이(5.0 → 5). */
private fun dayKm(distance: Distance): String = RunkkFormat.kmShort(distance).removeSuffix(".0")

/** 최근 10회 평균(G-14): 평균 거리·평균 심박 두 칸. 값이 없으면 "—"다. */
@Composable
internal fun RecentAverageCards(recent: RunBodyTrend.Window) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AverageTile(
            RunkkIcons.Route,
            stringResource(R.string.record_runbody_avg_distance),
            recent.averageDistance?.let(RunkkFormat::kmShort),
            stringResource(R.string.record_runbody_unit_km),
            Modifier.weight(1f)
        )
        AverageTile(
            RunkkIcons.Heart,
            stringResource(R.string.record_runbody_avg_heart),
            recent.averageHeartRate?.toString(),
            stringResource(R.string.record_runbody_unit_bpm),
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun AverageTile(icon: ImageVector, label: String, value: String?, unit: String, modifier: Modifier) {
    SummaryTile(modifier.semantics(mergeDescendants = true) {}) {
        TileHeader(icon, label, RunkkColors.TextSecondary, FontWeight.Medium)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                value ?: stringResource(R.string.record_runbody_no_score),
                style = RunkkTextStyles.text(26, FontWeight.Bold, lineHeight = 30, letterSpacingEm = -0.01f),
                color = RunkkColors.TextPrimary
            )
            if (value != null) {
                Text(
                    unit,
                    style = RunkkTextStyles.text(13, FontWeight.Medium),
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/** 러닝 코멘트(G-14·G-33): 말풍선 + 보라 제목, 본문(14 Regular 본문색). 코멘트가 없어도 같은 글자색으로 "코멘트 준비 중"(G-33). */
@Composable
internal fun CommentCard(state: RunBodyUiState) {
    SummaryTile(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, spacing = 8.dp) {
        TileHeader(
            RunkkIcons.ChatBubble,
            stringResource(R.string.record_report_comment_title),
            RunkkColors.Violet,
            FontWeight.Bold
        )
        Text(
            state.commentText.asString(),
            style = RunkkTextStyles.text(14, lineHeight = 21),
            color = RunkkColors.TextPrimary
        )
    }
}

@Composable
private fun TileHeader(icon: ImageVector, label: String, labelColor: Color, weight: FontWeight) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = RunkkColors.Violet, modifier = Modifier.size(18.dp))
        Text(label, style = RunkkTextStyles.text(13, weight), color = labelColor)
    }
}

/** 요약 작은 카드(모서리 20, 안쪽 16). */
@Composable
private fun SummaryTile(modifier: Modifier, spacing: Dp = 6.dp, content: @Composable ColumnScope.() -> Unit) {
    RunkkCard(
        modifier,
        shape = RunkkShapes.Large,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

private const val DISABLED_ALPHA = 0.3f
private val DAY_SIZE = 40.dp
private val CHIP_RADIUS = 999.dp
