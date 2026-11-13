package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.a207.runkk.app.core.designsystem.MetaItem
import com.a207.runkk.app.core.designsystem.MetaRow
import com.a207.runkk.app.core.designsystem.RunkkAvatarCircle
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkDialog
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkSectionHeader
import com.a207.runkk.app.core.designsystem.RunkkSectionHeaderSize
import com.a207.runkk.app.core.designsystem.RunkkSegmented
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.domain.crew.CelebrationCard
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.RaceStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * 레이스 탭(피그마 F-05·F-06·F-06b·F-08, FR-137~FR-141): 달성한 주면 맨 위 달성 띠(「자세히 보기」로 축하 팝업 F-08b), 이번 주 목표
 * 카드(합계, 목표, 100%를 넘어도 그대로인 진행률, 두 겹 막대, 참여 인원과 내 기여율), 러닝 보드(주간·월간 막대, 순위 숫자 없음).
 */
@Composable
internal fun ColumnScope.RaceTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val race = state.race
    if (race == null) {
        TabLoading(state.isLoading)
        return
    }
    if (state.isCelebrationBannerShown) CelebrationBanner(race, onOpen = actions.onCelebrationOpen)
    GoalCard(race, canSetGoal = state.canSetGoal, onEdit = actions.onGoalEdit)
    RunningBoard(state, race, isWeekOnly = state.isCelebrationBannerShown, onPeriod = actions.onPeriod)
}

/** 달성 띠(피그마 F-08): surfaceHigh 채움 + 2 테두리, 왼쪽 「10월 4주 목표 달성」, 오른쪽 「자세히 보기」(누르면 축하 팝업). */
@Composable
private fun CelebrationBanner(race: RaceStatus, onOpen: () -> Unit) {
    val (month, week) = weekOfMonth(race)
    val shape = RoundedCornerShape(28.dp)
    val more = stringResource(R.string.crew_celebration_more)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.SurfaceHigh)
            .border(2.dp, RunkkColors.Border, shape)
            .clickable(role = Role.Button, onClickLabel = more, onClick = onOpen)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.crew_celebration_banner, month, week),
            style = RunkkTextStyles.text(12, FontWeight.Bold, lineHeight = 18),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(more, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
    }
}

/**
 * 이번 주 목표 카드(피그마 F-05·F-06·F-06b·F-08): 제목(목표가 있으면 「이번 주 목표」, 없으면 「이번 주 달린 거리」)과 오른쪽 마감·달성·
 * 「목표 정하기」(크루장, 밑줄), 큰 숫자(Bold 30) + 「/ 80 km」 + 진행률(100을 넘어도 그대로), 8 두께 두 겹 막대, 참여 인원과 내 기여율.
 */
