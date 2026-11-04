package com.a207.runkk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.splitPace
import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.domain.record.RunSummary
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 접힌 「내 기록 요약 ⌄」 알약(피그마 61338:2049): 표면, r999, pl16 pr10 py8, 13 Medium + 아래 꺾쇠. 누르면 패널을 펼친다.
 */
@Composable
internal fun SummaryToggle(onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val collapsed = stringResource(R.string.home_summary_state_collapsed)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .shadow(TOGGLE_SHADOW, CircleShape, ambientColor = SHADOW, spotColor = SHADOW)
            .clip(CircleShape)
            .background(RunkkColors.Surface)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = collapsed }
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.home_summary_title),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextPrimary
        )
        Icon(
            RunkkIcons.ChevronDown,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 펼친 「내 기록 요약」 패널(피그마 61343:2552, 블러 없음): 머리(제목·「접기 ⌃」), 오늘·이번 달 두 칸, 최근 러닝 한 줄,
 * 러닝 잔디 5주×7일. 표면, 1 Border, r24, p16, 세로 12.
 */
@Composable
internal fun SummaryPanel(
    summary: HomeSummary?,
    onCollapse: () -> Unit,
    onLatestRun: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(PANEL_SHADOW, shape, ambientColor = SHADOW, spotColor = SHADOW)
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PanelHeader(onCollapse)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TodayCell(summary?.today, Modifier.weight(1f).fillMaxHeight())
            MonthCell(
                summary?.month ?: MonthSummary(com.a207.runkk.app.core.model.Distance.ZERO, 0),
                Modifier.weight(1f).fillMaxHeight()
            )
        }
        LatestRun(summary?.latestRun, onLatestRun)
        Grass(summary?.grass.orEmpty())
    }
}

@Composable
private fun PanelHeader(onCollapse: () -> Unit) {
    val expanded = stringResource(R.string.home_summary_state_expanded)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_summary_title),
            style = RunkkTextStyles.text(17, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Row(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onCollapse)
                .semantics { stateDescription = expanded }
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.home_summary_collapse),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextSecondary
            )
            Icon(
                RunkkIcons.ChevronUp,
                contentDescription = null,
                tint = RunkkColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 오늘 칸: 「오늘」 / 「3.20」 km / 「18분 40초, 1회」. 오늘 달리지 않았으면 「오늘은 아직 달리지 않았어요」. */
@Composable
private fun TodayCell(today: TodaySummary?, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_summary_today)
    if (today == null) {
        val none = stringResource(R.string.home_summary_today_none)
        SummaryCell(label, "$label, $none", modifier) {
            Text(
                none,
                style = RunkkTextStyles.text(14, FontWeight.Medium, lineHeight = 20),
                color = RunkkColors.TextPrimary
            )
        }
        return
    }
    val km = RunkkFormat.km(today.distance)
    val line = stringResource(R.string.home_summary_today_line, minutesSeconds(today.elapsed), today.runs)
    val spoken = stringResource(R.string.home_summary_km_spoken, km)
    SummaryCell(label, "$label, $spoken, $line", modifier) {
        KmValue(km)
        Text(line, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 이번 달 칸: 「이번 달」 / 「41.2」 km / 「러닝 9회」. */
@Composable
private fun MonthCell(month: MonthSummary, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_summary_month)
    val km = RunkkFormat.kmShort(month.distance)
    val runs = stringResource(R.string.home_summary_month_runs, month.runs)
    val spoken = stringResource(R.string.home_summary_km_spoken, km)
    SummaryCell(label, "$label, $spoken, $runs", modifier) {
        KmValue(km)
        Text(runs, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

@Composable
private fun SummaryCell(label: String, spoken: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Background)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = RunkkTextStyles.text(12, FontWeight.Medium), color = RunkkColors.TextSecondary)
        content()
    }
}

/** 큰 숫자 24 Bold + 「km」 12 Medium(기준선). */
@Composable
private fun KmValue(km: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            km,
            style = RunkkTextStyles.text(24, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            stringResource(R.string.home_summary_km),
            style = RunkkTextStyles.text(12, FontWeight.Medium),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.alignByBaseline()
        )
    }
}

/** 「최근 러닝」 제목과 한 줄(누르면 기록 상세). 기록이 없으면 안내 한 줄. */
@Composable
private fun LatestRun(run: RunSummary?, onLatestRun: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_summary_latest),
            style = RunkkTextStyles.text(13, FontWeight.Medium),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        if (run == null) {
            Text(
                stringResource(R.string.home_summary_latest_none),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RunkkColors.Background)
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            )
        } else {
            LatestRunRow(run, onClick = { onLatestRun(run.recordId) })
        }
    }
}

