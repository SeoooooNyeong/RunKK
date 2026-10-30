package com.a207.runkk.app.ui.record

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.ui.share.CardSide

/**
 * 러닝 카드 팝업(G1·G2, FR-068)과 카드 공유(G3, FR-069). 팝업은 카드를 눌러 앞·뒷면을 뒤집고, "공유"를 누르면 같은 경로 안에서
 * 카드 공유 단계(미리보기, 코스 지도 표시, 기기에 저장·스토리·더 보기)로 바뀐다. 뒤로가기는 공유 단계에서 팝업으로 돌아온다.
 */
@Composable
fun RecordCardRoute(recordId: String, viewModel: RecordCardViewModel = hiltViewModel()) {
    LaunchedEffect(recordId) { viewModel.load(recordId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    BackHandler(enabled = state.isShareStep) { viewModel.onShareStep(false) }
    RecordCardScreen(
        state = state,
        onClose = navigator::back,
        onSideSelected = viewModel::onSideSelected,
        onShare = viewModel::onShare,
        onMessageShown = viewModel::onMessageShown,
        onRetry = { viewModel.load(recordId) },
        onShareStep = viewModel::onShareStep
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
    modifier: Modifier = Modifier,
    onShareStep: (Boolean) -> Unit = {}
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
        when {
            state.isLoading -> RunkkLoading(modifier = Modifier.fillMaxSize())

            state.front == null -> Column(Modifier.fillMaxSize()) {
                RunkkTopBar(title = stringResource(R.string.record_card_title), onBack = onClose)
                RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.record_card_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )
            }

            state.isShareStep -> ShareStep(state, onSideSelected, onShare, onBack = { onShareStep(false) })

            else -> CardPopup(
                state,
                onFlip = { onSideSelected(if (state.side == CardSide.FRONT) CardSide.BACK else CardSide.FRONT) },
                onShare = { onShareStep(true) },
                onClose = onClose
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/**
 * 팝업(B-7·G1·G2): 배경 장면(AvatarBackdrop) 위 카드 앞면(RecordCardFront)과 그 바로 아래 "공유"·"닫기"(R2-18, D90).
 * 카드 공유(CARD_SHARE)가 닫혀 있으면(1차 배포) 뒤집기·면 점·공유가 없고 "닫기"만 있다(D-252, TC-9699). 열려 있으면 카드를 눌러
 * 코스 면(그린 이미지)으로 뒤집는다.
 */
@Composable
private fun CardPopup(state: RecordCardUiState, onFlip: () -> Unit, onShare: () -> Unit, onClose: () -> Unit) {
    val resources = LocalContext.current.resources
    val isShareOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CARD_SHARE)
    AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize().testTag(RECORD_CARD_BACKDROP_TAG))
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.weight(1f, fill = false).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val card = state.card
            val flipLabel = stringResource(
                if (state.side ==
                    CardSide.FRONT
                ) {
                    R.string.record_card_flip_to_back
                } else {
                    R.string.record_card_flip_to_front
                }
            )
            val flip = if (isShareOpen) {
                Modifier.clickable(role = Role.Button, onClickLabel = flipLabel, onClick = onFlip)
            } else {
                Modifier
            }
            val back = state.back
            if (isShareOpen && state.side == CardSide.BACK && back != null) {
                Image(
                    bitmap = back.asImageBitmap(),
                    contentDescription = card?.let { cardDescription(resources, it, CardSide.BACK) }
                        ?: stringResource(sideDescriptionRes(CardSide.BACK)),
                    modifier = Modifier.aspectRatio(CARD_RATIO).clip(RunkkShapes.ExtraLarge).then(flip)
                )
            } else if (card != null) {
                val description = cardDescription(resources, card, CardSide.FRONT)
                RecordCardFront(
                    card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(flip)
                        .semantics { contentDescription = description }
                )
            }
        }
        if (isShareOpen) SideDots(state.side)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isShareOpen) {
                RunkkPrimaryButton(
                    stringResource(R.string.record_card_share),
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
            RunkkSecondaryButton(
                stringResource(R.string.record_card_close),
                onClick = onClose,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 지금 보이는 면을 알리는 점 두 개(앞면, 뒷면). */
@Composable
private fun SideDots(side: CardSide) {
    val description = stringResource(sideDescriptionRes(side))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { stateDescription = description }
    ) {
        CardSide.entries.forEach {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (it == side) RunkkColors.Lime else RunkkColors.BorderStrong)
            )
        }
    }
}

/**
 * 카드 공유(G3): 미리보기, "코스 지도 표시"(켜면 코스 면을 공유한다), 공유 대상(기기에 저장, 인스타그램 스토리, 더 보기).
 * 코스 지도 공유가 꺼진 기록은 스위치를 끈 채 잠근다(FR-011·FR-069).
 */
@Composable
private fun ShareStep(
    state: RecordCardUiState,
    onSideSelected: (CardSide) -> Unit,
    onShare: (CardShareKind) -> Unit,
    onBack: () -> Unit
) {
    val resources = LocalContext.current.resources
    Column(modifier = Modifier.fillMaxSize()) {
        RunkkTopBar(title = stringResource(R.string.record_card_share_title), onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val bitmap = if (state.side == CardSide.FRONT) state.front else state.back
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = state.card?.let { cardDescription(resources, it, state.side) }
                        ?: stringResource(sideDescriptionRes(state.side)),
                    modifier = Modifier.weight(1f, fill = false).aspectRatio(CARD_RATIO).clip(RunkkShapes.ExtraLarge)
                )
            }
            RunkkSwitchRow(
                title = stringResource(R.string.record_card_show_course),
                isChecked = state.side == CardSide.BACK,
                onCheckedChange = { onSideSelected(if (it) CardSide.BACK else CardSide.FRONT) },
                isEnabled = state.canShareBack
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ShareTargetButton(RunkkIcons.Plus, stringResource(CardShareKind.SAVE.labelRes), state.isSharing) {
                onShare(CardShareKind.SAVE)
            }
            ShareTargetButton(RunkkIcons.Camera, stringResource(CardShareKind.STORY.labelRes), state.isSharing) {
                onShare(CardShareKind.STORY)
            }
            ShareTargetButton(RunkkIcons.Share, stringResource(CardShareKind.SYSTEM.labelRes), state.isSharing) {
                onShare(CardShareKind.SYSTEM)
            }
        }
    }
}

/** 공유 대상 한 칸: 원 아이콘과 이름. 공유 중에는 누를 수 없다. */
@Composable
private fun ShareTargetButton(icon: ImageVector, label: String, isSharing: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RunkkShapes.Medium)
            .clickable(enabled = !isSharing, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(RunkkColors.Surface).clearAndSetSemantics {},
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RunkkColors.Lime, modifier = Modifier.size(24.dp))
        }
        Text(label, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary, textAlign = TextAlign.Center)
    }
}

private const val CARD_RATIO = 1_080f / 1_350f

/** 카드 팝업 배경 장면의 시험 표지. */
internal const val RECORD_CARD_BACKDROP_TAG = "record-card-backdrop"

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
