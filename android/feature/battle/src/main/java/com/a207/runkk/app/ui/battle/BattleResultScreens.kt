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

/**
 * 대결 결과. "기록 카드"는 결과의 본인 기록 ID(recordId)로 러닝 카드를 열고, 기록 ID가 아직 없으면(업로드 대기) "기록 목록"으로
 * 간다. "홈으로"는 홈으로 간다.
 */
@Composable
fun BattleResultRoute(battleId: Long, viewModel: BattleResultViewModel = hiltViewModel()) {
    LaunchedEffect(battleId) { viewModel.load(battleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BattleResultScreen(
        state = state,
        onHistory = { navigator.navigate(Routes.RECORDS_BATTLES) },
        onCard = {
            val recordId = state.result?.recordId
            if (recordId !=
                null
            ) {
                navigator.navigate(Routes.RecordCard(recordId))
            } else {
                navigator.navigate(Routes.RECORDS)
            }
        },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(battleId, isForced = true) }
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
                stringResource(R.string.battle_result_title),
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            val result = state.result
            when {
                result == null && state.isLoading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }

                result == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.battle_result_load_failed),
                    onRetry = onRetry
                )

                result.isPending && state.isAwaitingVerdict -> AwaitingVerdict()

                result.isPending -> Pending(onRetry)

                else -> ResultBody(state, result, onHistory)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        BottomActions(state.result?.recordId != null, onCard, onHome)
    }
}

