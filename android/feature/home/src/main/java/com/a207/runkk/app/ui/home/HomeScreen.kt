package com.a207.runkk.app.ui.home

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.RunkkAvatarArt
import com.a207.runkk.app.core.designsystem.RunkkAvatarPortrait
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
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
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.runkkDashedBorder
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.FaceLayer
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.run.FreeRunGoal
import com.a207.runkk.app.domain.run.PlanSnapshot
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** 홈(A5·A5-2): 상단(닉네임·잔액·알림), 화면을 채우는 우리 집(오늘·이번 주, 랜드마크, 아바타, 자유런·배틀런 시작). */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 러닝 결과·다른 화면에서 돌아오면 요약을 다시 읽는다(처음 표시는 init이 읽는다).
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val navigator = LocalRunkkNavigator.current
    HomeScreen(
        state = state,
        onModeSelected = viewModel::onModeSelected,
        onNavigate = navigator::navigate,
        onRetry = viewModel::refresh,
        onBattleDistanceSelected = viewModel::onBattleDistanceSelected,
        onBattleStart = { navigator.navigate(battleQuickMatchKey(it)) }
    )
}

/** 홈 배틀런 시작 경로: 고른 거리로 바로 매칭(FR-080). 매칭 화면(ui.battle)은 경로 인자로 거리를 받는다. */
internal fun battleQuickMatchKey(distance: Distance): Routes.BattleQuickMatch = Routes.BattleQuickMatch(distance.meters)

@Composable
fun HomeScreen(
    state: HomeUiState,
    onModeSelected: (HomeRunMode) -> Unit,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBattleDistanceSelected: (Distance) -> Unit = {},
    onBattleStart: (Distance) -> Unit = {}
) {
    // 우리 집은 화면 남은 높이를 채운다. 작은 화면에서는 최소 높이를 지키고 화면이 스크롤된다.
    BoxWithConstraints(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        val roomHeight = (maxHeight - TOP_PART_HEIGHT).coerceAtLeast(ROOM_MIN_HEIGHT)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkLogo(
                RunkkLogoKind.WordmarkRrun,
                height = 24.dp,
                contentDescription = stringResource(R.string.home_logo_description)
            )
            Header(state, onNavigate)
            HouseRoom(
                state = state,
                actions = HomeRoomActions(onModeSelected, onNavigate, onRetry, onBattleDistanceSelected, onBattleStart),
                modifier = Modifier.fillMaxWidth().height(roomHeight)
            )
        }
    }
}

/** 우리 집 안의 행동 묶음. */
private class HomeRoomActions(
    val onModeSelected: (HomeRunMode) -> Unit,
    val onNavigate: (String) -> Unit,
    val onRetry: () -> Unit,
    val onBattleDistanceSelected: (Distance) -> Unit,
    val onBattleStart: (Distance) -> Unit
)

@Composable
private fun Header(state: HomeUiState, onNavigate: (String) -> Unit) {
    val header = state.header
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RunkkAvatarPortrait(
            state.faceKey,
            state.faceLayers,
            modifier = Modifier.size(
                48.dp
            ).border(2.dp, RunkkColors.Lime, CircleShape).padding(3.dp).clip(CircleShape).background(RunkkColors.Border)
        )
        HeaderIdentity(state, modifier = Modifier.weight(1f))
        BalanceButton(balance = header.balance, onClick = { onNavigate(Routes.SHOP) })
        RunkkIconButton(
            RunkkIcons.Bell,
            contentDescription = stringResource(
                R.string.home_header_notifications
            ),
            onClick = {
                onNavigate(Routes.NOTIFICATIONS)
            },
            borderColor = RunkkColors.BorderStrong
        )
    }
}

