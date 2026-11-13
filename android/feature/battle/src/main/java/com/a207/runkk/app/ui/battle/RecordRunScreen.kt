package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.CelebrityPortrait
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkAlpha
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import java.time.Duration
import java.time.LocalDate

/**
 * 기록런 탭(D-1·E-1, D-250): 제목 "기록런"과 셀프런 | 셀럽런 분절(D89). 고른 분절은 rememberSaveable로 지킨다. 분절 안은
 * SelfBattleViewModel·CelebBattleViewModel이 그대로 맡는다. [initialSegment]는 처음 고른 분절이다(셀럽런 경로 Routes.RECORD_RUN_CELEB).
 */
@Composable
fun RecordRunRoute(initialSegment: Int = SELF_SEGMENT) {
    var selected by rememberSaveable { mutableIntStateOf(initialSegment) }
    Column(modifier = Modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(stringResource(R.string.battle_record_run_title), isTitleCentered = false)
        RunkkSegmentedTabs(
            options = listOf(
                stringResource(R.string.battle_record_run_self),
                stringResource(R.string.battle_record_run_celeb)
            ),
            selectedIndex = selected,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (selected == SELF_SEGMENT) SelfBattleRoute() else CelebBattleRoute()
        }
    }
}

/** 나와의 대결(D-1, FR-096). */
@Composable
fun SelfBattleRoute(viewModel: SelfBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecordBattleEffects(viewModel)
    SelfBattleScreen(
        state = state,
        onSelect = viewModel::onSelect,
        onStart = viewModel::onStart,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

/** 셀럽런(E-1, FR-097·FR-100). */
@Composable
fun CelebBattleRoute(viewModel: CelebBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecordBattleEffects(viewModel)
    CelebBattleScreen(
        state = state,
        onFilter = viewModel::onFilter,
        onSelect = viewModel::onSelect,
        onStart = viewModel::onStart,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

/** 화면에 돌아오면 시작 중 표시를 풀고, 시작되면 대결 진행으로 간다. */
@Composable
private fun RecordBattleEffects(viewModel: RecordBattleViewModel) {
    val navigator = LocalRunkkNavigator.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordBattleEvent.Start -> navigator.navigate(event.key)
            }
        }
    }
}

/**
 * 나와의 대결(D-1, D-288): "어제의 나를 이겨 볼까요?", 나 VS 과거의 나, 거리 카드(1km·3km·5km·10km·하프, 기록 없는 거리는 누를 수 없다).
 * 처음엔 고른 거리가 없고 카드를 눌러야 "n km 기록 깨러 가기"가 보인다.
 */
@Composable
fun SelfBattleScreen(
    state: RecordBattleUiState,
    onSelect: (CandidateUi) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    RecordBattleFrame(state, onMessageShown, modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.battle_self_headline),
                    style = RunkkTextStyles.Headline,
                    color = RunkkColors.TextPrimary
                )
                ModeVersus(stringResource(R.string.battle_running_opponent_self), opponentFigure = Figure.PAST_SELF)
                CandidateBody(state, onRetry, emptyTitle = R.string.battle_self_empty_title) {
                    state.candidates.forEach { candidate ->
                        DistanceCardRow(
                            DistanceCard(
                                candidate.distance,
                                candidate.finishTime,
                                candidate.recordedOn
                            ),
                            isSelected = candidate == state.selected,
                            onClick = { onSelect(candidate) },
                            isEnabled = candidate.isSelectable,
                            label = distanceLabel(candidate.distance).asString()
                        )
                    }
                }
            }
            state.selected?.let { selected ->
                RunkkPrimaryButton(
                    text = stringResource(R.string.battle_self_break, distanceLabel(selected.distance).asString()),
                    onClick = onStart,
                    isLoading = state.isStarting,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }
            // 기록런은 탭 화면이라 솟은 탭 원 높이만큼 아래를 비운다(D100).
            Spacer(Modifier.height(RUNKK_BOTTOM_BAR_RISE))
        }
    }
}

/**
 * 셀럽런(E-1, D-288): 거리 칩, 셀럽 카드(SD 그림, 이름, 거리, 기록, 서버 출처 표기 그대로). 셀프런과 같이 처음엔 고른 셀럽이 없고
 * 카드를 눌러야 "대결하기"가 보인다.
 */