@Composable
private fun LatestRunRow(run: RunSummary, onClick: () -> Unit) {
    val kind = stringResource(kindLabelRes(run.cardKind))
    val date = latestDate(run)
    val km = RunkkFormat.km(run.distance)
    val time = RunkkFormat.duration(run.elapsed)
    val pace = run.averagePace?.let(::splitPace) ?: RunkkFormat.EMPTY
    val open = stringResource(R.string.home_summary_latest_open)
    val spoken = stringResource(
        R.string.home_summary_latest_spoken,
        kind,
        date,
        km,
        RunkkFormat.durationSpoken(run.elapsed),
        RunkkFormat.paceSpoken(run.averagePace)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RunkkColors.Background)
            .clickable(role = Role.Button, onClickLabel = open, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            }
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(RunkkColors.LimeFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                RunkkIcons.Walk,
                contentDescription = null,
                tint = RunkkColors.OnLimeFill,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    kind,
                    style = RunkkTextStyles.text(11, FontWeight.Medium),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RunkkColors.Surface)
                        .border(1.dp, RunkkColors.Border, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Text(
                    date,
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(km, stringResource(R.string.home_summary_km), Modifier.alignByBaseline())
                Metric(time, null, Modifier.alignByBaseline())
                Metric(pace, stringResource(R.string.home_summary_pace_unit), Modifier.alignByBaseline())
            }
        }
        Icon(
            RunkkIcons.ChevronRight,
            contentDescription = null,
            tint = RunkkColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun Metric(value: String, unit: String?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = RunkkTextStyles.text(16, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.alignByBaseline()
        )
        unit?.let {
            Text(
                it,
                style = RunkkTextStyles.text(12, FontWeight.Medium),
                color = RunkkColors.TextSecondary,
                modifier = Modifier.alignByBaseline()
            )
        }
    }
}

/** 「러닝 잔디」 「최근 5주」 + 요일 줄 + 5×7 칸. 단계·범례 없음. 보조 기술은 달린 날 수로 읽는다. */
@Composable
private fun Grass(weeks: List<List<GrassCell>>) {
    val ranDays = weeks.sumOf { week -> week.count { it.state == GrassState.RAN } }
    val spoken = stringResource(R.string.home_summary_grass_spoken, ranDays)
    val weekdays = stringArrayResource(R.array.home_summary_weekdays)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.home_summary_grass),
                style = RunkkTextStyles.text(13, FontWeight.Medium),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.alignByBaseline().semantics { heading() }
            )
            Text(
                stringResource(R.string.home_summary_grass_range),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.alignByBaseline()
            )
        }
        Column(
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(GRASS_GAP)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(GRASS_GAP)) {
                weekdays.forEach {
                    Text(
                        it,
                        style = RunkkTextStyles.text(11, FontWeight.Medium),
                        color = RunkkColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            weeks.forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(GRASS_GAP)) {
                    week.forEach { cell -> GrassBox(cell, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 잔디 칸: 달린 날 라임, 안 달린 날 배경, 미래는 채움 없이 1 Border, 오늘은 2 TextPrimary 테두리. */
@Composable
private fun GrassBox(cell: GrassCell, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val fill = when (cell.state) {
        GrassState.RAN -> RunkkColors.LimeFill
        GrassState.EMPTY -> RunkkColors.Background
        GrassState.FUTURE -> Color.Transparent
    }
    val outline = when {
        cell.isToday -> Modifier.border(2.dp, RunkkColors.TextPrimary, shape)
        cell.state == GrassState.FUTURE -> Modifier.border(1.dp, RunkkColors.Border, shape)
        else -> Modifier
    }
    Box(modifier.height(14.dp).clip(shape).background(fill).then(outline))
}

/** "18분 40초". */
@Composable
private fun minutesSeconds(elapsed: Duration): String =
    stringResource(R.string.home_summary_minutes_seconds, elapsed.toMinutes(), elapsed.seconds % SECONDS_PER_MINUTE)

/** 최근 러닝 날짜 "11월 18일 (수) 오전 7:10". */
@Composable
private fun latestDate(run: RunSummary): String {
    val pattern = stringResource(R.string.home_summary_latest_date_pattern)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
    return formatter.format(run.startedAt.atZone(ZoneId.systemDefault()))
}

/** 카드 종류 이름(자유런·배틀런·셀프런·셀럽런·크루런). */
private fun kindLabelRes(kind: CardKind): Int = when (kind) {
    CardKind.FREE_RUN -> R.string.home_kind_free
    CardKind.BATTLE_RUN -> R.string.home_kind_battle
    CardKind.SELF_RUN -> R.string.home_kind_self
    CardKind.CELEB_RUN -> R.string.home_kind_celeb
    CardKind.CREW_RUN -> R.string.home_kind_crew
}

private const val SECONDS_PER_MINUTE = 60L
private val GRASS_GAP = 4.dp
private val TOGGLE_SHADOW = 6.dp
private val PANEL_SHADOW = 8.dp

/** 알약·패널 그림자(피그마 rgba(23,26,18,0.12)). */
private val SHADOW = Color(0x1F171A12)