/** 닉네임과 티어·레벨 줄(불러오는 동안은 회색 막대). */
@Composable
private fun HeaderIdentity(state: HomeUiState, modifier: Modifier = Modifier) {
    val header = state.header
    val tierLevel = stringResource(
        R.string.home_header_tier_level,
        header.tier ?: stringResource(R.string.home_header_default_tier),
        header.level ?: RunkkFormat.EMPTY
    )
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (state.isHeaderLoading) {
            SkeletonBar(width = 96.dp, height = 16.dp)
            SkeletonBar(width = 72.dp, height = 12.dp)
        } else {
            Text(
                text = header.nickname ?: stringResource(R.string.home_header_default_nickname),
                style = RunkkTextStyles.text(16, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = tierLevel,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

/** 리워드 잔액 버튼(높이 40, 모서리 20). 누르면 상점. 잔액을 모르면 "-". */
@Composable
private fun BalanceButton(balance: Int?, onClick: () -> Unit) {
    val label = balance?.let { String.format(Locale.US, "%,d", it) } ?: RunkkFormat.EMPTY
    val openShop = stringResource(R.string.home_balance_open_shop)
    val description = stringResource(R.string.home_balance_description, label)
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClickLabel = openShop, onClick = onClick)
            .semantics { contentDescription = description },
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
private fun SkeletonBar(width: Dp, height: Dp) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(4.dp)).background(RunkkColors.Border))
}

/**
 * 우리 집(A5): 고른 랜드마크 배경(FR-202) 위에 오늘·이번 주 칸, 랜드마크 표기·누적 거리, 아바타, 러닝 시작 패널을 겹친다.
 * 배경은 장식이라 읽지 않고, 위에 얹는 칸은 반투명 바탕으로 글자를 읽기 쉽게 한다.
 */
@Composable
private fun HouseRoom(state: HomeUiState, actions: HomeRoomActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(ROOM_CORNER)
    val roomLabel = stringResource(R.string.home_house_title)
    Box(
        modifier = modifier
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .semantics {
                contentDescription = roomLabel
                isTraversalGroup = true
            }
    ) {
        AvatarBackdrop(state.backgroundKey, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SummaryTiles(state.dashboard, onNavigate = actions.onNavigate)
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.error != null && state.dashboard == null) {
                    RunkkErrorState(
                        state.error.asString(),
                        onRetry = actions.onRetry,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(20.dp))
                            .background(overlayColor())
                    )
                } else {
                    RoomAvatar(
                        state.faceKey,
                        state.faceLayers,
                        onAvatar = { actions.onNavigate(Routes.AVATAR) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    LandmarkTag(state.dashboard, modifier = Modifier.align(Alignment.TopStart))
                }
            }
            RunPanel(state, actions)
        }
    }
}

/** 우리 집 위에 얹는 칸의 반투명 바탕(지금 테마 배경색, 배경 그림 위 글자 대비). */
@Composable
private fun overlayColor(): Color = RunkkColors.Background.copy(alpha = OVERLAY_ALPHA)

