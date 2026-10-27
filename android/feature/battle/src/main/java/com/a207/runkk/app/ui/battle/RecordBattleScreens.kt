package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 셀프 탭: 나와의 싸움 선택(FR-096). */
@Composable
fun SelfBattleRoute(viewModel: SelfBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    SelfBattleScreen(
        state = state,
        onSelect = viewModel::onSelect,
        onStart = viewModel::onStart,
        onHistory = { navigator.navigate(Routes.BATTLE_HISTORY) },
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun SelfBattleScreen(
    state: RecordBattleUiState,
    onSelect: (CandidateUi) -> Unit,
    onStart: () -> Unit,
    onHistory: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    RecordBattleScaffold(
        title = stringResource(R.string.battle_type_self),
        subtitle = stringResource(R.string.battle_self_question),
        state = state,
        onHistory = onHistory,
        onRetry = onRetry,
        onMessageShown = onMessageShown,
        emptyTitle = stringResource(R.string.battle_self_empty_title),
        modifier = modifier,
        bottom = {
            RunkkPrimaryButton(
                text = state.selected?.let {
                    stringResource(R.string.battle_self_start, distanceLabel(it.distance).asString())
                } ?: stringResource(R.string.battle_self_choose_distance),
                onClick = onStart,
                isEnabled = state.selected != null,
                isLoading = state.isStarting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { candidate ->
        SelfCandidateCard(candidate, isSelected = candidate == state.selected, onClick = { onSelect(candidate) })
    }
}

/** 셀럽 탭: 유명인 선택(FR-097·FR-100). */
@Composable
fun CelebBattleRoute(viewModel: CelebBattleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    CelebBattleScreen(
        state = state,
        onFilter = viewModel::onFilter,
        onPick = viewModel::onPick,
        onConfirm = viewModel::onStart,
        onDismissConfirm = viewModel::onDismissConfirm,
        onHistory = { navigator.navigate(Routes.BATTLE_HISTORY) },
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown
    )
}

@Composable
fun CelebBattleScreen(
    state: RecordBattleUiState,
    onFilter: (Int) -> Unit,
    onPick: (CandidateUi) -> Unit,
    onConfirm: () -> Unit,
    onDismissConfirm: () -> Unit,
    onHistory: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    RecordBattleScaffold(
        title = stringResource(R.string.battle_celeb_title),
        subtitle = null,
        state = state,
        onHistory = onHistory,
        onRetry = onRetry,
        onMessageShown = onMessageShown,
        emptyTitle = stringResource(R.string.battle_celeb_empty_title),
        modifier = modifier,
        header = {
            RunkkSegmentedTabs(
                options = CELEB_FILTERS.map { it.first.asString() },
                selectedIndex = state.filterIndex,
                onSelect = onFilter,
                isCompact = true
            )
        }
    ) { candidate ->
        // 보드 H2: "대결"을 누르면 확인 없이 바로 유명인 대결을 시작한다.
        CelebCandidateCard(
            candidate,
            isStarting = state.isStarting,
            onClick = {
                onPick(candidate)
                onConfirm()
            }
        )
    }
}

@Composable
private fun RecordBattleScaffold(
    title: String,
    subtitle: String?,
    state: RecordBattleUiState,
    onHistory: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
    emptyTitle: String,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    item: @Composable (CandidateUi) -> Unit
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
            ScaffoldHead(title, subtitle, onHistory, header)
            CandidateBody(state, emptyTitle, onRetry, item)
            if (bottom != null) {
                HorizontalDivider(color = RunkkColors.Border)
                Box(Modifier.padding(16.dp)) { bottom() }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

/** 위쪽: 제목줄("함께 달린 기록 ›"), 질문(있으면), 거르기 머리. */
@Composable
private fun ColumnScope.ScaffoldHead(
    title: String,
    subtitle: String?,
    onHistory: () -> Unit,
    header: (@Composable () -> Unit)?
) {
    RunkkTopBar(title = title) {
        RunkkTextLink(stringResource(R.string.battle_history_link), onClick = onHistory)
    }
    if (subtitle != null) {
        Text(
            subtitle,
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() }
        )
    }
    if (header != null) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { header() }
    }
}

/** 후보 목록 본문: 읽는 중·오류·빈 목록·후보 줄. */
@Composable
private fun ColumnScope.CandidateBody(
    state: RecordBattleUiState,
    emptyTitle: String,
    onRetry: () -> Unit,
    item: @Composable (CandidateUi) -> Unit
) {
    when {
        state.isLoading && state.candidates.isEmpty() -> RunkkLoading(modifier = Modifier.weight(1f))

        state.error != null && state.candidates.isEmpty() ->
            RunkkErrorState(state.error.asString(), onRetry = onRetry, modifier = Modifier.weight(1f))

        state.candidates.isEmpty() -> RunkkEmptyState(
            title = emptyTitle,
            message = stringResource(R.string.battle_record_empty_message),
            modifier = Modifier.weight(1f)
        )

        else -> LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.candidates, key = { "${it.celebrityId}-${it.distance.meters}-${it.name}" }) { item(it) }
        }
    }
}

/** 나와의 싸움 거리 한 칸(H1): 거리, "내 최고 기록"과 기록, 기록한 날. 기록이 없으면 "기록 없음". */
@Composable
private fun SelfCandidateCard(candidate: CandidateUi, isSelected: Boolean, onClick: () -> Unit) {
    val label = distanceLabel(candidate.distance).asString()
    val spoken = selfSpoken(candidate)
    RunkkCard(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = isSelected },
        onClick = if (candidate.isSelectable) onClick else null,
        onClickLabel = stringResource(R.string.battle_record_select_label, label),
        borderColor = if (isSelected) RunkkColors.Lime else RunkkColors.Border,
        background = if (isSelected) RunkkColors.LimeContainer else RunkkColors.Surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = spoken }
        ) {
            Text(
                label,
                style = RunkkTextStyles.display(28),
                color = if (candidate.isSelectable) RunkkColors.Lime else RunkkColors.TextSecondary,
                modifier = Modifier.width(72.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.battle_self_best_label),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                Text(
                    candidate.finishTime?.let(RunkkFormat::duration) ?: stringResource(R.string.battle_no_record),
                    style = RunkkTextStyles.text(16, FontWeight.Black),
                    color = if (candidate.finishTime != null) RunkkColors.TextPrimary else RunkkColors.TextSecondary
                )
            }
            candidate.recordedOn?.let {
                Text(shortDateLabel(it), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
        }
    }
}

/** 유명인 한 칸(H2): SD 캐릭터, 이름, "거리, 기록", 출처, "대결" 버튼. */
@Composable
private fun CelebCandidateCard(candidate: CandidateUi, isStarting: Boolean, onClick: () -> Unit) {
    val spoken = celebSpoken(candidate)
    val bestTime = candidate.finishTime?.let(RunkkFormat::duration) ?: stringResource(R.string.battle_no_record)
    val distance = distanceLabel(candidate.distance).asString()
    RunkkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SdCharacter(size = 48)
            Column(
                modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    candidate.name,
                    style = RunkkTextStyles.text(15, FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.battle_celeb_record, distance, bestTime),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextPrimary
                )
                sourceLine(candidate)?.let {
                    Text(it.asString(), style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary)
                }
            }
            RunkkLimePillButton(
                stringResource(R.string.battle_celeb_start),
                onClick = onClick,
                isEnabled = candidate.isSelectable && !isStarting
            )
        }
    }
}