@Composable
fun CelebBattleScreen(
    state: RecordBattleUiState,
    onFilter: (Int) -> Unit,
    onSelect: (CandidateUi) -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    RecordBattleFrame(state, onMessageShown, modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CELEB_FILTERS.forEachIndexed { index, (label, _) ->
                    RunkkChoiceChip(
                        label = label.asString(),
                        isSelected = index == state.filterIndex,
                        onSelectedChange = { onFilter(index) }
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                when {
                    state.isLoading -> RunkkLoading()

                    state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)

                    state.candidates.isEmpty() -> RunkkEmptyState(
                        title = stringResource(R.string.battle_celeb_empty_title)
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.candidates, key = { it.celebrityId ?: it.name.hashCode().toLong() }) { candidate ->
                            CelebCandidateCard(
                                candidate,
                                isSelected = candidate == state.selected,
                                onClick = { onSelect(candidate) }
                            )
                        }
                    }
                }
            }
            state.selected?.let {
                RunkkPrimaryButton(
                    text = stringResource(R.string.battle_celeb_go),
                    onClick = onStart,
                    isLoading = state.isStarting,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }
            // 셀프런과 같이 솟은 탭 원 높이만큼 아래를 비운다(D100).
            Spacer(Modifier.height(RUNKK_BOTTOM_BAR_RISE))
        }
    }
}

/** 스낵바(시작 거부 안내)를 얹는 틀. */
@Composable
private fun RecordBattleFrame(
    state: RecordBattleUiState,
    onMessageShown: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
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
        content()
        RunkkSnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + RUNKK_BOTTOM_BAR_RISE)
        )
    }
}

@Composable
private fun CandidateBody(
    state: RecordBattleUiState,
    onRetry: () -> Unit,
    emptyTitle: Int,
    content: @Composable () -> Unit
) {
    when {
        state.isLoading -> RunkkLoading()

        state.error != null -> RunkkErrorState(state.error.asString(), onRetry = onRetry)

        state.candidates.isEmpty() -> RunkkEmptyState(
            title = stringResource(emptyTitle),
            message = stringResource(R.string.battle_record_empty_message)
        )

        else -> content()
    }
}

/**
 * 셀럽 카드 한 장(피그마 E-1 60865:7422): 셀럽 그림(코치는 둥근 얼굴) 58, 이름 15 굵게, 거리 11 굵게와 기록 13, 출처 11.
 * 고르기와 선택 표시는 셀프런 거리 카드(DistanceCardRow)와 같다.
 */
@Composable
private fun CelebCandidateCard(candidate: CandidateUi, isSelected: Boolean, onClick: () -> Unit) {
    val time = candidate.finishTime?.let(RunkkFormat::duration)
    val source = sourceLine(candidate)?.asString()
    val distance = distanceLabel(candidate.distance).asString()
    val description = listOfNotNull(candidate.name, distance, time, source).joinToString(", ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) RunkkColors.TextSecondary else RunkkColors.Border,
                RunkkShapes.ExtraLarge
            )
            .clickable(enabled = candidate.isSelectable, role = Role.RadioButton, onClick = onClick)
            .alpha(if (candidate.isSelectable) 1f else RunkkAlpha.Disabled)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CelebrityPortrait(size = 58.dp, figureKey = candidate.celebrityFigure)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                candidate.name,
                style = RunkkTextStyles.text(15, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    distance,
                    style = RunkkTextStyles.text(11, FontWeight.Bold),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text(
                    time ?: stringResource(R.string.battle_no_record),
                    style = RunkkTextStyles.Label,
                    color = RunkkColors.TextPrimary
                )
            }
            source?.let { Text(it, style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary) }
        }
    }
}

/** 출처 줄(FR-100, D-288): 서버 출처 표기("출처: …")를 그대로 보인다. 없으면 줄을 두지 않는다. */
internal fun sourceLine(candidate: CandidateUi): UiText? =
    candidate.sourceLabel?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }

@Preview(name = "나와의 대결", widthDp = 390, heightDp = 844)
@Composable
private fun SelfBattlePreview() {
    RunkkTheme(reduceMotion = true) {
        SelfBattleScreen(
            RecordBattleUiState(
                isLoading = false,
                candidates = listOf(
                    CandidateUi(
                        null,
                        "나",
                        Distance(5_000),
                        Duration.ofSeconds(1_625),
                        LocalDate.of(2026, 10, 12),
                        null,
                        null,
                        false
                    )
                )
            ),
            {},
            {},
            {},
            {}
        )
    }
}

/** 기록런 분절 순서: 셀프런, 셀럽런. */
internal const val SELF_SEGMENT = 0
internal const val CELEB_SEGMENT = 1
