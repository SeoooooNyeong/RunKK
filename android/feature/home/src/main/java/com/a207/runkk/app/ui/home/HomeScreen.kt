package com.a207.runkk.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkInfoChip
import com.a207.runkk.app.core.designsystem.RunkkLogo
import com.a207.runkk.app.core.designsystem.RunkkLogoKind
import com.a207.runkk.app.core.designsystem.RunkkNumberText
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.runkkDashedBorder
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.TodayPlan
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** 홈(A5): 우리 집, 자유런·배틀런 시작, 오늘·이번 주 요약, 스트릭·잔디, 미전송 기록. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    HomeScreen(
        state = state,
        onModeSelected = viewModel::onModeSelected,
        onNavigate = navigator::navigate,
        onRetry = viewModel::refresh
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onModeSelected: (HomeRunMode) -> Unit,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(
            RunkkColors.Background
        ).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        RunkkLogo(RunkkLogoKind.WordmarkRrun, height = 24.dp, contentDescription = "RRun 런크크")
        Header(state, onNavigate)
        HouseCard(state.dashboard, onAvatar = { onNavigate(Routes.AVATAR) })
        RunCard(state, onModeSelected, onNavigate)
        if (state.error != null && state.dashboard == null) {
            RunkkErrorState(state.error, onRetry = onRetry)
        } else {
            SummaryTiles(state.dashboard, onCoach = { onNavigate(Routes.COACH) })
            state.dashboard?.let { StreakCard(it, state.today, onRecords = { onNavigate(Routes.RECORDS) }) }
            state.dashboard?.crew?.let { crew -> CrewCard(crew, onCrew = { onNavigate(Routes.CREW) }) }
        }
        if (state.pendingCount > 0) {
            RunkkCard(dashed = true, background = RunkkColors.Background, shape = RunkkShapes.Medium, onClick = {
                onNavigate(Routes.RECORDS)
            }, onClickLabel = "기록 목록 열기") {
                Text(
                    "미전송 기록 ${state.pendingCount}건 · 연결되면 자동으로 보내요",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun Header(state: HomeUiState, onNavigate: (String) -> Unit) {
    val header = state.header
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(
                48.dp
            ).border(2.dp, RunkkColors.Lime, CircleShape).padding(3.dp).clip(CircleShape).background(RunkkColors.Border)
        )
        Column(
            modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
            },
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (state.headerLoading) {
                SkeletonBar(width = 96.dp, height = 16.dp)
                SkeletonBar(width = 72.dp, height = 12.dp)
            } else {
                Text(
                    text = header.nickname ?: "러너",
                    style = RunkkTextStyles.text(16, FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = "${header.tier ?: "언랭"} · Lv.${header.level ?: RunkkFormat.EMPTY}",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        BalanceButton(balance = header.balance, onClick = { onNavigate(Routes.SHOP) })
        RunkkIconButton(RunkkIcons.Bell, contentDescription = "알림함", onClick = {
            onNavigate(Routes.NOTIFICATIONS)
        }, borderColor = RunkkColors.BorderStrong)
    }
}

/** 리워드 잔액 버튼(높이 40, 모서리 20). 누르면 상점. 잔액을 모르면 "-". */
@Composable
private fun BalanceButton(balance: Int?, onClick: () -> Unit) {
    val label = balance?.let { String.format(Locale.US, "%,d", it) } ?: RunkkFormat.EMPTY
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClickLabel = "상점 열기", onClick = onClick)
            .semantics { contentDescription = "리워드 잔액 $label" },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.BorderStrong, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(RunkkIcons.Bolt, contentDescription = null, tint = RunkkColors.Lime, modifier = Modifier.size(16.dp))
            Text(label, style = RunkkTextStyles.display(18), color = RunkkColors.TextPrimary)
        }
    }
}

/** 불러오는 중 자리(회색 막대). */
@Composable
private fun SkeletonBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(4.dp)).background(RunkkColors.Border))
}

