package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 필터 결과 건수(작게, 바뀌면 보조 기술에 알린다). */
@Composable
internal fun FilterCount(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** 함께 달린 기록(FR-101). 항목을 누르면 그 대결 결과. 끝까지 내리면 다음 쪽을 이어 받는다. */
@Composable
fun BattleHistoryRoute(viewModel: BattleHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BattleHistoryScreen(
        state = state,
        onBack = navigator::back,
        onFilter = viewModel::onFilter,
        onEntry = { navigator.navigate(Routes.BattleResult(it)) },
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun BattleHistoryScreen(
    state: BattleHistoryUiState,
    onBack: () -> Unit,
    onFilter: (Int) -> Unit,
    onEntry: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onMessageShown: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - HISTORY_LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.history?.nextCursor, state.history?.entries?.size) {
        if (nearEnd && state.history?.nextCursor != null) onLoadMore()
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        HistoryList(state, listState, onBack, onFilter, onEntry, onRetry)
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

/** 끝에서 이만큼 안쪽 줄이 보이면 다음 쪽을 읽는다. */
private const val HISTORY_LOAD_AHEAD = 3

@Composable
private fun HistoryList(
    state: BattleHistoryUiState,
    listState: LazyListState,
    onBack: () -> Unit,
    onFilter: (Int) -> Unit,
    onEntry: (Long) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.battle_history_title), onBack = onBack)
        val history = state.history
        val filterLabels = HISTORY_FILTERS.map { stringResource(it.first) }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { HistoryStats(history) }
            item {
                RunkkSegmentedTabs(
                    options = filterLabels,
                    selectedIndex = state.filterIndex,
                    onSelect = onFilter,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
            // 필터를 바꿔 목록이 바뀌면 건수를 알린다(보이는 목록이 바뀐 것을 보조 기술 사용자도 알게).
            if (history != null && !state.isLoading && state.error == null) {
                item { FilterCount(stringResource(R.string.battle_history_count, history.total)) }
            }
            historyBody(state, onEntry, onRetry)
        }
    }
}

/** 이력 통계 세 칸(H4): 대결, 승, 패. */
@Composable
private fun HistoryStats(history: BattleHistory?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.battle_history_stat_battles to history?.total,
            R.string.battle_history_stat_wins to history?.wins,
            R.string.battle_history_stat_losses to history?.losses
        ).forEach { (label, value) ->
            RunkkStatTile(
                stringResource(label),
                StatTileValue(value?.toString() ?: "-"),
                style = StatTileStyle(valueStyle = RunkkTextStyles.DisplaySmall, isCentered = true),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 이력 목록 본문: 읽는 중·오류·빈 목록·항목(다음 쪽 읽는 중 표시 포함). */
private fun LazyListScope.historyBody(state: BattleHistoryUiState, onEntry: (Long) -> Unit, onRetry: () -> Unit) {
    val history = state.history
    when {
        history == null && state.isLoading -> item {
            Box(Modifier.fillMaxWidth().height(200.dp)) { RunkkLoading() }
        }

        history == null || state.error != null -> item {
            RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.battle_history_load_failed),
                onRetry = onRetry
            )
        }

        history.entries.isEmpty() -> item {
            RunkkEmptyState(
                stringResource(R.string.battle_history_empty_title),
                message = stringResource(R.string.battle_history_empty_message)
            )
        }

        else -> {
            items(history.entries, key = { it.battleId }) { entry ->
                HistoryRow(entry, onClick = { onEntry(entry.battleId) })
            }
            if (state.isLoadingMore) {
                item { Box(Modifier.fillMaxWidth().height(64.dp)) { RunkkLoading() } }
            }
        }
    }
}

/** 이력 한 줄(H4): "10.20, 랜덤, 5km", 상대 이름, 승·패 칩. 누르면 그 대결 결과. */
@Composable
private fun HistoryRow(entry: BattleHistory.Entry, onClick: () -> Unit) {
    val verdict = Verdict.of(entry.verdict)
    val playedFormat = DateTimeFormatter.ofPattern(stringResource(R.string.battle_history_played_pattern))
    val opponent = when (entry.type) {
        BattleHistory.Type.SELF -> stringResource(R.string.battle_history_self_opponent)
        BattleHistory.Type.GHOST -> stringResource(R.string.battle_history_ghost_opponent, entry.opponentName)
        else -> entry.opponentName
    }
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.battle_history_open_result),
        shape = RunkkShapes.Medium
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    listOf(
                        entry.playedAt.atZone(ZoneId.systemDefault()).format(playedFormat),
                        stringResource(typeLabel(entry.type)),
                        distanceLabel(entry.distance).asString()
                    ).joinToString(", "),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    opponent,
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RunkkBadge(
                stringResource(verdict?.labelRes ?: R.string.battle_pending),
                tone = if (verdict == Verdict.WIN) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
            )
        }
    }
}
