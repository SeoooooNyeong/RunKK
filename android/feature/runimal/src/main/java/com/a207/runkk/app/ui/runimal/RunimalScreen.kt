package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunimalAxisName
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkOverline
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSectionTitle
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import kotlin.math.roundToInt

/** MY 러니멀: 유형 카드·남은 횟수·16유형·카드 공유. */
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
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.asString(context))
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.runimal_title), onBack = onBack)
            val status = state.status
            when {
                status == null && state.isLoading -> RunkkLoading(modifier = Modifier.weight(1f))

                status == null -> RunkkErrorState(
                    state.error?.asString() ?: stringResource(R.string.runimal_load_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )

                else -> RunimalBody(state, status, Modifier.weight(1f))
            }
            if (state.canShare) ShareBar(state.isSharing, onShare)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/** 본문: 유형 카드(없으면 안내)·남은 횟수·16유형. */
@Composable
private fun RunimalBody(state: RunimalUiState, status: RunimalStatus, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val card = status.card
        if (status.type != null && card != null) {
            TypeCard(card, isProvisional = status.isProvisional)
        } else {
            RunkkEmptyState(
                title = stringResource(R.string.runimal_empty_title),
                message = stringResource(R.string.runimal_empty_message)
            )
        }
        state.remainingLabel?.let { Remaining(it.asString(), status) }
        AllTypes(status)
    }
}

/** 카드 공유 버튼들(스토리·기기 저장·다른 앱). */
@Composable
private fun ShareBar(isSharing: Boolean, onShare: (RunimalShareKind) -> Unit) {
    HorizontalDivider(color = RunkkColors.Border)
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkPrimaryButton(
            stringResource(R.string.runimal_share_with, stringResource(RunimalShareKind.STORY.labelRes)),
            onClick = { onShare(RunimalShareKind.STORY) },
            isLoading = isSharing,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkSecondaryButton(
                stringResource(RunimalShareKind.SAVE.labelRes),
                onClick = { onShare(RunimalShareKind.SAVE) },
                isEnabled = !isSharing,
                modifier = Modifier.weight(1f)
            )
            RunkkSecondaryButton(
                stringResource(RunimalShareKind.SYSTEM.labelRes),
                onClick = { onShare(RunimalShareKind.SYSTEM) },
                isEnabled = !isSharing,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 유형 카드(FR-151): 유형 동물 그림, 이름, 한 줄 설명, 축별 우세 비율. */
@Composable
private fun TypeCard(card: RunimalCard, isProvisional: Boolean) {
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
            RunkkOverline(
                stringResource(R.string.runimal_overline),
                color = RunkkColors.Lime,
                modifier = Modifier.weight(1f)
            )
            RunkkBadge(
                stringResource(
                    if (isProvisional) R.string.runimal_type_provisional else R.string.runimal_type_confirmed
                ),
                tone = if (isProvisional) RunkkBadgeTone.Muted else RunkkBadgeTone.Lime
            )
        }
        val art = RunimalArt.of(card.type)
        Image(
            painter = painterResource(art.drawableRes),
            contentDescription = if (art == RunimalArt.UNKNOWN) card.title else stringResource(art.nameRes),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(TYPE_ART_SIZE)
                .clip(CircleShape)
                .background(RunkkColors.Background)
                .padding(12.dp)
        )
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
            AxisBar(axis, ratio, isLime = index % 2 == 0)
        }
    }
}

/** 축 막대: 우세한 쪽과 비율(예: 솔로 78%). 비율은 0~1. */
@Composable
private fun AxisBar(axis: String, ratio: Double, isLime: Boolean) {
    val percent = (ratio.coerceIn(0.0, 1.0) * PERCENT).roundToInt()
    // 서버가 보낸 축 키(COMPANION 등)는 한국어 이름으로 바꾼다. 모르는 축은 받은 값 그대로 둔다.
    val label = RunimalAxisName.of(axis)?.let { stringResource(it.nameRes) } ?: axis
    val description = stringResource(R.string.runimal_axis_description, label, percent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        Text(label, style = RunkkTextStyles.Label, color = RunkkColors.TextPrimary, modifier = Modifier.width(72.dp))
        RunkkProgressBar(
            ratio.toFloat(),
            height = 12.dp,
            color = if (isLime) RunkkColors.Lime else RunkkColors.Lavender,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.runimal_percent, percent),
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
        Text(
            stringResource(R.string.runimal_remaining_hint),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/** 16유형(3.6.1): 동물 그림과 한국어 이름. 내 유형을 라임으로 표시한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllTypes(status: RunimalStatus) {
    if (status.allTypes.isEmpty()) return
    RunkkSectionTitle(stringResource(R.string.runimal_all_types_title))
    val mineLabel = stringResource(R.string.runimal_my_type)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        status.allTypes.forEach { type ->
            val mine = type == status.type || type == status.card?.title
            val art = RunimalArt.of(type)
            val name = if (art == RunimalArt.UNKNOWN) type else stringResource(art.nameRes)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RunkkShapes.Medium)
                    .background(if (mine) RunkkColors.LimeContainer else RunkkColors.Surface)
                    .border(1.dp, if (mine) RunkkColors.Lime else RunkkColors.Border, RunkkShapes.Medium)
                    .clearAndSetSemantics {
                        contentDescription = name
                        selected = mine
                        if (mine) stateDescription = mineLabel
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(art.drawableRes),
                    contentDescription = name,
                    modifier = Modifier.size(LIST_ART_SIZE)
                )
                Text(
                    name,
                    style = RunkkTextStyles.text(13, if (mine) FontWeight.Black else FontWeight.Normal),
                    color = if (mine) RunkkColors.Lime else RunkkColors.TextSecondary
                )
            }
        }
    }
}

private val TYPE_ART_SIZE = 144.dp
private val LIST_ART_SIZE = 40.dp
private const val PERCENT = 100
private const val PROVISIONAL_RUNS = 3
private const val CONFIRM_RUNS = 10

@Preview(name = "MY 러니멀", widthDp = 390, heightDp = 844)
@Composable
private fun RunimalPreview() {
    RunkkTheme(reduceMotion = true) {
        RunimalScreen(RunimalUiState(isLoading = false, status = RunimalStatus(null, true, 2, null, emptyList())), {
        }, {}, {}, {})
    }
}
