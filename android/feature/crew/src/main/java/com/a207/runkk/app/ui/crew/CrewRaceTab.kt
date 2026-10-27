package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 레이스 탭(와이어 E2, E9, FR-137~FR-141): 이번 주 공동 목표 카드(마감, 합계/목표, 진행률 100% 상한, 내 기여, 목표 설정)와
 * 기여도 보드(주간, 월간 막대, 순위 숫자 없음). 목표를 달성한 주는 목표 카드 대신 축하 카드와 공유 단추를 보이고 보드는 이번 주만.
 */
@Composable
internal fun ColumnScope.RaceTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val race = state.race
    if (race == null) {
        TabLoading(state.isLoading)
        return
    }
    val celebration = race.celebration
    if (celebration != null) {
        CelebrationCardView(race, celebration, isSharing = state.isSharing, onShare = actions.onShareCelebration)
    } else {
        GoalCard(race, canSetGoal = state.canSetGoal, onEdit = actions.onGoalEdit)
    }
    ContributionBoard(state, race, isWeekOnly = celebration != null, onPeriod = actions.onPeriod)
}

/** 이번 주 공동 목표(와이어 E2): 마감, 합계 / 목표 km, 진행률, 막대, 내 기여, 목표 설정(크루장, 운영진). */
@Composable
private fun GoalCard(race: RaceStatus, canSetGoal: Boolean, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val goal = race.goal
    val progress = goalProgressLabel(race)
    val mine = race.contributors.firstOrNull { it.isMe }
    val total = RunkkFormat.kmShort(race.total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(colors.surface)
            .border(1.dp, colors.outlineVariant, RunkkShapes.ExtraLarge)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_goal_title),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_race_deadline),
                style = RunkkTextStyles.text(12, FontWeight.Bold),
                color = colors.secondary
            )
        }
        val description = if (goal != null && progress != null) {
            stringResource(R.string.crew_goal_progress_description, total, RunkkFormat.kmShort(goal), progress)
        } else {
            stringResource(R.string.crew_race_total_description, total)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 글자를 키워도 줄을 넘지 않게 숫자 칸이 줄어든다(WCAG 1.4.4).
            Text(
                total,
                style = RunkkTextStyles.display(44),
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                if (goal != null) {
                    stringResource(R.string.crew_goal_of, RunkkFormat.kmShort(goal))
                } else {
                    stringResource(R.string.crew_goal_km_unit)
                },
                style = RunkkTextStyles.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp).weight(1f)
            )
            if (progress != null) {
                Text(
                    stringResource(R.string.crew_race_share_percent, progress),
                    style = RunkkTextStyles.text(15, FontWeight.Black),
                    color = colors.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        }
        if (progress != null) {
            RunkkProgressBar(progress / PERCENT.toFloat(), color = colors.primary, trackColor = colors.outlineVariant)
        } else if (!canSetGoal) {
            Text(
                stringResource(R.string.crew_goal_none),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_race_my_contribution, RunkkFormat.kmShort(mine?.distance ?: ZERO)),
                style = RunkkTextStyles.Caption,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (canSetGoal) RunkkTextLink(stringResource(R.string.crew_goal_set), onClick = onEdit)
        }
    }
}

/**
 * 공동 목표 달성 축하 카드(와이어 E9, FR-141): 그림, "10월 4주 공동 목표 달성", "크루명이 함께 목표를 넘었어요", 주간 합산, 목표 거리,
 * 참여 인원과 공유 단추.
 */
