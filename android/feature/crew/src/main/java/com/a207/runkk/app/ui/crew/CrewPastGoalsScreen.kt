package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.PastGoals
import java.util.Locale

@Composable
fun CrewPastGoalsRoute(key: Routes.CrewPastGoals, viewModel: CrewPastGoalsViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key.crewId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CrewPastGoalsScreen(
        state,
        onBack = navigator::back,
        onRetry = viewModel::load,
        onLoadMore = viewModel::onLoadMore,
        onMessageShown = viewModel::onMessageShown
    )
}

/** 지난 목표(피그마 F-21): 가운데 제목·뒤로, 주 카드 목록(최신 주부터, 간격 12). 끝 근처까지 내리면 다음 쪽을 읽는다. */
@Composable
fun CrewPastGoalsScreen(
    state: CrewPastGoalsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                list.layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(nearEnd, state.nextBefore) { if (nearEnd && state.nextBefore != null) onLoadMore() }
    Box(modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.crew_past_goals_title), onBack = onBack)
            val error = state.error
            when {
                error != null -> RunkkErrorState(error.asString(), onRetry = onRetry)

                state.isLoading && state.goals.isEmpty() -> RunkkLoading(height = 160.dp)

                state.isLoaded && state.goals.isEmpty() -> RunkkEmptyState(
                    stringResource(R.string.crew_past_goals_empty)
                )

                else -> LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 11.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.goals, key = { it.weekStart.toString() }) { PastGoalCard(it) }
                    if (state.isLoadingMore) {
                        item(key = "more") {
                            RunkkLoading(height = 64.dp)
                        }
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 지난 목표 카드(피그마 F-21): "10월 3주차" + "달성/달성 실패", "72.0 / 80 km" + "90%", 막대(100% 넘으면 가득), "참여 6명".
 * 보조 기술에는 한 문장으로 읽힌다.
 */
@Composable
private fun PastGoalCard(goal: PastGoals.Goal) {
    val result =
        stringResource(if (goal.isAchieved) R.string.crew_past_goals_achieved else R.string.crew_past_goals_failed)
    val total = RunkkFormat.kmShort(goal.total)
    val target = targetKm(goal.goal)
    PastGoalWeekCard(
        week = stringResource(R.string.crew_past_goals_week, goal.month, goal.weekOfMonth),
        status = result,
        total = total,
        goalLabel = stringResource(R.string.crew_past_goals_goal, target),
        percent = stringResource(R.string.crew_past_goals_percent, goal.progressPercent),
        progress = goal.progressPercent.coerceIn(0, FULL_PERCENT) / FULL_PERCENT.toFloat(),
        participants = stringResource(R.string.crew_past_goals_participants, goal.participantCount),
        spoken = stringResource(
            R.string.crew_past_goals_spoken,
            goal.month,
            goal.weekOfMonth,
            result,
            target,
            total,
            goal.progressPercent,
            goal.participantCount
        )
    )
}

/**
 * 지난 목표 한 주 카드(피그마 F-07·F-21 공용): 투명 바탕 + 테두리 1, 모서리 28, 안쪽 14, 간격 8. 주 이름 13/16과 오른쪽 결과 12/18 Bold,
 * 합산 22/26 Bold + 「/ 80 km」 13/16 + 진행률 15 Bold, 8 두께 막대(barCrew, [progress]가 null이면 없음), 참여 인원 12/18.
 * 보조 기술에는 [spoken] 한 문장으로 읽힌다.
 */
@Composable
internal fun PastGoalWeekCard(
    week: String,
    status: String,
    total: String,
    goalLabel: String,
    percent: String?,
    progress: Float?,
    participants: String,
    spoken: String
) {
    RunkkCard(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        shape = RunkkShapes.Dialog,
        contentPadding = PaddingValues(14.dp),
        borderColor = RunkkColors.Border,
        background = Color.Transparent
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                week,
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                status,
                style = RunkkTextStyles.text(12, FontWeight.Bold, lineHeight = 18),
                color = RunkkColors.TextPrimary
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                total,
                style = RunkkTextStyles.text(22, FontWeight.Bold, lineHeight = 26),
                color = RunkkColors.TextPrimary
            )
            Text(
                goalLabel,
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f).padding(bottom = 3.dp)
            )
            percent?.let {
                Text(it, style = RunkkTextStyles.text(15, FontWeight.Bold), color = RunkkColors.TextPrimary)
            }
        }
        progress?.let {
            RunkkProgressBar(it, height = 8.dp, color = RunkkColors.BarCrew, trackColor = RunkkColors.SurfaceHigh)
        }
        Text(participants, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary)
    }
}

private const val FULL_PERCENT = 100

/** 목표 km: 정수면 "80", 아니면 소수 첫째 자리 "12.5". */
internal fun targetKm(distance: Distance): String {
    val km = distance.meters / 1000.0
    return if (distance.meters % 1000 == 0L) km.toLong().toString() else String.format(Locale.US, "%.1f", km)
}
