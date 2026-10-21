package com.a207.runkk.app.ui.record

import android.content.res.Resources
import androidx.annotation.StringRes
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.record.RunCard
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
    val messageText = state.message?.asString()
    LaunchedEffect(state.message) {
        messageText?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.record_card_title), onBack = onClose)
            when {
                state.loading -> RunkkLoading(modifier = Modifier.weight(1f))

                state.front == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.record_card_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> CardPreview(state, onSideSelected, modifier = Modifier.weight(1f))
            }
            if (state.front != null) CardShareButtons(state.sharing, onShare)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/** 카드 미리보기: 앞·뒷면 탭, 고른 면 이미지, 뒷면을 공유할 수 없을 때의 안내. */
@Composable
private fun CardPreview(state: RecordCardUiState, onSideSelected: (CardSide) -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RunkkSegmentedTabs(
            listOf(stringResource(R.string.record_card_tab_front), stringResource(R.string.record_card_tab_back)),
            selectedIndex = state.side.ordinal,
            onSelect = { onSideSelected(CardSide.entries[it]) },
            modifier = Modifier.fillMaxWidth()
        )
        val bitmap = if (state.side == CardSide.FRONT) state.front else state.back
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = state.card?.let { cardDescription(resources, it, state.side) }
                    ?: stringResource(sideDescriptionRes(state.side)),
                modifier = Modifier.weight(
                    1f,
                    fill = false
                ).aspectRatio(CARD_RATIO).clip(RunkkShapes.ExtraLarge)
            )
        }
        if (state.side == CardSide.BACK && !state.canShareBack) {
            Text(
                stringResource(R.string.record_card_back_not_shareable),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender
            )
        }
    }
}

/** 공유 버튼: 스토리(큰 버튼), 기기 저장·다른 앱. */
@Composable
private fun CardShareButtons(sharing: Boolean, onShare: (CardShareKind) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkPrimaryButton(stringResource(R.string.record_card_share_story_button), onClick = {
            onShare(CardShareKind.STORY)
        }, loading = sharing, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkSecondaryButton(stringResource(CardShareKind.SAVE.labelRes), onClick = {
                onShare(CardShareKind.SAVE)
            }, enabled = !sharing, modifier = Modifier.weight(1f))
            RunkkSecondaryButton(stringResource(CardShareKind.SYSTEM.labelRes), onClick = {
                onShare(CardShareKind.SYSTEM)
            }, enabled = !sharing, modifier = Modifier.weight(1f))
        }
    }
}

private const val CARD_RATIO = 1_080f / 1_350f

@StringRes
private fun sideDescriptionRes(side: CardSide): Int =
    if (side == CardSide.FRONT) R.string.record_card_front_desc else R.string.record_card_back_desc

/** 카드 이미지 대체 텍스트: 면, 종류, 거리, 시간, 평균 페이스(이미지 안 수치를 보조 기술에도 준다). */
internal fun cardDescription(res: Resources, card: RunCard, side: CardSide): String = listOfNotNull(
    res.getString(sideDescriptionRes(side)),
    res.getString(kindLabelRes(card.cardKind)),
    res.getString(R.string.record_distance_spoken, RunkkFormat.km(card.distance)),
    RunkkFormat.durationSpoken(res, card.elapsed),
    card.averagePace?.let { RunkkFormat.paceSpoken(res, it) }
).joinToString(", ")

/** 기록 화면 전용 오류 문구. 남의 기록 카드(403 PERMISSION_DENIED)는 본인 기록만 된다고 알리고, 나머지는 공용 문구다. */
internal object RecordErrorText {
    fun of(error: AppError): UiText = if (error is AppError.Rejected && error.code == PERMISSION_DENIED) {
        UiText.Res(R.string.record_error_own_record_only)
    } else {
        RunkkErrorText.of(error)
    }

    private const val PERMISSION_DENIED = "PERMISSION_DENIED"
}

@Preview(name = "기록 카드(불러오는 중)", widthDp = 390, heightDp = 844)
@Composable
private fun RecordCardPreview() {
    RunkkTheme(reduceMotion = true) { RecordCardScreen(RecordCardUiState(), {}, {}, {}, {}, {}) }
}
