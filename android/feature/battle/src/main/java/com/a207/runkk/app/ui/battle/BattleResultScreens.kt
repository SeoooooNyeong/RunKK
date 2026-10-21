package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleResult
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 대결 결과. "기록 카드"는 대결 결과에 기록 ID가 없어 기록 목록으로, "홈으로"는 홈으로 간다. */
@Composable
fun BattleResultRoute(battleId: Long, viewModel: BattleResultViewModel = hiltViewModel()) {
    LaunchedEffect(battleId) { viewModel.load(battleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BattleResultScreen(
        state = state,
        onHistory = { navigator.navigate(Routes.BATTLE_HISTORY) },
        onCard = { navigator.navigate(Routes.RECORDS) },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(battleId, force = true) }
    )
}

@Composable
fun BattleResultScreen(
    state: BattleResultUiState,
    onHistory: () -> Unit,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "BATTLE RESULT",
                style = RunkkTextStyles.Overline,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.semantics { heading() }
            )
            val result = state.result
            when {
                result == null && state.loading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }
                result == null -> RunkkErrorState(state.error ?: "결과를 불러오지 못했어요", onRetry = onRetry)
                result.pending -> Pending(onRetry)
                else -> ResultBody(state, result, onHistory)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkSecondaryButton("기록 카드", onClick = onCard, modifier = Modifier.weight(1f))
            RunkkPrimaryButton("홈으로", onClick = onHome, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Pending(onRetry: () -> Unit) {
    RunkkCard(borderColor = RunkkColors.Lavender) {
        Text("보류", style = RunkkTextStyles.display(56), color = RunkkColors.Lavender)
        Text(
            "먼저 도착한 기록이 아직 서버에 없어요. 기록이 올라오면 결과를 확정하고 알려 드려요(최대 24시간).",
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary
        )
        Text(
            "내 기록이 아직 전송 대기 중이면 연결되었을 때 자동으로 보내요.",
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        RunkkPillButton("결과 다시 보기", onClick = onRetry)
    }
}

@Composable
private fun ResultBody(state: BattleResultUiState, result: BattleResult, onHistory: () -> Unit) {
    VerdictCard(state.verdict)
    if (result.newRecord) RunkkBanner("신기록! 이 거리의 새 최고 기록이에요", strong = true)
    ComparisonTable(result.rows)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            "적립 리워드",
            result.rewardPoints?.let { "+$it" } ?: "+0",
            valueColor = RunkkColors.Lime,
            valueStyle = RunkkTextStyles.display(28),
            modifier = Modifier.weight(1f)
        )
        RunkkStatTile(
            "전적",
            if (state.wins != null && state.losses != null) "${state.wins}W ${state.losses}L" else "-",
            spoken = if (state.wins != null && state.losses != null) "${state.wins}승 ${state.losses}패" else "전적 없음",
            valueStyle = RunkkTextStyles.display(28),
            modifier = Modifier.weight(1f)
        )
    }
    RunkkTextLink("함께 달린 기록 보기 →", onClick = onHistory, contentDescription = "함께 달린 기록 보기")
}

@Composable
private fun VerdictCard(verdict: Verdict?) {
    val background = when (verdict) {
        Verdict.WIN -> RunkkColors.Lime
        Verdict.LOSE -> RunkkColors.Lavender
        else -> RunkkColors.Surface
    }
    val content = if (verdict == null || verdict == Verdict.DRAW) RunkkColors.TextPrimary else RunkkColors.OnLime
    val message = when (verdict) {
        Verdict.WIN -> "상대보다 먼저 도착했어요!"
        Verdict.LOSE -> "아쉽게 졌어요. 다음 대결에서 다시 만나요"
        Verdict.DRAW -> "1초 안에 함께 도착했어요. 무승부!"
        null -> "결과를 확인하고 있어요"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = "${verdict?.label ?: "결과"}. $message"
            }
    ) {
        Text(
            when (verdict) {
                Verdict.WIN -> "WIN"
                Verdict.LOSE -> "LOSE"
                else -> "DRAW"
            },
            style = RunkkTextStyles.display(136),
            color = content.copy(alpha = DECOR_ALPHA),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-24).dp).clearAndSetSemantics {}
        )
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(verdict?.headline ?: "RESULT", style = RunkkTextStyles.display(56), color = content)
            Text(message, style = RunkkTextStyles.text(16, FontWeight.Black), color = content)
        }
    }
}

@Composable
private fun ComparisonTable(rows: List<BattleResult.Row>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Large)
    ) {
        TableRow("항목", "나", "상대", header = true)
        rows.forEach { row ->
            HorizontalDivider(color = RunkkColors.Border)
            TableRow(row.item, row.mine, row.theirs, header = false)
        }
    }
}