/** 보조 기술용: "5킬로미터, 최고 27분 30초, 2026.10.12, 평균 1킬로미터당 5분 30초". */
@Composable
internal fun selfSpoken(c: CandidateUi): String = listOfNotNull(
    spokenDistance(c.distance),
    c.finishTime?.let { stringResource(R.string.battle_record_best, RunkkFormat.durationSpoken(it)) }
        ?: stringResource(R.string.battle_no_record_yet),
    c.recordedOn?.let(::dateLabel),
    c.averagePace?.let { stringResource(R.string.battle_record_average_spoken, RunkkFormat.paceSpoken(it)) }
).joinToString(", ")

@Composable
internal fun celebSpoken(c: CandidateUi): String = listOfNotNull(
    c.name,
    spokenDistance(c.distance),
    c.finishTime?.let { RunkkFormat.durationSpoken(it) } ?: stringResource(R.string.battle_no_record),
    sourceLine(c)?.asString()
).joinToString(", ")

/** "5km"를 "5킬로미터"로(하프·풀은 "하프 마라톤"·"풀 마라톤"). */
@Composable
internal fun spokenDistance(distance: Distance): String {
    val label = distanceLabel(distance)
    return when (label) {
        UiText.Res(R.string.battle_distance_half) -> stringResource(R.string.battle_distance_half_marathon)
        UiText.Res(R.string.battle_distance_full) -> stringResource(R.string.battle_distance_full_marathon)
        else -> RunkkFormat.spokenLabel(label.asString())
    }
}

/** 출처 줄(FR-100, 보드 H2 "출처: [출처]"). 서버 출처 표기(sourceLabel)가 없으면 줄을 두지 않는다. */
internal fun sourceLine(candidate: CandidateUi): UiText? =
    candidate.sourceLabel?.let { uiText(R.string.battle_record_source, it) }

/** 유명인 공통 SD 캐릭터 자리(라벤더 원). 그림 자원은 2단계에 넣는다(FR-099). */
@Composable
internal fun SdCharacter(size: Int, modifier: Modifier = Modifier, label: String = "SD") {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(RunkkColors.Border)
            .border(2.dp, RunkkColors.Lavender, CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = RunkkTextStyles.display((size / 3).coerceAtLeast(10)), color = RunkkColors.Lavender)
    }
}

internal fun dateLabel(date: LocalDate): String = date.format(DATE)

/** 카드의 짧은 날짜(보드 H1 "[09.14]"). */
internal fun shortDateLabel(date: LocalDate): String = date.format(SHORT_DATE)

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM.dd")

@Preview(name = "나와의 싸움", widthDp = 390, heightDp = 844)
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
                        Duration.ofSeconds(1_650),
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
            {},
            {}
        )
    }
}