@Composable
private fun HouseCard(dashboard: Dashboard?, onAvatar: () -> Unit) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth().height(136.dp),
        shape = RunkkShapes.ExtraLarge,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunkkCard(
                modifier = Modifier.size(width = 80.dp, height = 104.dp),
                shape = RunkkShapes.Medium,
                dashed = true,
                background = RunkkColors.Surface,
                onClick = onAvatar,
                onClickLabel = "아바타 꾸미기",
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "아바타",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.semantics(mergeDescendants = true) {
                }
            ) {
                Text("우리 집", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                Text(
                    dashboard?.landmark ?: "첫 랜드마크를 향해",
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary
                )
                RunkkNumberText(
                    value = dashboard?.total?.let(RunkkFormat::km) ?: "0.00",
                    unit = "KM",
                    style = RunkkTextStyles.display(24),
                    color = RunkkColors.Lime
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunCard(state: HomeUiState, onModeSelected: (HomeRunMode) -> Unit, onNavigate: (String) -> Unit) {
    RunkkCard(shape = RunkkShapes.ExtraLarge, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkSegmentedTabs(
            options = listOf("자유런", "배틀런"),
            selectedIndex = state.mode.ordinal,
            onSelect = { onModeSelected(HomeRunMode.entries[it]) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "러닝 종류" }
        )
        when (state.mode) {
            HomeRunMode.FREE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "오늘 계획 그대로",
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    RunkkTextLink("설정", onClick = {
                        onNavigate(Routes.FREE_RUN_SETTINGS)
                    }, contentDescription = "자유런 설정")
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val spoken = planChipsSpoken(state.todayPlan)
                    planChips(state.todayPlan).forEachIndexed { index, chip ->
                        RunkkInfoChip(
                            label = chip,
                            highlighted = index == 0 && state.todayPlan?.distance != null,
                            display =
                                index == 0 && state.todayPlan?.distance != null,
                            spoken = spoken.getOrNull(index)
                        )
                    }
                }
                RunkkPrimaryButton(
                    text = "자유런 시작",
                    onClick = { onNavigate(Routes.RUN) },
                    size = RunkkButtonSize.Large,
                    trailingArrow = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HomeRunMode.BATTLE -> {
                Text(
                    "같은 거리를 랜덤 상대와 동시에 달려요. 지난 거리를 기억해요.",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1K", "3K", "5K", "10K").forEach { RunkkInfoChip(it, display = true) }
                }
                RunkkPrimaryButton(
                    text = "배틀런 시작",
                    onClick = { onNavigate(Routes.BATTLE_RANDOM) },
                    size = RunkkButtonSize.Large,
                    trailingArrow = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 오늘 계획 칩: 목표 거리(있으면 첫 칩), 목표 페이스. 계획이 없으면 "목표 없이 달리기". */
internal fun planChips(plan: TodayPlan?): List<String> {
    if (plan == null || (plan.distance == null && plan.pace == null)) return listOf("목표 없이 달리기")
    return listOfNotNull(
        plan.distance?.let(RunkkFormat::targetLabel),
        plan.pace?.let {
            "목표 ${RunkkFormat.pace(it)}/km"
        }
    )
}

/** 계획 칩 읽기 문구: "오늘 목표 5킬로미터", "목표 페이스 1킬로미터당 5분 30초"("5:30"이 시각으로 읽히지 않게). */
internal fun planChipsSpoken(plan: TodayPlan?): List<String> {
    if (plan == null || (plan.distance == null && plan.pace == null)) return listOf("목표 없이 달리기")
    return listOfNotNull(
        plan.distance?.let { "오늘 목표 ${RunkkFormat.spokenLabel(RunkkFormat.targetLabel(it))}" },
        plan.pace?.let { "목표 페이스 ${RunkkFormat.paceSpoken(it)}" }
    )
}

@Composable
private fun SummaryTiles(dashboard: Dashboard?, onCoach: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            label = "오늘",
            value = dashboard?.today?.let(RunkkFormat::km) ?: "0.00",
            unit = "KM",
            caption = dashboard?.let(::todayCaption) ?: "오늘 달린 거리",
            valueStyle = RunkkTextStyles.display(32, lineHeightRatio = 1f),
            modifier = Modifier.weight(1f)
        )
        val achievement = dashboard?.weeklyAchievement
        RunkkStatTile(
            label = "이번 주 목표",
            value = achievement?.let { "${(it * PERCENT).roundToInt()}" } ?: "-",
            unit = if (achievement != null) "%" else null,
            valueColor = RunkkColors.Lime,
            valueStyle = RunkkTextStyles.display(32, lineHeightRatio = 1f),
            caption = if (achievement == null) "목표를 정해 보세요" else null,
            spoken = dashboard?.let(::weeklySpoken),
            onClick = onCoach,
            modifier = Modifier.weight(1f)
        ) {
            if (achievement != null) RunkkProgressBar(progress = achievement.toFloat())
        }
    }
}

/** 스트릭·잔디(FR-052~FR-055): 최근 4주, 달린 날은 라임. */
@Composable
private fun StreakCard(dashboard: Dashboard, today: LocalDate?, onRecords: () -> Unit) {
    val end = today ?: dashboard.grass.keys.maxOrNull() ?: return
    val days = (GRASS_DAYS - 1 downTo 0).map { end.minusDays(it.toLong()) }
    RunkkCard(onClick = onRecords, onClickLabel = "기록 달력 열기", verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "스트릭",
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            RunkkNumberText(
                value = "${dashboard.streakDays}",
                unit = "일",
                style = RunkkTextStyles.display(24),
                color = RunkkColors.Lime
            )
        }
        val ran = days.count { (dashboard.grass[it]?.meters ?: 0L) > 0 }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "최근 4주 중 ${ran}일 달렸어요" }
        ) {
            days.chunked(DAYS_PER_WEEK).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(2f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(grassColor(dashboard, day))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewCard(crew: Dashboard.CrewArea, onCrew: () -> Unit) {
    RunkkCard(onClick = onCrew, onClickLabel = "내 크루 열기") {
        Text("우리 크루 · ${crew.crewName}", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                crew.landmark ?: "크루 랜드마크를 향해",
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text("${crew.streakDays}일 연속", style = RunkkTextStyles.BodyStrong, color = RunkkColors.Lime)
        }
    }
}

/** 오늘 칸 아래 줄(하이파이 HiHome "[시간] · [n]회"). 오늘 기록이 없으면 안내 문구. */
internal fun todayCaption(dashboard: Dashboard): String = if (dashboard.todayRuns <= 0) {
    "아직 달리지 않았어요"
} else {
    "${RunkkFormat.duration(dashboard.todayElapsed)} · ${dashboard.todayRuns}회"
}

/**
 * 이번 주 칸 읽기 문구. weeklyAchievement는 거리 달성 비율(0.6 = 60%, 100%를 넘을 수 있다)이라 거리 기준임을 밝히고,
 * 횟수 목표가 있으면 덧붙인다.
 */
internal fun weeklySpoken(dashboard: Dashboard): String {
    val achievement = dashboard.weeklyAchievement ?: return "목표를 정해 보세요"
    val weekly = dashboard.weekly
    return listOfNotNull(
        "거리 ${(achievement * PERCENT).roundToInt()}% 달성",
        weekly?.let { "${it.goalRuns}회 중 ${it.actualRuns}회" }
    ).joinToString(", ")
}

/** 잔디 칸 색: 서버 진하기(0~4)가 있으면 단계별로, 없으면 달린 날만 라임. */
private fun grassColor(dashboard: Dashboard, day: LocalDate): Color {
    val level = dashboard.grassLevels[day] ?: if ((dashboard.grass[day]?.meters ?: 0L) > 0) MAX_GRASS_LEVEL else 0
    return GRASS_COLORS[level.coerceIn(0, MAX_GRASS_LEVEL)]
}

private const val MAX_GRASS_LEVEL = 4
private val GRASS_COLORS = listOf(
    RunkkColors.Border,
    RunkkColors.LimeContainer,
    RunkkColors.LimeDim,
    RunkkColors.Lime.copy(alpha = 0.8f),
    RunkkColors.Lime
)

private const val PERCENT = 100
private const val GRASS_DAYS = 28
private const val DAYS_PER_WEEK = 7

@Preview(name = "A5 홈", widthDp = 390, heightDp = 1100)
@Composable
private fun HomeScreenPreview() {
    val today = LocalDate.of(2026, 10, 20)
    RunkkTheme(reduceMotion = true) {
        HomeScreen(
            state = HomeUiState(
                loading = false,
                today = today,
                dashboard = Dashboard(
                    today = Distance(3_020),
                    total = Distance(128_400),
                    landmark = "한강 다리 12개",
                    streakDays = 4,
                    grass = (0L..27L).filter { it % 3 != 0L }.associate { today.minusDays(it) to Distance(4_000) },
                    weeklyAchievement = 0.6,
                    crew = Dashboard.CrewArea("새벽크루", 9, emptyMap(), "남산 3바퀴")
                ),
                todayPlan = TodayPlan(Distance(5_000), Pace(330)),
                pendingCount = 1,
                header = HomeHeader("달리는곰", "골드", 3, 1_250),
                headerLoading = false
            ),
            onModeSelected = {},
            onNavigate = {},
            onRetry = {}
        )
    }
}