/** 오늘 달린 거리·이번 주 목표 두 칸. 오늘은 기록 목록, 이번 주는 코치로 간다. */
@Composable
private fun SummaryTiles(dashboard: Dashboard?, onNavigate: (String) -> Unit) {
    val resources = LocalContext.current.resources
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoomTile(
            label = stringResource(R.string.home_summary_today),
            spoken = todaySpoken(resources, dashboard),
            onClick = { onNavigate(Routes.RECORDS) },
            onClickLabel = stringResource(R.string.home_open_records),
            modifier = Modifier.weight(1f)
        ) {
            RunkkNumberText(
                value = dashboard?.today?.let(RunkkFormat::km) ?: "0.00",
                unit = "KM",
                style = RunkkTextStyles.display(28, lineHeightRatio = 1f),
                color = RunkkColors.TextPrimary
            )
            Text(
                dashboard?.let { todayCaption(resources, it) } ?: stringResource(R.string.home_today_not_run),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.TextSecondary
            )
        }
        val achievement = dashboard?.weeklyAchievement
        RoomTile(
            label = stringResource(R.string.home_summary_week_goal),
            spoken = dashboard?.let { weeklySpoken(resources, it) } ?: stringResource(R.string.home_summary_set_goal),
            onClick = { onNavigate(Routes.COACH) },
            onClickLabel = stringResource(R.string.home_open_coach),
            modifier = Modifier.weight(1f)
        ) {
            RunkkNumberText(
                value = achievement?.let { "${(it * PERCENT).roundToInt()}" } ?: "-",
                unit = achievement?.let { "%" },
                style = RunkkTextStyles.display(28, lineHeightRatio = 1f),
                color = RunkkColors.Lime
            )
            if (achievement != null) {
                RunkkProgressBar(
                    progress = achievement.toFloat(),
                    height = 6.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    stringResource(R.string.home_summary_set_goal),
                    style = RunkkTextStyles.Micro,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    }
}

/** 우리 집 위 반투명 칸(누르면 [onClick]). 보조 기술은 "라벨 읽기문구"로 한 번에 읽는다. */
@Composable
private fun RoomTile(
    label: String,
    spoken: String,
    onClick: () -> Unit,
    onClickLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(overlayColor())
            .border(1.dp, RunkkColors.Border, shape)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label $spoken"
                role = Role.Button
                onClick(label = onClickLabel) {
                    onClick()
                    true
                }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        content()
    }
}

/** 왼쪽 위 랜드마크 표기와 누적 거리. 아직 랜드마크가 없으면 첫 랜드마크를 향한다는 표기. */
@Composable
private fun LandmarkTag(dashboard: Dashboard?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(overlayColor())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            dashboard?.landmark ?: stringResource(R.string.home_house_first_landmark),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary
        )
        RunkkNumberText(
            value = dashboard?.total?.let(RunkkFormat::km) ?: "0.00",
            unit = "KM",
            style = RunkkTextStyles.display(22, lineHeightRatio = 1f),
            color = RunkkColors.Lime
        )
    }
}

/** 우리 집 가운데 아바타(기본 캐릭터·셀카 얼굴). 그림이 없으면 점선 자리. 누르면 아바타 꾸미기. */
@Composable
private fun RoomAvatar(
    faceKey: String?,
    faceLayers: List<FaceLayer>,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasArt = RunkkAvatarArt.characterOf(faceKey) != null || faceLayers.isNotEmpty()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.home_house_edit_avatar),
                onClick = onAvatar
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasArt) {
            RunkkAvatarPortrait(faceKey, faceLayers, Modifier.size(AVATAR_SIZE).testTag(HOME_FACE_TAG))
        } else {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 152.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(overlayColor())
                    .runkkDashedBorder(corner = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.home_house_avatar),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        // 아바타 발밑 그림자(장식).
        Box(
            Modifier
                .size(width = 160.dp, height = 18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = SHADOW_ALPHA))
                .clearAndSetSemantics {}
        )
    }
}

/** 러닝 시작 패널: 자유런·배틀런 탭, 칩 줄과 설정, 시작 버튼. */
@Composable
private fun RunPanel(state: HomeUiState, actions: HomeRoomActions) {
    val modeDescription = stringResource(R.string.home_mode_description)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(overlayColor())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunkkSegmentedTabs(
            options = listOf(stringResource(R.string.home_mode_free), stringResource(R.string.home_mode_battle)),
            selectedIndex = state.mode.ordinal,
            onSelect = { actions.onModeSelected(HomeRunMode.entries[it]) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = modeDescription }
        )
        when (state.mode) {
            HomeRunMode.FREE -> FreeRunPanel(state, actions.onNavigate)

            HomeRunMode.BATTLE -> BattleRunPanel(
                state.battleDistance,
                actions.onBattleDistanceSelected,
                actions.onBattleStart,
                onSettings = { actions.onNavigate(Routes.FREE_RUN_SETTINGS) }
            )
        }
    }
}

