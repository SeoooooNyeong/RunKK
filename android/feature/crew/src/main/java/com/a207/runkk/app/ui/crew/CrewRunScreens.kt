package com.a207.runkk.app.ui.crew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkBanner
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkHint
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLiveDot
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkLongPressButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkProgressBar
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentRow
import com.a207.runkk.app.core.designsystem.RunkkSelectField
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkStatTile
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRunResult
import com.a207.runkk.app.domain.crew.Lobby
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- 모집글 쓰기 ----------

@Composable
fun CrewRunWriteRoute(key: Routes.CrewRunWrite, viewModel: CrewRunWriteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewRunWriteEvent.Posted -> navigator.back()
            }
        }
    }
    CrewRunWriteScreen(
        state = state,
        onBack = navigator::back,
        onTitle = viewModel::onTitle,
        onBody = viewModel::onBody,
        onDistance = viewModel::onDistance,
        onDay = viewModel::onDay,
        onTime = viewModel::onTime,
        onSubmit = { viewModel.onSubmit(key.crewId) }
    )
}

@Composable
fun CrewRunWriteScreen(
    state: CrewRunWriteUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onDistance: (Distance) -> Unit,
    onDay: (Int) -> Unit,
    onTime: (LocalTime) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = "크루런 모집", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RunkkTextField(value = state.title, onValueChange = onTitle, label = "제목", placeholder = "토요일 아침 5K 같이 뛰어요")
            RunkkTextField(
                value = state.body,
                onValueChange = onBody,
                label = "내용(선택)",
                placeholder = "페이스·분위기를 알려 주세요",
                singleLine = false
            )
            Text("크루런 거리", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            RunkkSegmentRow(
                options = CREW_RUN_DISTANCES.map(RunkkFormat::targetLabel),
                selectedIndex = CREW_RUN_DISTANCES.indexOf(state.distance),
                onSelect = { onDistance(CREW_RUN_DISTANCES[it]) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkSelectField(
                    label = "날짜",
                    options = (0 until CrewRunWriteUiState.DAYS).toList(),
                    selected = state.dayOffset,
                    optionLabel = { offset -> dayLabel(state, offset) },
                    onSelect = onDay,
                    modifier = Modifier.weight(1f)
                )
                RunkkSelectField(
                    label = "출발 시각",
                    options = CrewRunWriteUiState.TIMES,
                    selected = state.time,
                    optionLabel = { it.format(TIME) },
                    onSelect = onTime,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "장소는 정하지 않아요. 같은 시간에 각자 있는 곳에서 함께 출발하고, 위치 대신 달린 거리만 공유돼요. " +
                    "시작 30분 전부터 대기실이 열려요.",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            state.error?.let {
                Text(
                    it,
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = RunkkColors.Lavender,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        RunkkPrimaryButton(
            "모집글 올리기",
            onClick = onSubmit,
            enabled = state.valid,
            loading = state.saving,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

private fun dayLabel(state: CrewRunWriteUiState, offset: Int): String {
    val date = state.today.plusDays(offset.toLong())
    val prefix = when (offset) {
        0 -> "오늘 "
        1 -> "내일 "
        else -> ""
    }
    return prefix + date.format(DAY)
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN)
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// ---------- 대기실·진행 ----------

@Composable
fun CrewRunRoute(key: Routes.CrewRunLobby, viewModel: CrewRunViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CrewRunEvent.Finished -> {
                    navigator.back()
                    navigator.navigate(Routes.CrewRunResult(event.postId))
                }

                CrewRunEvent.Left -> navigator.back()
            }
        }
    }
    val running = state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN
    BackHandler {
        when {
            running -> viewModel.onQuitTapped()

            state.phase == CrewRunPhase.CANCELLED -> {
                viewModel.onCancelledClosed()
                navigator.back()
            }

            else -> viewModel.onLeaveLobby()
        }
    }
    CrewRunScreen(
        state = state,
        actions = CrewRunActions(
            onReady = viewModel::onReadyToggle,
            onRefresh = viewModel::enter,
            onLeave = viewModel::onLeaveLobby,
            onQuitTap = viewModel::onQuitTapped,
            onQuitLongPress = viewModel::onQuitLongPressed,
            onQuitConfirm = viewModel::onQuitConfirmed,
            onQuitDismiss = viewModel::onQuitDismissed,
            onClose = {
                viewModel.onCancelledClosed()
                navigator.back()
            }
        )
    )
}

data class CrewRunActions(
    val onReady: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onQuitTap: () -> Unit = {},
    val onQuitLongPress: () -> Unit = {},
    val onQuitConfirm: () -> Unit = {},
    val onQuitDismiss: () -> Unit = {},
    val onClose: () -> Unit = {}
)

@Composable
fun CrewRunScreen(state: CrewRunUiState, actions: CrewRunActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RunkkColors.Background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkLiveDot(color = if (state.phase == CrewRunPhase.RUNNING) RunkkColors.Lavender else RunkkColors.Lime)
            Text(
                "CREW RUN" + (state.distance?.let { " · ${RunkkFormat.targetLabel(it)}" } ?: ""),
                style = RunkkTextStyles.Overline,
                color = if (state.phase == CrewRunPhase.RUNNING) RunkkColors.Lavender else RunkkColors.Lime,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
        }
        when (state.phase) {
            CrewRunPhase.ENTERING -> if (state.error != null) {
                RunkkErrorState(state.error, onRetry = actions.onRefresh, modifier = Modifier.weight(1f))
            } else {
                RunkkLoading(Modifier.weight(1f))
            }

            CrewRunPhase.LOBBY -> LobbyBody(state, actions)

            CrewRunPhase.COUNTDOWN -> CountdownBody(state)

            CrewRunPhase.RUNNING, CrewRunPhase.FINISHED -> RelayBody(state)

            CrewRunPhase.CANCELLED -> RunkkErrorState(
                "크루런이 취소됐어요" + (state.cancelledReason?.let { " · $it" } ?: ""),
                onRetry = actions.onClose,
                retryLabel = "돌아가기",
                modifier = Modifier.weight(1f)
            )
        }
        if (state.phase == CrewRunPhase.RUNNING || state.phase == CrewRunPhase.COUNTDOWN) {
            state.hint?.let { RunkkHint(it, modifier = Modifier.align(Alignment.CenterHorizontally)) }
            RunkkLongPressButton(
                onTap = actions.onQuitTap,
                onLongPress = actions.onQuitLongPress,
                tapLabel = "그만두기 안내",
                longPressLabel = "길게 눌러 그만두기",
                contentDescription = "크루런 그만두기",
                caption = "길게 눌러 그만두기",
                size = 72.dp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    RunkkIcons.Close,
                    contentDescription = null,
                    tint = RunkkColors.Lavender,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
    if (state.confirmingQuit) {
        RunkkConfirmDialog(
            title = "크루런을 그만둘까요?",
            message = "그만두면 순위에서 빠지고 크루원에게 \"그만둠\"으로 보여요. 지금까지 달린 기록은 저장돼 크루 거리에 더해져요.",
            confirmLabel = "그만두기",
            onConfirm = actions.onQuitConfirm,
            onDismiss = actions.onQuitDismiss,
            dismissLabel = "계속 달리기",
            destructive = true
        )
    }
}

@Composable
private fun ColumnScope.LobbyBody(state: CrewRunUiState, actions: CrewRunActions) {
    val lobby = state.lobby ?: return
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("출발까지", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            Text(untilLabel(state.untilStart), style = RunkkTextStyles.display(72), color = RunkkColors.Lime)
            Text(meetLabel(lobby.meetAt), style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkStatTile(
                "크루런 거리",
                state.distance?.let(RunkkFormat::targetLabel) ?: RunkkFormat.EMPTY,
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
            RunkkStatTile(
                "준비 완료",
                "${lobby.participants.count { it.ready }}/${lobby.participants.size}",
                spoken = "${lobby.participants.size}명 중 ${lobby.participants.count { it.ready }}명",
                centered = true,
                valueStyle = RunkkTextStyles.DisplaySmall,
                modifier = Modifier.weight(1f)
            )
        }
        Participants(lobby)
        Text(
            "시간이 되면 3초 카운트다운 뒤 다 같이 출발해요. 위치는 공유되지 않고 달린 거리만 보여요. " +
                "1km마다 순위를 음성으로 알려 줘요.",
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        state.error?.let {
            Text(
                it,
                style = RunkkTextStyles.Caption,
                color = RunkkColors.Lavender,
                modifier = Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                }
            )
        }
    }
    RunkkPrimaryButton(
        if (state.ready) "준비 완료 · 시작을 기다리는 중" else "준비 완료",
        onClick = actions.onReady,
        enabled = state.distance != null,
        loading = state.busy,
        modifier = Modifier.fillMaxWidth()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkSecondaryButton("새로 고침", onClick = actions.onRefresh, modifier = Modifier.weight(1f), height = 48.dp)
        RunkkSecondaryButton("대기실 나가기", onClick = actions.onLeave, modifier = Modifier.weight(1f), height = 48.dp)
    }
}

@Composable
private fun Participants(lobby: Lobby) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lobby.participants.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(RunkkColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    p.displayName,
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                RunkkBadge(
                    if (p.ready) "준비 완료" else "들어오는 중",
                    tone = if (p.ready) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.CountdownBody(state: CrewRunUiState) {
    Column(
        Modifier.weight(1f).fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text(state.countdown?.toString() ?: "READY", style = RunkkTextStyles.display(160), color = RunkkColors.Lime)
        Text("다 같이 출발해요", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
    }
}

/** 진행 중 중계(FR-149): 순위·표시명·누적 거리·연결 상태. */
@Composable
private fun ColumnScope.RelayBody(state: CrewRunUiState) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("위치는 공유되지 않고 달린 거리만 보여요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        val total = state.distance?.meters?.coerceAtLeast(1)
        state.relay.forEach { row ->
            val status = connectionLabel(row.connection)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(RunkkColors.Surface)
                    .border(1.dp, if (row.rank == 1) RunkkColors.Lime else RunkkColors.Border, RunkkShapes.Medium)
                    .padding(16.dp)
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull(
                            row.rank?.let { "${it}위" },
                            row.name,
                            "${RunkkFormat.km(row.distance)}킬로미터",
                            status
                        ).joinToString(", ")
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        row.rank?.toString() ?: "-",
                        style = RunkkTextStyles.display(24),
                        color = if (row.rank == 1) RunkkColors.Lime else RunkkColors.TextSecondary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        row.name,
                        style = RunkkTextStyles.BodyStrong,
                        color = RunkkColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    status?.let { RunkkBadge(it, tone = RunkkBadgeTone.Muted) }
                    Text(
                        "${RunkkFormat.km(row.distance)} KM",
                        style = RunkkTextStyles.display(20),
                        color = RunkkColors.TextPrimary
                    )
                }
                if (total != null) {
                    RunkkProgressBar(
                        row.distance.meters.toFloat() / total,
                        color = if (row.rank == 1) RunkkColors.Lime else RunkkColors.Lavender
                    )
                }
            }
        }
        if (state.relay.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("크루원 진행을 기다리는 중", style = RunkkTextStyles.Body, color = RunkkColors.TextSecondary)
            }
        }
        state.error?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.Lavender) }
    }
}

/** 연결 상태 문구(서버 CrewRunRelay.Connection 이름). 연결됨이면 null. */
internal fun connectionLabel(connection: String): String? = when (connection) {
    "CHECKING" -> "연결 확인 중"
    "QUIT" -> "그만둠"
    "ARRIVED" -> "도착"
    else -> null
}

/** 남은 시간 "12:34" 또는 "1:02:03". */
internal fun untilLabel(left: Duration?): String = left?.let(RunkkFormat::duration) ?: RunkkFormat.EMPTY

// ---------- 결과 ----------

@Composable
fun CrewRunResultRoute(postId: Long, viewModel: CrewRunResultViewModel = hiltViewModel()) {
    LaunchedEffect(postId) { viewModel.load(postId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    CrewRunResultScreen(
        state = state,
        onCard = { navigator.navigate(Routes.RECORDS) },
        onHome = { navigator.restart(Routes.HOME) },
        onRetry = { viewModel.load(postId, force = true) }
    )
}

@Composable
fun CrewRunResultScreen(
    state: CrewRunResultUiState,
    onCard: () -> Unit,
    onHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "CREW RUN RESULT",
                style = RunkkTextStyles.Overline,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            val result = state.result
            when {
                result == null && state.loading -> Box(Modifier.fillMaxWidth().height(240.dp)) { RunkkLoading() }
                result == null -> RunkkErrorState(state.error ?: "결과를 불러오지 못했어요", onRetry = onRetry)
                else -> ResultBody(state, result)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunkkSecondaryButton("기록 카드", onClick = onCard, modifier = Modifier.weight(1f))
            RunkkPrimaryButton("홈으로", onClick = onHome, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultBody(state: CrewRunResultUiState, result: CrewRunResult) {
    val all = result.finishers.size
    Column(
        Modifier.fillMaxWidth().clip(
            RunkkShapes.ExtraLarge
        ).background(RunkkColors.Lime).padding(24.dp).semantics(mergeDescendants = true) {
        },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            if (state.finishedCount == all && all > 0) "${all}명 모두 완주했어요!" else "${all}명 중 ${state.finishedCount}명 완주",
            style = RunkkTextStyles.text(20, FontWeight.Black),
            color = RunkkColors.OnLime
        )
        Text(
            "크루 주간 거리에 +${RunkkFormat.kmShort(result.raceContribution)}km",
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.OnLime
        )
    }
    if (result.firstCrewRun) RunkkBanner("우리 크루의 첫 크루런이에요!")
    result.finishers.sortedBy { it.rank ?: Int.MAX_VALUE }.forEach { f ->
        val time = f.elapsedSeconds?.let { RunkkFormat.duration(Duration.ofSeconds(it)) }
        val pace = f.averagePaceSecondsPerKm?.takeIf { it > 0 }?.let { RunkkFormat.pace(Pace(it)) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Medium)
                .background(RunkkColors.Surface)
                .border(1.dp, if (f.rank == 1) RunkkColors.Lime else RunkkColors.Border, RunkkShapes.Medium)
                .padding(16.dp)
                .clearAndSetSemantics {
                    contentDescription = listOfNotNull(
                        f.rank?.let { "${it}위" } ?: "미완주",
                        f.displayName,
                        f.elapsedSeconds?.let { RunkkFormat.durationSpoken(Duration.ofSeconds(it)) },
                        f.averagePaceSecondsPerKm?.takeIf { it > 0 }?.let { "평균 " + RunkkFormat.paceSpoken(Pace(it)) }
                    ).joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                f.rank?.toString() ?: "-",
                style = RunkkTextStyles.display(24),
                color = if (f.rank == 1) RunkkColors.Lime else RunkkColors.TextSecondary,
                modifier = Modifier.width(24.dp)
            )
            Text(
                f.displayName,
                style = RunkkTextStyles.BodyStrong,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (f.rank == null) {
                RunkkBadge("미완주", tone = RunkkBadgeTone.Muted)
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        time ?: RunkkFormat.EMPTY,
                        style = RunkkTextStyles.display(20),
                        color = RunkkColors.TextPrimary
                    )
                    pace?.let { Text("평균 $it/km", style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary) }
                }
            }
        }
    }
}

@Preview(name = "크루런 대기실", widthDp = 390, heightDp = 844)
@Composable
private fun CrewRunLobbyPreview() {
    RunkkTheme(reduceMotion = true) { CrewRunScreen(CrewRunUiState(phase = CrewRunPhase.ENTERING), CrewRunActions()) }
}