/** 하단 버튼: 기록 카드(기록 ID가 없으면 기록 목록)와 홈으로. */
@Composable
private fun BottomActions(hasRecord: Boolean, onCard: () -> Unit, onHome: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkSecondaryButton(
            stringResource(if (hasRecord) R.string.battle_result_record_card else R.string.battle_result_record_list),
            onClick = onCard,
            modifier = Modifier.weight(1f)
        )
        RunkkPrimaryButton(
            stringResource(R.string.battle_result_home),
            onClick = onHome,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 판정을 기다리는 중: 도착 직후 기록이 올라가 판정이 나올 때까지 보인다. */
@Composable
private fun AwaitingVerdict() {
    val waiting = stringResource(R.string.battle_result_waiting)
    RunkkCard(borderColor = RunkkColors.Lavender) {
        Box(Modifier.fillMaxWidth().height(96.dp)) { RunkkLoading(label = waiting) }
        Text(
            waiting,
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 다시 읽기를 멈춘 뒤에도 판정이 없다: 직접 다시 읽게 한다. */
@Composable
private fun Pending(onRetry: () -> Unit) {
    RunkkCard(modifier = Modifier.fillMaxWidth(), borderColor = RunkkColors.Lavender) {
        Text(
            stringResource(R.string.battle_result_late),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.Lavender,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        RunkkPillButton(
            stringResource(R.string.battle_result_retry),
            onClick = onRetry,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ResultBody(state: BattleResultUiState, result: BattleResult, onHistory: () -> Unit) {
    VerdictCard(state.verdict, verdictMessage(state.verdict, result).asString())
    if (result.isNewRecord) RunkkBanner(stringResource(R.string.battle_result_new_record), isStrong = true)
    ComparisonTable(result.rows)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkStatTile(
            stringResource(R.string.battle_result_reward),
            StatTileValue(result.rewardPoints?.let { "+$it" } ?: "+0"),
            style = StatTileStyle(valueStyle = RunkkTextStyles.display(28), valueColor = RunkkColors.Lime),
            modifier = Modifier.weight(1f)
        )
        val weekly = state.weekly
        RunkkStatTile(
            stringResource(R.string.battle_result_weekly),
            StatTileValue(
                weekly?.let { weeklyLabel(it).asString() } ?: "-",
                spoken =
                    weekly?.let { weeklySpoken(it).asString() } ?: stringResource(R.string.battle_result_weekly_none)
            ),
            style = StatTileStyle(valueStyle = RunkkTextStyles.text(24, FontWeight.Black)),
            modifier = Modifier.weight(1f)
        )
    }
    RunkkTextLink(
        stringResource(R.string.battle_result_history_link),
        onClick = onHistory,
        contentDescription = stringResource(R.string.battle_result_history_link_desc)
    )
}

@Composable
private fun VerdictCard(verdict: Verdict?, message: String) {
    val background = when (verdict) {
        Verdict.WIN -> RunkkColors.Lime
        Verdict.LOSE -> RunkkColors.Lavender
        else -> RunkkColors.Surface
    }
    val content = if (verdict == null || verdict == Verdict.DRAW) RunkkColors.TextPrimary else RunkkColors.OnLime
    val description = stringResource(
        R.string.battle_result_verdict_desc,
        stringResource(verdict?.labelRes ?: R.string.battle_result_verdict_unknown),
        message
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = description
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
            Text(
                stringResource(verdict?.headlineRes ?: R.string.battle_result_verdict_unknown),
                style = RunkkTextStyles.text(40, FontWeight.Black),
                color = content
            )
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
        TableRow(
            stringResource(R.string.battle_result_col_item),
            stringResource(R.string.battle_me),
            stringResource(R.string.battle_opponent),
            isHeader = true
        )
        rows.forEach { row ->
            HorizontalDivider(color = RunkkColors.Border)
            TableRow(row.item, row.mine, row.theirs, isHeader = false)
        }
    }
}

@Composable
private fun TableRow(item: String, mine: String, theirs: String, isHeader: Boolean) {
    val description = stringResource(
        R.string.battle_result_row_desc,
        item,
        spokenValue(item, mine),
        spokenValue(item, theirs)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                if (!isHeader) contentDescription = description
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item,
            style = if (isHeader) RunkkTextStyles.Caption else RunkkTextStyles.Label,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(WEIGHT_ITEM)
        )
        Cell(mine, isHeader, RunkkColors.Lime)
        Cell(theirs, isHeader, if (isHeader) RunkkColors.TextSecondary else RunkkColors.TextPrimary)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, isHeader: Boolean, color: Color) {
    Text(
        text,
        style = if (isHeader) RunkkTextStyles.text(12, FontWeight.Bold) else cellStyle(text),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
    )
}

/** 비교표 값 읽기: "27:30" → "27분 30초", 페이스·1km 항목은 "1킬로미터당 …". 그 밖은 그대로. */
@Composable
internal fun spokenValue(item: String, value: String): String {
    val parts = TIME_VALUE.matchEntire(value)?.groupValues ?: return value
    val (a, b, c) = Triple(parts[1].toInt(), parts[2].toInt(), parts[3].toIntOrNull())
    val text = if (c != null) {
        stringResource(R.string.battle_result_spoken_hms, a, b, c)
    } else {
        stringResource(R.string.battle_result_spoken_ms, a, b)
    }
    val perKm = stringResource(R.string.battle_result_pace_keyword) in item ||
        stringResource(R.string.battle_result_per_km_keyword) in item
    return if (perKm) stringResource(R.string.battle_result_spoken_per_km, text) else text
}

private val TIME_VALUE = Regex("""(\d+):(\d{2})(?::(\d{2}))?""")

/** 숫자는 Barlow, "공개 안 됨" 같은 한글은 본문 글꼴. */
private fun cellStyle(text: String) =
    if (text.any { it in '가'..'힣' }) RunkkTextStyles.Caption else RunkkTextStyles.display(22)

private const val WEIGHT_ITEM = 1.2f
private const val DECOR_ALPHA = 0.18f

@Preview(name = "대결 결과", widthDp = 390, heightDp = 844)
@Composable
private fun BattleResultPreview() {
    RunkkTheme(reduceMotion = true) {
        BattleResultScreen(
            BattleResultUiState(
                isLoading = false,
                result = BattleResult(
                    1,
                    false,
                    "WIN",
                    listOf(BattleResult.Row("기록", "27:30", "28:12")),
                    40,
                    false,
                    opponentName = "바람처럼",
                    recordId = "r-1",
                    myReach = java.time.Duration.ofSeconds(1_650),
                    opponentReach = java.time.Duration.ofSeconds(1_692),
                    weekly = BattleResult.Weekly(3, 1, 0)
                )
            ),
            {},
            {},
            {},
            {}
        )
    }
}