/** 자유런 탭: 목표 칩(오늘 계획 또는 자유런 설정 목표), 음성·진동 칩, 설정, 시작 버튼. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeRunPanel(state: HomeUiState, onNavigate: (String) -> Unit) {
    val goal = state.freeRunGoal
    val plan = goal.plan
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            val resources = LocalContext.current.resources
            val spoken = planChipsSpoken(resources, goal)
            planChips(resources, plan).forEachIndexed { index, chip ->
                val isTarget = index == 0 && plan?.targetDistance != null
                RunkkInfoChip(
                    label = chip,
                    isHighlighted = isTarget,
                    isDisplay = isTarget,
                    spoken = spoken.getOrNull(index)
                )
            }
            RunkkInfoChip(
                stringResource(if (state.isRunVoiceOn) R.string.home_free_voice_on else R.string.home_free_voice_off)
            )
            RunkkInfoChip(
                stringResource(
                    if (state.isRunVibrationOn) R.string.home_free_vibration_on else R.string.home_free_vibration_off
                )
            )
        }
        RunkkTextLink(stringResource(R.string.home_free_settings), onClick = {
            onNavigate(Routes.FREE_RUN_SETTINGS)
        }, contentDescription = stringResource(R.string.home_free_settings_description))
    }
    RunkkPrimaryButton(
        text = stringResource(R.string.home_free_start),
        onClick = { onNavigate(Routes.RUN) },
        size = RunkkButtonSize.Large,
        hasTrailingArrow = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 배틀런 탭(A5-2): 대결 거리 고르기(1·3·5·10km, 처음엔 기본 대결 거리), 설정, "랜덤 매칭 시작".
 * 거리를 고르는 것만으로는 매칭하지 않고 시작 버튼을 눌러야 고른 거리로 매칭을 요청한다(FR-080).
 */
