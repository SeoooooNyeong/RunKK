package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
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

                state.isLoading && state.goals.isEmpty() -> Box(Modifier.fillMaxWidth().height(160.dp)) {
                    RunkkLoading()
                }

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
                            Box(Modifier.fillMaxWidth().height(64.dp)) { RunkkLoading() }
                        }
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 지난 목표 카드(피그마 F-21): 바탕 없음, divider 테두리, 모서리 28, 안쪽 14, 간격 8. "10월 3주차" + "달성/달성 실패", "72.0 / 80 km" +
 * "90%", 막대(트랙 8 surfaceHigh, 채움 barCrew, 100% 넘으면 가득), "참여 6명". 보조 기술에는 한 문장으로 읽힌다.
 */
@Composable
private fun PastGoalCard(goal: PastGoals.Goal) {
    val shape = RoundedCornerShape(28.dp)
    val result =
        stringResource(if (goal.isAchieved) R.string.crew_past_goals_achieved else R.string.crew_past_goals_failed)
    val total = RunkkFormat.kmShort(goal.total)
    val target = targetKm(goal.goal)
    val spoken = stringResource(
        R.string.crew_past_goals_spoken,
        goal.month,
        goal.weekOfMonth,
        result,
        target,
        total,
        goal.progressPercent,
        goal.participantCount
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, RunkkColors.Border, shape)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.crew_past_goals_week, goal.month, goal.weekOfMonth),
                style = RunkkTextStyles.text(13),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(result, style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.TextPrimary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                buildAnnotatedString {
                    withStyle(RunkkTextStyles.text(22, FontWeight.Bold, lineHeight = 26).toSpanStyle()) {
                        append(total)
                    }
                    append(" ")
                    withStyle(RunkkTextStyles.text(13).toSpanStyle()) {
                        append(stringResource(R.string.crew_past_goals_goal, target))
                    }
                },
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_past_goals_percent, goal.progressPercent),
                style = RunkkTextStyles.text(15, FontWeight.Bold),
                color = RunkkColors.TextPrimary
            )
        }
        GoalBar(goal.progressPercent)
        Text(
            stringResource(R.string.crew_past_goals_participants, goal.participantCount),
            style = RunkkTextStyles.text(12),
            color = RunkkColors.TextPrimary
        )
    }
}

/** 진행 막대(피그마 `RunKK / Race / 막대 채움` Style=Crew): 트랙 8 surfaceHigh 모서리 4, 채움 barCrew. 100 넘으면 가득. */
@Composable
private fun GoalBar(percent: Int) {
    val fraction = percent.coerceIn(0, FULL_PERCENT) / FULL_PERCENT.toFloat()
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(RunkkColors.SurfaceHigh)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(4.dp))
                .background(RunkkColors.BarCrew)
        )
    }
}

private const val FULL_PERCENT = 100

/** 목표 km: 정수면 "80", 아니면 소수 첫째 자리 "12.5". */
internal fun targetKm(distance: Distance): String {
    val km = distance.meters / 1000.0
    return if (distance.meters % 1000 == 0L) km.toLong().toString() else String.format(Locale.US, "%.1f", km)
}
