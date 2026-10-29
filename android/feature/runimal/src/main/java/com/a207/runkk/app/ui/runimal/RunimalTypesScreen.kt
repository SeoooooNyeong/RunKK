package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalTypeBoard

/** Runimal 16유형(G-5, D-285): 축 범례와 4×4 격자. 확정받은 적 없는 유형은 흐리지만 눌러 상세로 갈 수 있다. */
@Composable
fun RunimalTypesRoute(viewModel: RunimalTypesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RunimalTypesScreen(
        state = state,
        onBack = navigator::back,
        onSelect = { navigator.navigate(Routes.RunimalType(it)) },
        onRetry = viewModel::load
    )
}

@Composable
fun RunimalTypesScreen(
    state: RunimalTypesUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.runimal_types_title), onBack = onBack)
        val board = state.board
        when {
            board == null && state.isLoading -> RunkkLoading(modifier = Modifier.weight(1f))

            board == null -> RunkkErrorState(
                state.error?.asString() ?: stringResource(R.string.runimal_load_failed),
                onRetry = onRetry,
                modifier = Modifier.weight(1f)
            )

            else -> Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AxisLegend(state.currentAxes)
                TypeGrid(state, board, onSelect)
            }
        }
    }
}

/** 머리 축 범례 4칸(솔로/소셜, 단거리/장거리, 단골/탐험, 루틴/즉흥). 지금 유형이 기운 쪽을 굵게 한다. */
@Composable
private fun AxisLegend(currentAxes: Map<String, AxisLean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunimalAxisLabel.entries.forEach { axis ->
            val lean = currentAxes[axis.name]
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RunkkShapes.Large)
                    .background(RunkkColors.Surface)
                    .padding(vertical = 12.dp)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LegendSide(stringResource(axis.shortName(AxisLean.A)), isLeaning = lean == AxisLean.A)
                LegendSide(stringResource(axis.shortName(AxisLean.B)), isLeaning = lean == AxisLean.B)
            }
        }
    }
}

@Composable
private fun LegendSide(name: String, isLeaning: Boolean) {
    Text(
        name,
        style = RunkkTextStyles.text(13, if (isLeaning) FontWeight.Black else FontWeight.Normal),
        color = if (isLeaning) RunkkColors.TextPrimary else RunkkColors.TextSecondary
    )
}

/** 16유형 4×4 격자: 그림과 이름. 지금 유형은 라임 테두리, 확정받은 적 없는 유형은 흐리게. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeGrid(state: RunimalTypesUiState, board: RunimalTypeBoard, onSelect: (String) -> Unit) {
    val mineLabel = stringResource(R.string.runimal_my_type)
    val neverLabel = stringResource(R.string.runimal_types_never_confirmed)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = GRID_COLUMNS
    ) {
        board.types.forEach { entry ->
            val isMine = entry.type == board.current
            val isDimmed = state.isDimmed(entry)
            Column(
                modifier = Modifier
                    .weight(1f)
                    // 배경·테두리까지 흐리게 하려면 alpha가 그리기 수정자보다 앞에 와야 한다.
                    .alpha(if (isDimmed) DIMMED_ALPHA else 1f)
                    .clip(RunkkShapes.Large)
                    .background(if (isMine) RunkkColors.LimeContainer else RunkkColors.Surface)
                    .border(
                        if (isMine) 2.dp else 1.dp,
                        if (isMine) RunkkColors.Lime else RunkkColors.Border,
                        RunkkShapes.Large
                    )
                    .clickable(role = Role.Button) { onSelect(entry.type) }
                    .semantics {
                        if (isMine) {
                            stateDescription = mineLabel
                        } else if (isDimmed) {
                            stateDescription = neverLabel
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(RunimalArt.of(entry.type).drawableRes),
                    contentDescription = null,
                    modifier = Modifier.size(ART_SIZE)
                )
                Text(
                    entry.displayName,
                    style = RunkkTextStyles.text(11, if (isMine) FontWeight.Bold else FontWeight.Normal),
                    color = RunkkColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    minLines = 2,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        repeat((GRID_COLUMNS - board.types.size % GRID_COLUMNS) % GRID_COLUMNS) { Spacer(Modifier.weight(1f)) }
    }
}

private val ART_SIZE = 48.dp
private const val GRID_COLUMNS = 4
private const val DIMMED_ALPHA = 0.4f

@Preview(name = "Runimal 16유형", widthDp = 390, heightDp = 844)
@Composable
private fun RunimalTypesPreview() {
    RunkkTheme(reduceMotion = true) {
        RunimalTypesScreen(RunimalTypesUiState(isLoading = true), {}, {}, {})
    }
}