@Composable
private fun CelebrationCardView(
    race: RaceStatus,
    card: CelebrationCard,
    isSharing: Boolean,
    onShare: (CelebrationCard) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val (month, week) = weekOfMonth(race)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(colors.primaryContainer)
            .border(1.dp, colors.primary, RunkkShapes.ExtraLarge)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(RunkkIcons.Trophy, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(32.dp))
        }
        Text(
            stringResource(R.string.crew_celebration_week, month, week),
            style = RunkkTextStyles.text(12, FontWeight.Bold),
            color = colors.primary
        )
        Text(
            stringResource(
                if (hasFinalConsonant(card.crewName)) {
                    R.string.crew_celebration_headline_i
                } else {
                    R.string.crew_celebration_headline_ga
                },
                card.crewName
            ),
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CelebrationStat(
                stringResource(R.string.crew_contribution_km, RunkkFormat.kmShort(card.total)),
                stringResource(R.string.crew_celebration_total)
            )
            CelebrationStat(
                stringResource(R.string.crew_contribution_km, RunkkFormat.kmShort(card.goal)),
                stringResource(R.string.crew_celebration_goal)
            )
            CelebrationStat(
                stringResource(R.string.crew_celebration_people_value, card.participantCount),
                stringResource(R.string.crew_celebration_people)
            )
        }
        RunkkPrimaryButton(
            stringResource(R.string.crew_celebration_share),
            onClick = { onShare(card) },
            isEnabled = !isSharing,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 축하 카드의 숫자 한 칸(값과 이름). */
@Composable
private fun RowScope.CelebrationStat(value: String, label: String) {
    Column(
        Modifier.weight(1f).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(label, style = RunkkTextStyles.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 기여도 보드(와이어 E2, FR-140, TC-7497): 이름, 막대, 거리. 순위 숫자는 없다. 본인 막대는 포인트 색. 주간은 레이스 기여자, 월간은
 * 크루 랭킹 응답으로 그린다. [isWeekOnly]면(목표 달성 주, E9) 기간 고르기 대신 "이번 주"를 보인다.
 */
@Composable
private fun ContributionBoard(
    state: CrewHomeUiState,
    race: RaceStatus,
    isWeekOnly: Boolean,
    onPeriod: (CrewRanking.Period) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isWeekly = isWeekOnly || state.period == CrewRanking.Period.WEEKLY
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.crew_contribution_title),
            style = RunkkTextStyles.CardTitle,
            color = colors.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (isWeekOnly) {
            Text(
                stringResource(R.string.crew_contribution_this_week),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant
            )
        } else {
            RunkkSegmentedTabs(
                options = listOf(
                    stringResource(R.string.crew_race_period_weekly),
                    stringResource(R.string.crew_race_period_monthly)
                ),
                selectedIndex = state.period.ordinal,
                onSelect = { onPeriod(CrewRanking.Period.entries[it]) },
                isCompact = true
            )
        }
    }
    val bars = if (isWeekly) {
        contributionBars(race)
    } else {
        rankingBars(state.ranking?.entries.orEmpty(), meName = race.contributors.firstOrNull { it.isMe }?.displayName)
    }
    if (bars.isEmpty()) {
        Text(
            stringResource(R.string.crew_race_ranking_empty),
            style = RunkkTextStyles.Caption,
            color = colors.onSurfaceVariant
        )
    }
    bars.forEach { ContributionRow(it) }
}

/** 기여도 한 줄: 머리글자 동그라미, 이름, 막대, 거리. */
@Composable
private fun ContributionRow(bar: ContributionBar) {
    val colors = MaterialTheme.colorScheme
    val name = if (bar.isMe) stringResource(R.string.crew_contribution_me) else bar.displayName
    val km = RunkkFormat.kmShort(bar.distance)
    val description = stringResource(R.string.crew_contribution_row_description, name, km)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CrewLogo(name, size = 32, isAccent = bar.isMe, modifier = Modifier.clip(CircleShape))
        Text(
            name,
            style = RunkkTextStyles.Body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.outlineVariant)
        ) {
            if (bar.fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(bar.fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (bar.isMe) colors.primary else colors.onSurfaceVariant)
                )
            }
        }
        Text(
            stringResource(R.string.crew_contribution_km, km),
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(56.dp)
        )
    }
}

/** 레이스 마감 주의 "몇 월 몇째 주"(그 주 목요일이 속한 달 기준). */
private fun weekOfMonth(race: RaceStatus, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> {
    // 마감은 일요일 자정(다음 주 월요일 0시)이라 1초 앞으로 당겨 이번 주 안으로 둔다.
    val day = race.closesAt.minusSeconds(1).atZone(zone).toLocalDate()
    val thursday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusDays(THURSDAY_OFFSET)
    return thursday.monthValue to (thursday.dayOfMonth - 1) / DAYS_PER_WEEK + 1
}

/** 한글 이름 끝 글자에 받침이 있는가(조사 이/가). 한글이 아니면 받침 있음으로 본다. */
private fun hasFinalConsonant(name: String): Boolean {
    val last = name.trim().lastOrNull() ?: return true
    if (last !in HANGUL_FIRST..HANGUL_LAST) return true
    return (last - HANGUL_FIRST) % JONGSEONG_COUNT != 0
}

/** 기여율(0~1 또는 0~100)을 정수 %로(소수 버림, FR-140). */
internal fun shareLabel(share: Double): Int = (if (share <= 1.0) share * PERCENT else share).toInt()

private val ZERO = Distance(0)
private const val PERCENT = 100
private const val THURSDAY_OFFSET = 3L
private const val DAYS_PER_WEEK = 7
private const val HANGUL_FIRST = '가'
private const val HANGUL_LAST = '힣'
private const val JONGSEONG_COUNT = 28
