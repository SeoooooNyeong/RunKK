package com.a207.runkk.app.ui.record

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.ui.share.CardSide

/** 기록 카드 팝업(FR-068·FR-069): 앞면 기록·뒷면 코스, 기기 저장·다른 앱·스토리 공유. */
@Composable
fun RecordCardRoute(recordId: String, viewModel: RecordCardViewModel = hiltViewModel()) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RecordCardScreen(
        state = state,
        onClose = navigator::back,
        onSideSelected = viewModel::onSideSelected,
        onShare = viewModel::onShare,
        onMessageShown = viewModel::onMessageShown,
        onRetry = { viewModel.load(recordId) }
    )
}

@Composable
fun RecordCardScreen(
    state: RecordCardUiState,
    onClose: () -> Unit,
    onSideSelected: (CardSide) -> Unit,
    onShare: (CardShareKind) -> Unit,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
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
            RunkkTopBar(title = "기록 카드", onBack = onClose)
            when {
                state.loading -> RunkkLoading(modifier = Modifier.weight(1f))

                state.front == null -> RunkkErrorState(
                    state.error ?: "카드를 만들지 못했어요",
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RunkkSegmentedTabs(
                        listOf("앞면 · 기록", "뒷면 · 코스"),
                        selectedIndex = state.side.ordinal,
                        onSelect = { onSideSelected(CardSide.entries[it]) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val bitmap = if (state.side == CardSide.FRONT) state.front else state.back
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = if (state.side == CardSide.FRONT) "러닝 카드 앞면" else "러닝 카드 뒷면, 코스 지도",
                            modifier = Modifier.weight(
                                1f,
                                fill = false
                            ).aspectRatio(CARD_RATIO).clip(RunkkShapes.ExtraLarge)
                        )
                    }
                    if (state.side == CardSide.BACK && !state.canShareBack) {
                        Text(
                            "코스 지도 공유가 꺼져 있어 뒷면은 공유할 수 없어요",
                            style = RunkkTextStyles.Caption,
                            color = RunkkColors.Lavender
                        )
                    }
                }
            }
            if (state.front != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RunkkPrimaryButton(CardShareKind.STORY.label + "로 공유", onClick = {
                        onShare(CardShareKind.STORY)
                    }, loading = state.sharing, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunkkSecondaryButton(CardShareKind.SAVE.label, onClick = {
                            onShare(CardShareKind.SAVE)
                        }, enabled = !state.sharing, modifier = Modifier.weight(1f))
                        RunkkSecondaryButton(CardShareKind.SYSTEM.label, onClick = {
                            onShare(CardShareKind.SYSTEM)
                        }, enabled = !state.sharing, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

private const val CARD_RATIO = 1_080f / 1_350f

@Preview(name = "기록 카드(불러오는 중)", widthDp = 390, heightDp = 844)
@Composable
private fun RecordCardPreview() {
    RunkkTheme(reduceMotion = true) { RecordCardScreen(RecordCardUiState(), {}, {}, {}, {}, {}) }
}
