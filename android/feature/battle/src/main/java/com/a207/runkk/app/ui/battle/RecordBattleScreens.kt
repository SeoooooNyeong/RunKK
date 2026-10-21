package com.a207.runkk.app.ui.battle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
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
        title = "나와의 싸움",
        subtitle = "거리마다 내 최고 기록이 상대가 돼요. 달리는 동안 과거의 나와 몇 m 차이인지 알려 줘요.",
        state = state,
        onHistory = onHistory,
        onRetry = onRetry,
        onMessageShown = onMessageShown,
        emptyTitle = "아직 대결할 기록이 없어요",
        modifier = modifier,
        bottom = {
            RunkkPrimaryButton(
                text = state.selected?.let { "${distanceLabel(it.distance)} 과거의 나와 대결" } ?: "거리를 골라 주세요",
                onClick = onStart,
                enabled = state.selected != null,
                loading = state.starting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { candidate ->
        SelfCandidateCard(candidate, selected = candidate == state.selected, onClick = { onSelect(candidate) })
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
        title = "셀럽과 대결",
        subtitle = "공개된 기록만 써요. 셀럽은 SD 캐릭터와 닉네임으로 보여요.",
        state = state,
        onHistory = onHistory,
        onRetry = onRetry,
        onMessageShown = onMessageShown,
        emptyTitle = "이 거리의 셀럽 기록이 없어요",
        modifier = modifier,
        header = {
            RunkkSegmentedTabs(
                options = CELEB_FILTERS.map { it.first },
                selectedIndex = state.filterIndex,
                onSelect = onFilter,
                compact = true
            )
        }
    ) { candidate ->
        CelebCandidateCard(candidate, onClick = { onPick(candidate) })
    }
    val selected = state.selected
    if (state.confirming && selected != null) {
        RunkkConfirmDialog(
            title = "${selected.name}와 대결할까요?",
            message =
                "${distanceLabel(
                    selected.distance
                )} · ${selected.finishTime?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY}" +
                    " 기록과 함께 달려요. 3초 뒤 측정을 시작해요.",
            confirmLabel = "대결 시작",
            onConfirm = onConfirm,
            onDismiss = onDismissConfirm
        )
    }
}

@Composable
private fun RecordBattleScaffold(
    title: String,
    subtitle: String,
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
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            RunkkTopBar(title = title) {
                RunkkIconButton(RunkkIcons.History, contentDescription = "함께 달린 기록", onClick = onHistory)
            }
            Text(
                subtitle,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (header != null) {
                Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { header() }
                // 필터(거리)를 바꾸면 후보 수를 알린다.
                if (!state.loading && state.error == null) {
                    FilterCount("기록 ${state.candidates.size}개", modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                }
            }
            when {
                state.loading && state.candidates.isEmpty() -> RunkkLoading(modifier = Modifier.weight(1f))

                state.error != null && state.candidates.isEmpty() ->
                    RunkkErrorState(state.error, onRetry = onRetry, modifier = Modifier.weight(1f))

                state.candidates.isEmpty() -> RunkkEmptyState(
                    title = emptyTitle,
                    message = "자유런으로 기록을 쌓으면 대결할 수 있어요",
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
            if (bottom != null) {
                HorizontalDivider(color = RunkkColors.Border)
                Box(Modifier.padding(16.dp)) { bottom() }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun SelfCandidateCard(candidate: CandidateUi, selected: Boolean, onClick: () -> Unit) {
    val label = distanceLabel(candidate.distance)
    RunkkCard(
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        onClick = if (candidate.selectable) onClick else null,
        onClickLabel = "$label 고르기",
        borderColor = if (selected) RunkkColors.Lime else RunkkColors.Border,
        background = if (selected) RunkkColors.LimeContainer else RunkkColors.Surface
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = selfSpoken(candidate) }
        ) {
            Text(
                label,
                style = RunkkTextStyles.display(32),
                color = if (candidate.selectable) RunkkColors.Lime else RunkkColors.TextSecondary,
                modifier = Modifier.size(width = 72.dp, height = 40.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (candidate.finishTime != null) {
                    Text(
                        "최고 ${RunkkFormat.duration(candidate.finishTime)}",
                        style = RunkkTextStyles.text(16, FontWeight.Black),
                        color = RunkkColors.TextPrimary
                    )
                    Text(
                        listOfNotNull(
                            candidate.recordedOn?.let(::dateLabel),
                            "평균 ${RunkkFormat.pace(candidate.averagePace)}/km"
                        ).joinToString(" · "),
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary
                    )
                } else {
                    Text("아직 기록이 없어요", style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CelebCandidateCard(candidate: CandidateUi, onClick: () -> Unit) {
    RunkkCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (candidate.selectable) onClick else null,
        onClickLabel = "${candidate.name}와 대결하기"
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = celebSpoken(candidate) }
        ) {
            SdCharacter(size = 48)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    candidate.name,
                    style = RunkkTextStyles.text(15, FontWeight.Black),
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${distanceLabel(
                        candidate.distance
                    )} · ${candidate.finishTime?.let(RunkkFormat::duration) ?: "기록 없음"}",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextPrimary
                )
                Text(sourceLine(candidate), style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary)
            }
        }
    }
}

/** 보조 기술용: "5킬로미터, 최고 27분 30초, 2026.10.12, 평균 1킬로미터당 5분 30초". */
internal fun selfSpoken(c: CandidateUi): String = listOfNotNull(
    spokenDistance(c.distance),
    c.finishTime?.let { "최고 " + RunkkFormat.durationSpoken(it) } ?: "아직 기록이 없어요",
    c.recordedOn?.let(::dateLabel),
    c.averagePace?.let { "평균 " + RunkkFormat.paceSpoken(it) }
).joinToString(", ")

internal fun celebSpoken(c: CandidateUi): String = listOfNotNull(
    c.name,
    spokenDistance(c.distance),
    c.finishTime?.let(RunkkFormat::durationSpoken) ?: "기록 없음",
    sourceLine(c)
).joinToString(", ")

/** "5K"를 "5킬로미터"로(하프·풀은 "하프 마라톤"·"풀 마라톤"). */
internal fun spokenDistance(distance: Distance): String = when (val label = distanceLabel(distance)) {
    "하프", "풀" -> "$label 마라톤"
    else -> RunkkFormat.spokenLabel(label)
}

/** 출처 줄(FR-100): "공개 기록 기준 · 출처". 구간 기록이 없으면 추정 안내를 붙인다(A-006). */
internal fun sourceLine(candidate: CandidateUi): String = buildString {
    append("공개 기록 기준")
    candidate.sourceLabel?.let { append(" · ").append(it) }
    if (candidate.estimated) append(" · 구간 기록이 없으면 평균 페이스로 추정")
}

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

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

@Preview(name = "나와의 싸움", widthDp = 390, heightDp = 844)
@Composable
private fun SelfBattlePreview() {
    RunkkTheme(reduceMotion = true) {
        SelfBattleScreen(
            RecordBattleUiState(
                loading = false,
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