@Composable
private fun GoalCard(race: RaceStatus, canSetGoal: Boolean, onEdit: () -> Unit) {
    val goal = race.goal
    val progress = goalProgressLabel(race)
    val total = RunkkFormat.kmShort(race.total)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (goal != null) R.string.crew_goal_title else R.string.crew_goal_title_none),
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            if (goal != null) {
                val isAchieved = race.achievedAt != null
                Text(
                    stringResource(if (isAchieved) R.string.crew_goal_achieved else R.string.crew_race_deadline),
                    style = RunkkTextStyles.text(12, if (isAchieved) FontWeight.Bold else FontWeight.Normal, 18),
                    color = RunkkColors.TextPrimary
                )
            }
            if (canSetGoal) GoalLink(onEdit)
        }
        val description = if (goal != null && progress != null) {
            stringResource(R.string.crew_goal_progress_description, total, RunkkFormat.kmShort(goal), progress)
        } else {
            stringResource(R.string.crew_race_total_description, total)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 단위 칸이 남는 폭을 차지해 진행률이 오른쪽 끝에 붙는다(피그마 F-05).
            Text(
                total,
                style = RunkkTextStyles.text(30, FontWeight.Bold, lineHeight = 30),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                softWrap = false
            )
            Text(
                if (goal != null) {
                    stringResource(R.string.crew_goal_of, RunkkFormat.kmShort(goal))
                } else {
                    stringResource(R.string.crew_goal_km_unit)
                },
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.padding(bottom = 2.dp).weight(1f)
            )
            if (progress != null) {
                Text(
                    stringResource(R.string.crew_race_share_percent, progress),
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        RaceBar(total = goalFillFraction(race), mine = myFillFraction(race))
        val share = myShareLabel(race)
        MetaRow(
            listOf(
                MetaItem(RunkkIcons.Group, stringResource(R.string.crew_race_participants, participantCount(race))),
                MetaItem(
                    RunkkIcons.TrendingUp,
                    stringResource(R.string.crew_race_my_share, share),
                    spoken = stringResource(R.string.crew_race_my_share_spoken, share)
                )
            )
        )
    }
}

/** 「목표 정하기」(피그마 F-06): 13/16 Regular 본문색 밑줄, 누름 칸 48. */
@Composable
private fun GoalLink(onEdit: () -> Unit) {
    RunkkTextLink(
        stringResource(R.string.crew_goal_set),
        onClick = onEdit,
        isUnderlined = true,
        style = RunkkTextStyles.text(13, lineHeight = 16)
    )
}

/**
 * 두 겹 막대(피그마 F-05·F-07): 8 두께, 트랙 surfaceHigh r4, 전체 진행 barCrew, 그 앞(왼쪽부터) 내 기여 구간 보라([mine]이 0이면
 * 없음). 보조 기술에는 숨긴다(숫자는 위 줄이 읽는다).
 */
@Composable
internal fun RaceBar(total: Float, mine: Float = 0f) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(RunkkColors.SurfaceHigh)
            .clearAndSetSemantics {}
    ) {
        if (total > 0f) Box(Modifier.fillMaxWidth(total).fillMaxHeight().clip(shape).background(RunkkColors.BarCrew))
        if (mine > 0f) Box(Modifier.fillMaxWidth(mine).fillMaxHeight().clip(shape).background(RunkkColors.Violet))
    }
}

/**
 * 공동 목표 축하 팝업(피그마 F-08b, FR-141): 가운데 카드(surface + 2 테두리, r28, 안쪽 20, 간격 14), 「10월 4주」, 크루 기본 이미지 80 원,
 * 「[크루명]이 함께 목표를 달성했어요」, 숫자 3칸(주간 합산, 목표 거리, 참여 인원), 「확인」. 그 주 첫 진입 때 한 번 자동으로 뜬다.
 * 가운데 정렬 카드라 왼쪽 정렬 제목의 RunkkDialog 대신 같은 딤·모양으로 그린다.
 */