@Composable
private fun TableRow(item: String, mine: String, theirs: String, header: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                if (!header) contentDescription = "$item, 나 ${spokenValue(item, mine)}, 상대 ${spokenValue(item, theirs)}"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item,
            style = if (header) RunkkTextStyles.Caption else RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(WEIGHT_ITEM)
        )
        Cell(mine, header, RunkkColors.Lime)
        Cell(theirs, header, if (header) RunkkColors.TextSecondary else RunkkColors.TextPrimary)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, header: Boolean, color: Color) {
    Text(
        text,
        style = if (header) RunkkTextStyles.text(12, FontWeight.Bold) else cellStyle(text),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
    )
}

/** 비교표 값 읽기: "27:30" → "27분 30초", 페이스·1km 항목은 "1킬로미터당 …". 그 밖은 그대로. */
internal fun spokenValue(item: String, value: String): String {
    val parts = TIME_VALUE.matchEntire(value)?.groupValues ?: return value
    val (a, b, c) = Triple(parts[1].toInt(), parts[2].toInt(), parts[3].toIntOrNull())
    val text = if (c != null) "${a}시간 ${b}분 ${c}초" else "${a}분 ${b}초"
    return if ("페이스" in item || "1km" in item) "1킬로미터당 $text" else text
}

private val TIME_VALUE = Regex("""(\d+):(\d{2})(?::(\d{2}))?""")

/** 숫자는 Barlow, "공개 안 됨" 같은 한글은 본문 글꼴. */
private fun cellStyle(text: String) =
    if (text.any { it in '가'..'힣' }) RunkkTextStyles.Caption else RunkkTextStyles.display(22)

private const val WEIGHT_ITEM = 1.2f
private const val DECOR_ALPHA = 0.18f

/** 함께 달린 기록(FR-101). 항목을 누르면 그 대결 결과. */
@Composable
fun BattleHistoryRoute(viewModel: BattleHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BattleHistoryScreen(
        state = state,
        onBack = navigator::back,
        onFilter = viewModel::onFilter,
        onEntry = { navigator.navigate(Routes.BattleResult(it)) },
        onRetry = viewModel::load
    )
}

@Composable
fun BattleHistoryScreen(
    state: BattleHistoryUiState,
    onBack: () -> Unit,
    onFilter: (Int) -> Unit,
    onEntry: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = "함께 달린 기록", onBack = onBack)
        val history = state.history
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunkkStatTile(
                        "대결",
                        history?.total?.toString() ?: "-",
                        centered = true,
                        valueStyle = RunkkTextStyles.DisplaySmall,
                        modifier = Modifier.weight(1f)
                    )
                    RunkkStatTile(
                        "승·패",
                        history?.let { "${it.wins}W ${it.losses}L" } ?: "-",
                        spoken = history?.let { "${it.wins}승 ${it.losses}패" },
                        centered = true,
                        valueStyle = RunkkTextStyles.DisplaySmall,
                        modifier = Modifier.weight(1f)
                    )
                    RunkkStatTile(
                        "셀프 신기록",
                        history?.selfNewRecords?.toString() ?: "-",
                        centered = true,
                        valueStyle = RunkkTextStyles.DisplaySmall,
                        valueColor = RunkkColors.Lime,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item {
                RunkkSegmentedTabs(
                    options = HISTORY_FILTERS.map { it.first },
                    selectedIndex = state.filterIndex,
                    onSelect = onFilter,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }
            when {
                history == null && state.loading -> item {
                    Box(Modifier.fillMaxWidth().height(200.dp)) { RunkkLoading() }
                }

                history == null || state.error != null -> item {
                    RunkkErrorState(state.error ?: "기록을 불러오지 못했어요", onRetry = onRetry)
                }

                history.entries.isEmpty() -> item {
                    RunkkEmptyState("아직 함께 달린 기록이 없어요", message = "배틀런이나 나와의 싸움을 시작해 보세요")
                }

                else -> items(history.entries, key = { it.battleId }) { entry ->
                    HistoryRow(entry, onClick = { onEntry(entry.battleId) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: BattleHistory.Entry, onClick: () -> Unit) {
    val verdict = Verdict.of(entry.verdict)
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = "대결 결과 보기",
        shape = RunkkShapes.Medium
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunkkBadge(typeLabel(entry.type))
                    Text(
                        entry.playedAt.atZone(ZoneId.systemDefault()).format(PLAYED),
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                }
                Text(
                    "${entry.opponentName} · ${distanceLabel(entry.distance)}",
                    style = RunkkTextStyles.text(15, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RunkkBadge(
                verdict?.label ?: "보류",
                tone = if (verdict == Verdict.WIN) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
            )
        }
    }
}

private val PLAYED: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

@Preview(name = "대결 결과", widthDp = 390, heightDp = 844)
@Composable
private fun BattleResultPreview() {
    RunkkTheme(reduceMotion = true) {
        BattleResultScreen(
            BattleResultUiState(
                loading = false,
                result = BattleResult(
                    1,
                    false,
                    "WIN",
                    listOf(BattleResult.Row("기록", "27:30", "28:12")),
                    40,
                    false
                ),
                wins = 3,
                losses = 1
            ),
            {},
            {},
            {},
            {}
        )
    }
}
