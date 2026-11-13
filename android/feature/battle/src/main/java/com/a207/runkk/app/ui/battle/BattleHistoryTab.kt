package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.StatTileLabelPosition
import com.a207.runkk.app.core.designsystem.StatTileStyle
import com.a207.runkk.app.core.designsystem.StatTileValue
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleHistory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 기록 화면 "대결" 탭(G-10, D-281, FR-101). BattleFeatureEntry가 칸 SlotKey.RECORD_BATTLES에 넣는다. 머리 통계(대결·승·패),
 * 필터 칩(전체·배틀런·셀프런·셀럽런), 행(날짜·종류·거리, 상대, 승·패 배지)을 그리고 행을 누르면 그 대결 결과를 연다.
 * 끝까지 내리면 다음 쪽을 이어 받는다.
 */
@Composable
fun BattleHistoryTab(viewModel: BattleHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BattleHistoryTabContent(
        state = state,
        onFilter = viewModel::onFilter,
        onEntry = { navigator.navigate(Routes.BattleResult(it)) },
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun BattleHistoryTabContent(
    state: BattleHistoryUiState,
    onFilter: (Int) -> Unit,
    onEntry: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    onMessageShown: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val isNearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - HISTORY_LOAD_AHEAD
        }
    }
    LaunchedEffect(isNearEnd, state.history?.nextCursor, state.history?.entries?.size) {
        if (isNearEnd && state.history?.nextCursor != null) onLoadMore()
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
        ) {
            item(key = "stats") { HistoryStats(state.history) }
            item(key = "filters") { HistoryFilters(state.filterIndex, onFilter) }
            historyRows(state, onEntry, onRetry)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

/** 끝에서 이만큼 안쪽 줄이 보이면 다음 쪽을 읽는다. */
private const val HISTORY_LOAD_AHEAD = 3

/** 머리 통계 세 칸: "[n] 대결", "[n] 승", "[n] 패"(이력 응답의 total·wins·losses). 값 18 Bold 위, 라벨 12 아래. */
@Composable
private fun HistoryStats(history: BattleHistory?) {
    val style = StatTileStyle(
        valueStyle = RunkkTextStyles.text(18, FontWeight.Bold),
        valueColor = RunkkColors.TextPrimary,
        isCentered = true,
        shape = RectangleShape,
        contentPadding = PaddingValues(0.dp),
        labelPosition = StatTileLabelPosition.Bottom,
        labelStyle = RunkkTextStyles.Caption,
        background = Color.Transparent,
        spacing = 4.dp
    )
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        listOf(
            R.string.battle_history_stat_battles to history?.total,
            R.string.battle_history_stat_wins to history?.wins,
            R.string.battle_history_stat_losses to history?.losses
        ).forEach { (label, value) ->
            RunkkStatTile(
                label = stringResource(label),
                value = StatTileValue(value?.toString() ?: "-"),
                modifier = Modifier.weight(1f),
                style = style
            )
        }
    }
}

/** 필터 칩: 전체·배틀런(친구 대결 포함)·셀프런·셀럽런. */
@Composable
private fun HistoryFilters(selectedIndex: Int, onFilter: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HISTORY_FILTERS.forEachIndexed { index, (label, _) ->
            RunkkChoiceChip(
                label = stringResource(label),
                isSelected = index == selectedIndex,
                onSelectedChange = { onFilter(index) }
            )
        }
    }
}

/** 이력 행: 읽는 중·오류·빈 목록·항목(다음 쪽 읽는 중 표시 포함). */
private fun LazyListScope.historyRows(state: BattleHistoryUiState, onEntry: (Long) -> Unit, onRetry: () -> Unit) {
    val history = state.history
    when {
        history == null && state.isLoading -> item(key = "loading") {
            RunkkLoading(height = 200.dp)
        }

        history == null || state.error != null -> item(key = "error") {
            RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.battle_history_load_failed),
                onRetry = onRetry
            )
        }

        history.entries.isEmpty() -> item(key = "empty") {
            RunkkEmptyState(
                stringResource(R.string.battle_history_empty_title),
                message = stringResource(R.string.battle_history_tab_empty_message)
            )
        }

        else -> {
            items(history.entries, key = { it.battleId }) { entry ->
                HistoryRow(entry, onClick = { onEntry(entry.battleId) })
            }
            if (state.isLoadingMore) {
                item(key = "more") { RunkkLoading(height = 64.dp) }
            }
        }
    }
}

/** 행: "10월 20일, 배틀런, 5km", 상대(닉네임·"10월 2일의 나"·셀럽 이름), 승·패 배지. 판정 전이면 배지 없음. */
@Composable
private fun HistoryRow(entry: BattleHistory.Entry, onClick: () -> Unit) {
    val pattern = stringResource(R.string.battle_history_date_pattern)
    val dateFormat = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
    val verdict = Verdict.of(entry.verdict)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.battle_history_open_result),
                    onClick = onClick
                )
                .padding(vertical = 12.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        R.string.battle_history_row_caption,
                        entry.playedAt.atZone(ZoneId.systemDefault()).format(dateFormat),
                        historyTypeText(entry).asString(),
                        distanceLabel(entry.distance).asString()
                    ),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    entry.opponentName,
                    style = RunkkTextStyles.text(15, FontWeight.Medium),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (verdict != null) VerdictBadge(verdict)
        }
        HorizontalDivider(color = RunkkColors.Border)
    }
}

/** 승·패·무 배지(알약): 승은 본문색 채움(반전), 나머지는 표면. */
@Composable
private fun VerdictBadge(verdict: Verdict) {
    RunkkBadge(
        stringResource(verdict.labelRes),
        tone = if (verdict == Verdict.WIN) RunkkBadgeTone.Inverted else RunkkBadgeTone.Neutral,
        shape = RunkkShapes.Pill
    )
}

@Preview(name = "G-10 대결 탭", widthDp = 390, heightDp = 700)
@Composable
private fun BattleHistoryTabPreview() {
    val played = Instant.parse("2026-10-20T10:00:00Z")
    RunkkTheme(reduceMotion = true) {
        BattleHistoryTabContent(
            BattleHistoryUiState(
                isLoading = false,
                history = BattleHistory(
                    entries = listOf(
                        BattleHistory.Entry(1, BattleHistory.Type.RANDOM, "바람처럼", Distance(5_000), "WIN", played),
                        BattleHistory.Entry(2, BattleHistory.Type.SELF, "10월 2일의 나", Distance(5_000), "LOSE", played),
                        BattleHistory.Entry(3, BattleHistory.Type.CELEBRITY, "가수 P.", Distance(5_000), "WIN", played)
                    ),
                    total = 24,
                    wins = 15,
                    losses = 9,
                    selfNewRecords = 0
                )
            ),
            onFilter = {},
            onEntry = {},
            onRetry = {}
        )
    }
}