@Composable
internal fun CelebrationDialog(race: RaceStatus, card: CelebrationCard, onClose: () -> Unit) {
    val (month, week) = weekOfMonth(race)
    val title = stringResource(R.string.crew_celebration_week, month, week)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(CELEBRATION_DIM) }
        val shape = RunkkShapes.Dialog
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 350.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(RunkkColors.Surface)
                .border(2.dp, RunkkColors.Border, shape)
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, style = RunkkTextStyles.text(14, FontWeight.Bold, 18), color = RunkkColors.TextPrimary)
            RunkkAvatarCircle(80.dp) {
                Icon(
                    RunkkIcons.Group,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(44.dp)
                )
            }
            Text(
                stringResource(
                    if (hasFinalConsonant(card.crewName)) {
                        R.string.crew_celebration_headline_i
                    } else {
                        R.string.crew_celebration_headline_ga
                    },
                    card.crewName
                ),
                style = RunkkTextStyles.text(22, FontWeight.Bold, lineHeight = 30),
                color = RunkkColors.TextPrimary,
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
            RunkkButton(
                stringResource(R.string.crew_celebration_confirm),
                onClick = onClose,
                size = RunkkButtonSize.Medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 축하 팝업의 숫자 한 칸(값 Bold 18, 이름 12 본문색). */
@Composable
private fun RowScope.CelebrationStat(value: String, label: String) {
    Column(
        Modifier.weight(1f).semantics(mergeDescendants = true) {}.padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = RunkkTextStyles.SheetTitle,
            color = RunkkColors.TextPrimary,
            maxLines = 1
        )
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
    }
}

/**
 * 러닝 보드(피그마 F-05, FR-140, TC-7497): 제목 「러닝 보드」 Bold 14와 주간·월간 고르기(달성한 주는 「이번 주」만). 줄은 원 32, 이름
 * 13 폭 64, 막대 11.2(남 barCrew, 나 보라), 거리 Bold 13 폭 52. 순위 숫자는 없다.
 */
@Composable
private fun RunningBoard(
    state: CrewHomeUiState,
    race: RaceStatus,
    isWeekOnly: Boolean,
    onPeriod: (CrewRanking.Period) -> Unit
) {
    val isWeekly = isWeekOnly || state.period == CrewRanking.Period.WEEKLY
    RunkkSectionHeader(stringResource(R.string.crew_contribution_title), size = RunkkSectionHeaderSize.Compact) {
        if (isWeekOnly) {
            Text(
                stringResource(R.string.crew_contribution_this_week),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextPrimary
            )
        } else {
            RunkkSegmented(
                options = listOf(
                    stringResource(R.string.crew_race_period_weekly),
                    stringResource(R.string.crew_race_period_monthly)
                ),
                selectedIndex = state.period.ordinal,
                onSelect = { onPeriod(CrewRanking.Period.entries[it]) },
                modifier = Modifier.width(PERIOD_WIDTH)
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
            color = RunkkColors.TextSecondary
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { bars.forEach { ContributionRow(it) } }
}

/** 러닝 보드 한 줄: 원(프로필 자리), 이름, 막대, 거리. */
@Composable
private fun ContributionRow(bar: ContributionBar) {
    val name = if (bar.isMe) stringResource(R.string.crew_contribution_me) else bar.displayName
    val km = RunkkFormat.kmShort(bar.distance)
    val description = stringResource(R.string.crew_contribution_row_description, name, km)
    val barShape = RoundedCornerShape(5.6.dp)
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RunkkAvatarCircle(32.dp)
        Text(
            name,
            style = RunkkTextStyles.text(13, lineHeight = 16),
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp)
        )
        Box(Modifier.weight(1f).height(11.2.dp).clip(barShape).background(RunkkColors.SurfaceHigh)) {
            if (bar.fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(bar.fraction)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(if (bar.isMe) RunkkColors.Violet else RunkkColors.BarCrew)
                )
            }
        }
        Text(
            stringResource(R.string.crew_contribution_km, km),
            style = RunkkTextStyles.text(13, FontWeight.Bold),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(52.dp)
        )
    }
}

/** 레이스 주의 "몇 월 몇째 주"(그 주 목요일이 속한 달 기준). */
internal fun weekOfMonth(race: RaceStatus, zone: ZoneId = ZoneId.systemDefault()): Pair<Int, Int> =
    weekOfMonth(raceWeekStart(race, zone))

/** 월요일 [monday]가 시작하는 주의 "몇 월 몇째 주"(그 주 목요일이 속한 달 기준, F-07 「10월 4주차」와 같다). */
internal fun weekOfMonth(monday: LocalDate): Pair<Int, Int> {
    val thursday = monday.plusDays(THURSDAY_OFFSET)
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

private const val PERCENT = 100
private const val THURSDAY_OFFSET = 3L
private const val DAYS_PER_WEEK = 7
private const val HANGUL_FIRST = '가'
private const val HANGUL_LAST = '힣'
private const val JONGSEONG_COUNT = 28

/** 축하 팝업 뒤 딤(피그마 rgba(23,27,18,0.45)). */
private const val CELEBRATION_DIM = 0.45f

/** 주간·월간 고르기 폭(피그마 작은 세그먼트 두 칸). */
private val PERIOD_WIDTH = 140.dp