@Composable
private fun BattleRunPanel(
    distance: Distance,
    onDistanceSelected: (Distance) -> Unit,
    onStart: (Distance) -> Unit,
    onSettings: () -> Unit
) {
    val distances = HomeUiState.BATTLE_DISTANCES
    val labels = distances.map(RunkkFormat::targetLabel)
    val groupLabel = stringResource(R.string.home_battle_distance_title)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSegmentRow(
            options = labels,
            selectedIndex = distances.indexOf(distance),
            onSelect = { onDistanceSelected(distances[it]) },
            spokenLabels = labels.map { RunkkFormat.spokenLabel(it) },
            modifier = Modifier.weight(1f).semantics {
                contentDescription = groupLabel
                isTraversalGroup = true
            }
        )
        RunkkTextLink(
            stringResource(R.string.home_free_settings),
            onClick = onSettings,
            contentDescription = stringResource(R.string.home_battle_settings_description)
        )
    }
    RunkkPrimaryButton(
        text = stringResource(R.string.home_battle_start),
        onClick = { onStart(distance) },
        size = RunkkButtonSize.Large,
        hasTrailingArrow = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 목표 칩: 목표 거리(있으면 첫 칩), 목표 페이스. 목표가 없으면 "목표 없이 달리기". */
internal fun planChips(res: Resources, plan: PlanSnapshot?): List<String> {
    if (plan == null ||
        (plan.targetDistance == null && plan.targetPace == null)
    ) {
        return listOf(res.getString(R.string.home_plan_none))
    }
    return listOfNotNull(
        plan.targetDistance?.let(RunkkFormat::targetLabel),
        plan.targetPace?.let {
            res.getString(R.string.home_plan_pace, RunkkFormat.pace(it))
        }
    )
}

/**
 * 목표 칩 읽기 문구: 오늘 계획이면 "오늘 목표 5킬로미터", 자유런 설정 목표면 "목표 3킬로미터",
 * "목표 페이스 1킬로미터당 5분 30초"("5:30"이 시각으로 읽히지 않게).
 */
internal fun planChipsSpoken(res: Resources, goal: FreeRunGoal): List<String> {
    val plan = goal.plan
    if (plan == null ||
        (plan.targetDistance == null && plan.targetPace == null)
    ) {
        return listOf(res.getString(R.string.home_plan_none))
    }
    val distanceText =
        if (goal.isFromCoachPlan) R.string.home_plan_distance_spoken else R.string.home_goal_distance_spoken
    return listOfNotNull(
        plan.targetDistance?.let {
            res.getString(distanceText, RunkkFormat.spokenLabel(res, RunkkFormat.targetLabel(it)))
        },
        plan.targetPace?.let { res.getString(R.string.home_plan_pace_spoken, RunkkFormat.paceSpoken(res, it)) }
    )
}

/** 오늘 칸 아래 줄("[시간], [n]회"). 오늘 기록이 없으면 안내 문구. */
internal fun todayCaption(res: Resources, dashboard: Dashboard): String = if (dashboard.todayRuns <= 0) {
    res.getString(R.string.home_today_not_run)
} else {
    res.getString(R.string.home_today_duration_runs, RunkkFormat.duration(dashboard.todayElapsed), dashboard.todayRuns)
}

/** 오늘 칸 읽기 문구: "3.02킬로미터, 18분 20초, 2회"("KM"·"18:20"을 알파벳·시각으로 읽지 않게). */
internal fun todaySpoken(res: Resources, dashboard: Dashboard?): String {
    val km = res.getString(R.string.home_today_spoken_km, RunkkFormat.km(dashboard?.today ?: Distance.ZERO))
    if (dashboard == null || dashboard.todayRuns <= 0) return res.getString(R.string.home_today_spoken_not_run, km)
    return res.getString(
        R.string.home_today_spoken_runs,
        km,
        RunkkFormat.durationSpoken(res, dashboard.todayElapsed),
        dashboard.todayRuns
    )
}

/**
 * 이번 주 칸 읽기 문구. weeklyAchievement는 거리 달성 비율(0.6 = 60%, 100%를 넘을 수 있다)이라 거리 기준임을 밝히고,
 * 횟수 목표가 있으면 덧붙인다.
 */
internal fun weeklySpoken(res: Resources, dashboard: Dashboard): String {
    val achievement = dashboard.weeklyAchievement ?: return res.getString(R.string.home_summary_set_goal)
    val weekly = dashboard.weekly
    return listOfNotNull(
        res.getString(R.string.home_week_spoken_distance, (achievement * PERCENT).roundToInt()),
        weekly?.let { res.getString(R.string.home_week_spoken_runs, it.goalRuns, it.actualRuns) }
    ).joinToString(", ")
}

private const val PERCENT = 100
private const val OVERLAY_ALPHA = 0.78f
private const val SHADOW_ALPHA = 0.28f
private val ROOM_CORNER = 28.dp
private val AVATAR_SIZE = 168.dp

/** 우리 집 위쪽(바깥 여백 32 + 로고 24 + 상단 48 + 사이 32). */
private val TOP_PART_HEIGHT = 136.dp

/** 우리 집 최소 높이: 두 칸 + 아바타 + 러닝 패널이 겹치지 않는 높이. */
private val ROOM_MIN_HEIGHT = 560.dp

@Preview(name = "A5 홈", widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    val today = LocalDate.of(2026, 10, 20)
    RunkkTheme(reduceMotion = true) {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                today = today,
                dashboard = Dashboard(
                    today = Distance(3_020),
                    total = Distance(128_400),
                    landmark = "한강 다리 12개",
                    streakDays = 4,
                    grass = emptyMap(),
                    weeklyAchievement = 0.6,
                    crew = null
                ),
                freeRunGoal = FreeRunGoal(PlanSnapshot(Distance(5_000), Pace(330)), isFromCoachPlan = true),
                header = HomeHeader("달리는곰", "골드", 3, 1_250),
                isHeaderLoading = false
            ),
            onModeSelected = {},
            onNavigate = {},
            onRetry = {}
        )
    }
}

/** 우리 집 아바타 자리의 얼굴 그림(시험이 찾는 표지). */
internal const val HOME_FACE_TAG = "home-avatar-face"
