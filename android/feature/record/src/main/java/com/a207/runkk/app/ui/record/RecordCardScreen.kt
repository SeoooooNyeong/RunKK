package com.a207.runkk.app.ui.record

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.AvatarBackdrop
import com.a207.runkk.app.core.designsystem.LocalRunkkColors
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkSwitchRow
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.record.RunCard
import com.a207.runkk.app.ui.share.CapturedCard
import com.a207.runkk.app.ui.share.CardSide
import com.a207.runkk.app.ui.share.ShareTargetBar
import com.a207.runkk.app.ui.share.capturedBy
import com.a207.runkk.app.ui.share.rememberCardCapture
import kotlinx.coroutines.launch

/**
 * 러닝 카드 팝업(K-1, FR-068)과 카드 공유(K-2, FR-069). 팝업은 카드를 눌러 앞·뒷면을 뒤집고, "공유"를 누르면 같은 경로 안에서
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
        onShare = { viewModel.onShare(it) },
        onMessageShown = viewModel::onMessageShown,
        onRetry = { viewModel.load(recordId) },
        onShareStep = viewModel::onShareStep,
        onShareCaptured = viewModel::onShare,
        onCourseLocked = viewModel::onCourseLocked
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
    onShareStep: (Boolean) -> Unit = {},
    onShareCaptured: (CardShareKind, CapturedCard?) -> Unit = { kind, _ -> onShare(kind) },
    onCourseLocked: () -> Unit = {}
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
        val card = state.card
        when {
            state.isLoading -> RunkkLoading(modifier = Modifier.fillMaxSize())

            state.front == null || card == null -> Column(Modifier.fillMaxSize()) {
                RunkkTopBar(title = stringResource(R.string.record_card_title), onBack = onClose)
                RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.record_card_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )
            }

            state.isShareStep -> ShareStep(
                state,
                card,
                onSideSelected = onSideSelected,
                onShare = onShareCaptured,
                onCourseLocked = onCourseLocked,
                onBack = { onShareStep(false) }
            )

            else -> CardPopup(
                state,
                card,
                onFlip = { onSideSelected(if (state.side == CardSide.FRONT) CardSide.BACK else CardSide.FRONT) },
                onShare = { onShareStep(true) },
                onClose = onClose
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/**
 * 팝업(K-1): 배경 장면(AvatarBackdrop) 위 카드와 그 아래 면 점, 반씩 나란한 "공유"·"닫기"(R2-18, D93). 카드를 누르면 앞면(기록)과
 * 뒷면(코스)이 뒤집힌다. 두 면은 같은 크기(334×383 비율)다.
 */
