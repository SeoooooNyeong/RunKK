package com.a207.runkk.app.ui.runimal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
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
import com.a207.runkk.app.core.designsystem.LocalReleaseScope
import com.a207.runkk.app.core.designsystem.RunimalArt
import com.a207.runkk.app.core.designsystem.RunimalAxisName
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
import com.a207.runkk.app.core.designsystem.UnreleasedFeature
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.runimal.AxisLean
import com.a207.runkk.app.domain.runimal.RunimalCard
import com.a207.runkk.app.domain.runimal.RunimalStatus
import kotlin.math.roundToInt

/** MY 러니멀(G-4): 유형 카드·남은 횟수·성향 막대·16유형·카드 공유(CARD_SHARE가 열려 있을 때만). */
@Composable
fun RunimalRoute(viewModel: RunimalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    RunimalScreen(
        state = state,
        onBack = navigator::back,
        onShare = viewModel::onShare,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown,
        onShowTypes = { navigator.navigate(Routes.RUNIMAL_TYPES) }
    )
}

@Composable
fun RunimalScreen(
    state: RunimalUiState,
    onBack: () -> Unit,
    onShare: (RunimalShareKind) -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    onShowTypes: () -> Unit,
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
    // 카드 공유는 1차 배포에서 닫는다(D-252, D-285).
    val isShareOpen = LocalReleaseScope.current.isOpen(UnreleasedFeature.CARD_SHARE)
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

                else -> RunimalBody(state, status, onShowTypes, Modifier.weight(1f))
            }
            if (state.canShare && isShareOpen) ShareBar(state.isSharing, onShare)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 160.dp))
    }
}

