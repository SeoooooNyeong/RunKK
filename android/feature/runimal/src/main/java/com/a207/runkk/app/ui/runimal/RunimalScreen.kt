package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import kotlin.math.roundToInt

/** MY Runimal: 유형 카드·남은 횟수·16유형·카드 공유. */
@Composable
fun RunimalRoute(viewModel: RunimalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RunimalScreen(
        state = state,
        onBack = navigator::back,
        onShare = viewModel::onShare,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun RunimalScreen(
    state: RunimalUiState,
    onBack: () -> Unit,
    onShare: (RunimalShareKind) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = "MY Runimal", onBack = onBack)
            val status = state.status
            when {
                status == null && state.loading -> RunkkLoading(modifier = Modifier.weight(1f))

                status == null -> RunkkErrorState(
                    state.error ?: "Runimal을 불러오지 못했어요",
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val card = status.card
                    if (status.type != null && card != null) {
                        TypeCard(card, provisional = status.provisional)
                    } else {
                        RunkkEmptyState(
                            title = "아직 유형이 없어요",
                            message = "유효 러닝 3번째에 임시 유형, 10번째에 정식 유형이 나와요"
                        )
                    }
                    state.remainingLabel?.let { Remaining(it, status) }
                    AllTypes(status)
                }
            }
            if (state.canShare) {
                HorizontalDivider(color = RunkkColors.Border)
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RunkkPrimaryButton(
                        RunimalShareKind.STORY.label + "로 카드 공유",
                        onClick = { onShare(RunimalShareKind.STORY) },
                        loading = state.sharing,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunkkSecondaryButton(
                            RunimalShareKind.SAVE.label,
                            onClick = { onShare(RunimalShareKind.SAVE) },
                            enabled = !state.sharing,
                            modifier = Modifier.weight(1f)
                        )
                        RunkkSecondaryButton(
                            RunimalShareKind.SYSTEM.label,
                            onClick = { onShare(RunimalShareKind.SYSTEM) },
                            enabled = !state.sharing,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/** 유형 카드(FR-151): 유형(아이콘은 동물 이름 글자), 이름, 한 줄 설명, 축별 우세 비율. */
@Composable
private fun TypeCard(card: RunimalCard, provisional: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Lime, RunkkShapes.ExtraLarge)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "MY RUNIMAL",
                style = RunkkTextStyles.Overline,
                color = RunkkColors.Lime,
                modifier = Modifier.weight(1f)
            )
            RunkkBadge(
                if (provisional) "임시 유형" else "확정 유형",
                tone = if (provisional) RunkkBadgeTone.Muted else RunkkBadgeTone.Lime
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Text(
                card.title,
                style = RunkkTextStyles.text(28, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(card.description, style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
        }
        card.axisRatios.entries.forEachIndexed { index, (axis, ratio) ->
            AxisBar(axis, ratio, lime = index % 2 == 0)
        }
    }
}

/** 축 막대: 우세한 쪽과 비율(예: 솔로 78%). 비율은 0~1. */
@Composable
private fun AxisBar(axis: String, ratio: Double, lime: Boolean) {
    val percent = (ratio.coerceIn(0.0, 1.0) * PERCENT).roundToInt()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$axis $percent%" }
    ) {
        Text(axis, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary, modifier = Modifier.width(72.dp))
        RunkkProgressBar(
            ratio.toFloat(),
            height = 12.dp,
            color = if (lime) RunkkColors.Lime else RunkkColors.Lavender,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$percent%",
            style = RunkkTextStyles.display(20),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun Remaining(label: String, status: RunimalStatus) {
    val total = if (status.type == null) PROVISIONAL_RUNS else CONFIRM_RUNS
    val done = (total - (status.remainingRuns ?: total)).coerceIn(0, total)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Large)
            .background(RunkkColors.Surface)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary)
        RunkkProgressBar(done.toFloat() / total)
        Text("3번째에 임시, 10번째에 정식 유형이 나와요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
}

/** 16유형(3.6.1). 내 유형을 라임으로 표시한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllTypes(status: RunimalStatus) {
    if (status.allTypes.isEmpty()) return
    RunkkSectionTitle("16가지 Runimal")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        status.allTypes.forEach { type ->
            val mine = type == status.type || type == status.card?.title
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RunkkShapes.Medium)
                    .background(if (mine) RunkkColors.LimeContainer else RunkkColors.Surface)
                    .border(1.dp, if (mine) RunkkColors.Lime else RunkkColors.Border, RunkkShapes.Medium)
                    .semantics(mergeDescendants = true) {
                        selected = mine
                        if (mine) stateDescription = "내 유형"
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    type,
                    style = RunkkTextStyles.text(13, if (mine) FontWeight.Black else FontWeight.Normal),
                    color = if (mine) RunkkColors.Lime else RunkkColors.TextSecondary
                )
            }
        }
    }
}

private const val PERCENT = 100
private const val PROVISIONAL_RUNS = 3
private const val CONFIRM_RUNS = 10

@Preview(name = "MY Runimal", widthDp = 390, heightDp = 844)
@Composable
private fun RunimalPreview() {
    RunkkTheme(reduceMotion = true) {
        RunimalScreen(RunimalUiState(loading = false, status = RunimalStatus(null, true, 2, null, emptyList())), {
        }, {}, {}, {})
    }
}