@Composable
private fun CardPopup(
    state: RecordCardUiState,
    card: RunCard,
    onFlip: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit
) {
    val resources = LocalContext.current.resources
    val side = state.side
    AvatarBackdrop(backgroundKey = null, modifier = Modifier.fillMaxSize().testTag(RECORD_CARD_BACKDROP_TAG))
    Column(
        modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        val flipLabel = stringResource(
            if (side == CardSide.FRONT) R.string.record_card_flip_to_back else R.string.record_card_flip_to_front
        )
        val description = cardDescription(resources, card, side)
        RecordCardFace(
            card,
            side,
            Modifier
                .weight(1f, fill = false)
                .clickable(role = Role.Button, onClickLabel = flipLabel, onClick = onFlip)
                .semantics { contentDescription = description }
        )
        SideDots(side)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RunkkPrimaryButton(
                stringResource(R.string.record_card_share),
                onClick = onShare,
                modifier = Modifier.weight(1f)
            )
            RunkkSecondaryButton(
                stringResource(R.string.record_card_close),
                onClick = onClose,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 지금 면의 카드(앞면 기록 또는 뒷면 코스). */
@Composable
private fun RecordCardFace(card: RunCard, side: CardSide, modifier: Modifier) {
    when (side) {
        CardSide.FRONT -> RecordCardFront(card, modifier)
        CardSide.BACK -> RecordCardBack(card, modifier)
    }
}

/**
 * 지금 보이는 면을 알리는 점 두 개(K-1): 지금 면은 길쭉한 진한 점, 다른 면은 작은 표면색 점. 다크는 표면색이 어두워 밤 배경 장면에
 * 묻히므로 다른 면 점에 밝은 테두리를 두른다(디자인 시스템 RunkkPageDots에는 점 테두리가 없어 여기 둔다).
 */
@Composable
private fun SideDots(side: CardSide) {
    val description = stringResource(sideDescriptionRes(side))
    val isDark = LocalRunkkColors.current.isDark
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag(RECORD_CARD_DOTS_TAG).semantics { stateDescription = description }
    ) {
        CardSide.entries.forEach {
            val isCurrent = it == side
            Box(
                Modifier
                    .size(width = if (isCurrent) 18.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) RunkkColors.TextPrimary else RunkkColors.Surface)
                    .then(
                        if (isDark && !isCurrent) {
                            Modifier.border(1.dp, RunkkColors.TextPrimary.copy(alpha = DOT_RING_ALPHA), CircleShape)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

/**
 * 카드 공유(K-2): 미리보기 카드, "코스 지도 표시"(켜면 미리보기가 코스 면으로 바뀌고 그 면을 공유한다), 공유 대상 막대.
 * 코스 지도 공유가 꺼진 기록은 스위치를 끈 채 흐리게 잠그고, 누르면 기존 안내를 스낵바로 보인다(FR-011·FR-069).
 * 공유할 때 미리보기 카드를 그대로 찍어 넘기고, 워드마크는 ViewModel이 공유 이미지에만 붙인다(사용자 결정 2026-11-09).
 */
@Composable
private fun ShareStep(
    state: RecordCardUiState,
    card: RunCard,
    onSideSelected: (CardSide) -> Unit,
    onShare: (CardShareKind, CapturedCard?) -> Unit,
    onCourseLocked: () -> Unit,
    onBack: () -> Unit
) {
    val resources = LocalContext.current.resources
    val side = state.side
    val capture = rememberCardCapture()
    val isDark = LocalRunkkColors.current.isDark
    val scope = rememberCoroutineScope()
    val share: (CardShareKind) -> Unit = { kind -> scope.launch { onShare(kind, capture.capture(isDark)) } }
    Column(modifier = Modifier.fillMaxSize()) {
        RunkkTopBar(
            title = stringResource(R.string.record_card_share_title),
            onBack = onBack,
            isTitleCentered = true
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            val description = cardDescription(resources, card, side)
            RecordCardFace(
                card,
                side,
                Modifier
                    .weight(1f, fill = false)
                    .capturedBy(capture)
                    .semantics { contentDescription = description }
            )
            CourseSwitch(
                isChecked = side == CardSide.BACK,
                isEnabled = state.canShareBack,
                onCheckedChange = { onSideSelected(if (it) CardSide.BACK else CardSide.FRONT) },
                onLocked = onCourseLocked
            )
        }
        ShareTargetBar(
            saveLabel = stringResource(CardShareKind.SAVE.labelRes),
            storyLabel = stringResource(CardShareKind.STORY.labelRes),
            moreLabel = stringResource(CardShareKind.SYSTEM.labelRes),
            isSharing = state.isSharing,
            onSave = { share(CardShareKind.SAVE) },
            onStory = { share(CardShareKind.STORY) },
            onMore = { share(CardShareKind.SYSTEM) }
        )
    }
}

/** "코스 지도 표시" 줄(K-2): 표면색 둥근 칸. 잠기면 흐리게 하고 누르면 [onLocked]로 이유를 알린다. */
@Composable
private fun CourseSwitch(
    isChecked: Boolean,
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLocked: () -> Unit
) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RunkkShapes.Medium,
        contentPadding = PaddingValues(0.dp)
    ) {
        // 바탕은 그대로 두고 줄만 흐리게 한다.
        Box(Modifier.graphicsLayer { alpha = if (isEnabled) 1f else LOCKED_ALPHA }) {
            RunkkSwitchRow(
                title = stringResource(R.string.record_card_show_course),
                isChecked = isChecked,
                onCheckedChange = onCheckedChange,
                isEnabled = isEnabled
            )
            // 잠긴 스위치는 누름을 받지 않으므로 위에 덮개를 얹어 누름을 안내로 돌린다.
            if (!isEnabled) {
                Box(Modifier.matchParentSize().clickable(onClick = onLocked).clearAndSetSemantics {})
            }
        }
    }
}

private const val LOCKED_ALPHA = 0.45f
private const val DOT_RING_ALPHA = 0.8f

/** 카드 팝업 배경 장면의 시험 표지. */
internal const val RECORD_CARD_BACKDROP_TAG = "record-card-backdrop"

/** 카드 면 점의 시험 표지. */
internal const val RECORD_CARD_DOTS_TAG = "record-card-dots"

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