/** 본문: 유형 카드(없으면 안내)·남은 횟수·성향·16유형 버튼·16유형 격자. */
@Composable
private fun RunimalBody(
    state: RunimalUiState,
    status: RunimalStatus,
    onShowTypes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val card = status.card
        if (status.type != null && card != null) {
            TypeCard(card, isProvisional = status.isProvisional)
        } else {
            RunkkEmptyState(title = stringResource(R.string.runimal_empty_title))
        }
        state.remainingLabel?.let {
            Text(it.asString(), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
        if (status.type != null && card != null) Tendency(card)
        RunkkPrimaryButton(
            stringResource(R.string.runimal_show_types),
            onClick = onShowTypes,
            modifier = Modifier.fillMaxWidth()
        )
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

/** 유형 카드(FR-151): 임시·확정 표시, 유형 동물 그림, 이름, 한 줄 설명. */
@Composable
private fun TypeCard(card: RunimalCard, isProvisional: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Lime, RunkkShapes.ExtraLarge)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RunkkBadge(
            stringResource(if (isProvisional) R.string.runimal_type_provisional else R.string.runimal_type_confirmed),
            tone = if (isProvisional) RunkkBadgeTone.Muted else RunkkBadgeTone.Lime
        )
        val art = RunimalArt.of(card.type)
        Image(
            painter = painterResource(art.drawableRes),
            contentDescription = if (art == RunimalArt.UNKNOWN) card.title else stringResource(art.nameRes),
            modifier = Modifier
                .size(TYPE_ART_SIZE)
                .clip(CircleShape)
                .background(RunkkColors.Background)
                .padding(12.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {}
        ) {
            Text(
                card.title,
                style = RunkkTextStyles.text(24, FontWeight.Black),
                color = RunkkColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                card.description,
                style = RunkkTextStyles.Body,
                color = RunkkColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 성향: 서버 축 막대(axes)가 있으면 우세 쪽 막대, 옛 서버면 축 비율 막대. */
@Composable
private fun Tendency(card: RunimalCard) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RunkkSectionTitle(stringResource(R.string.runimal_tendency_title))
        if (card.axes.isNotEmpty()) {
            card.axes.forEach { LeanBar(it) }
        } else {
            card.axisRatios.entries.forEachIndexed { index, (axis, ratio) ->
                AxisBar(axis, ratio, isLime = index % 2 == 0)
            }
        }
    }
}

/**
 * 축 막대(D-285): 왼쪽 A 이름(솔로런 등), 오른쪽 B 이름. 우세 쪽 이름은 굵게 하고 옆에 비율을 붙이며, 막대는 우세 쪽에서부터
 * 그 비율만큼 찬다(B 우세면 오른쪽에서 찬다).
 */
@Composable
private fun LeanBar(bar: RunimalCard.AxisBar) {
    val label = RunimalAxisLabel.of(bar.axis)
    val nameA = label?.let { stringResource(it.longName(AxisLean.A)) } ?: bar.axis
    val nameB = label?.let { stringResource(it.longName(AxisLean.B)) }.orEmpty()
    val percent = bar.percent.coerceIn(0, PERCENT)
    val isA = bar.lean == AxisLean.A
    val description = stringResource(R.string.runimal_axis_description, if (isA) nameA else nameB, percent)
    val percentText = stringResource(R.string.runimal_percent, percent)
    val shape = RoundedCornerShape(LEAN_BAR_HEIGHT / 2)
    Column(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SideName(nameA, isLeaning = isA)
            if (isA) PercentText(percentText)
            Spacer(Modifier.weight(1f))
            if (!isA) PercentText(percentText)
            SideName(nameB, isLeaning = !isA)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(LEAN_BAR_HEIGHT)
                .clip(shape)
                .background(RunkkColors.Border)
                .testTag("runimal_axis_track_${bar.axis}")
        ) {
            if (percent > 0) {
                Box(
                    Modifier
                        .align(if (isA) Alignment.CenterStart else Alignment.CenterEnd)
                        .fillMaxWidth(percent / PERCENT.toFloat())
                        .fillMaxHeight()
                        .clip(shape)
                        .background(RunkkColors.Lime)
                        .testTag("runimal_axis_fill_${bar.axis}")
                )
            }
        }
    }
}

@Composable
private fun SideName(name: String, isLeaning: Boolean) {
    Text(
        name,
        style = RunkkTextStyles.text(14, if (isLeaning) FontWeight.Black else FontWeight.Normal),
        color = if (isLeaning) RunkkColors.TextPrimary else RunkkColors.TextSecondary
    )
}

@Composable
private fun PercentText(text: String) {
    Text(text, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
}

/** 옛 서버용 축 막대: 우세한 쪽과 비율(예: 동행 78%). 비율은 0~1. */
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

/**
 * 16유형(3.6.1, J1): 동물 그림 작은 격자(한 줄 8칸). 내 유형을 라임 테두리로 표시하고 이름은 보조 기술로 읽는다.
 * FlowRow는 그림의 고유 너비로 줄을 나눠 칸이 커지므로 줄을 직접 나눈다.
 */
@Composable
private fun AllTypes(status: RunimalStatus) {
    if (status.allTypes.isEmpty()) return
    val mineLabel = stringResource(R.string.runimal_my_type)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        status.allTypes.chunked(GRID_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { type ->
                    val mine = type == status.type || type == status.card?.title
                    val art = RunimalArt.of(type)
                    val name = if (art == RunimalArt.UNKNOWN) type else stringResource(art.nameRes)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RunkkShapes.Medium)
                            .background(if (mine) RunkkColors.LimeContainer else RunkkColors.Surface)
                            .border(
                                if (mine) 2.dp else 1.dp,
                                if (mine) RunkkColors.Lime else RunkkColors.Border,
                                RunkkShapes.Medium
                            )
                            .clearAndSetSemantics {
                                contentDescription = name
                                selected = mine
                                if (mine) stateDescription = mineLabel
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(art.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val TYPE_ART_SIZE = 120.dp
private val LEAN_BAR_HEIGHT = 10.dp
private const val GRID_COLUMNS = 8
private const val PERCENT = 100

@Preview(name = "MY 러니멀", widthDp = 390, heightDp = 844)
@Composable
private fun RunimalPreview() {
    RunkkTheme(reduceMotion = true) {
        RunimalScreen(
            RunimalUiState(isLoading = false, status = RunimalStatus(null, true, 2, null, emptyList())),
            {},
            {},
            {},
            {},
            {}
        )
    }
}
