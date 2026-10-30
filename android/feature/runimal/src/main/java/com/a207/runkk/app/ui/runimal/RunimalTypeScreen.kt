package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkInfoChip
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.runimal.RunimalTypeDetail

/** Runimal 유형 상세(G-6, D-285): 그림·이름·4축 칩·한 줄 설명, 특징 3줄, 러너 중 비율. */
@Composable
fun RunimalTypeRoute(type: String, viewModel: RunimalTypeViewModel = hiltViewModel()) {
    LaunchedEffect(type) { viewModel.load(type) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RunimalTypeScreen(
        state = state,
        onBack = navigator::back,
        onRetry = { viewModel.load(type, isForced = true) }
    )
}

@Composable
fun RunimalTypeScreen(
    state: RunimalTypeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.runimal_type_title), onBack = onBack, isTitleCentered = true)
        val detail = state.detail
        val error = state.error
        when {
            detail == null && error != null -> RunkkErrorState(
                error.asString(),
                onRetry = onRetry,
                modifier = Modifier.weight(1f)
            )

            detail == null -> RunkkLoading(modifier = Modifier.weight(1f))

            else -> Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TypeCard(detail)
                Traits(detail.traits)
                state.shareLabel?.let {
                    Text(it.asString(), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                }
            }
        }
    }
}

/** 유형 카드: 연한 둥근 네모 위 그림(G-6), 이름, 기운 쪽 4축 칩(솔로·장거리 등), 한 줄 설명. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeCard(detail: RunimalTypeDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.ExtraLarge)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunimalArtTile(RunimalArt.of(detail.type), size = ART_SIZE)
        Text(
            detail.displayName,
            style = RunkkTextStyles.text(22, FontWeight.Black),
            color = RunkkColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunimalAxisLabel.entries.forEach { axis ->
                detail.axes[axis.name]?.let { lean -> RunkkInfoChip(stringResource(axis.shortName(lean))) }
            }
        }
        Text(
            detail.description,
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** "이런 러너예요"와 특징 줄. */
@Composable
private fun Traits(traits: List<String>) {
    if (traits.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.runimal_type_traits_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        // 앞의 "·"는 보이기만 하고 보조 기술은 특성 문구만 읽는다(AN8-17).
        traits.forEach {
            Text(
                stringResource(R.string.runimal_type_trait, it),
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.semantics { contentDescription = it }
            )
        }
    }
}

private val ART_SIZE = 120.dp

@Preview(name = "유형 상세", widthDp = 390, heightDp = 844)
@Composable
private fun RunimalTypePreview() {
    RunkkTheme(reduceMotion = true) {
        RunimalTypeScreen(RunimalTypeUiState(isLoading = true), {}, {})
    }
}
