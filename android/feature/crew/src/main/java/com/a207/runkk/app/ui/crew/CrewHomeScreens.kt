package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunimalTypeLabel
import com.a207.runkk.app.core.designsystem.RunkkAccentOutlinedButton
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSecondaryButton
import com.a207.runkk.app.core.designsystem.RunkkSegmentedTabs
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTabRow
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewDetail
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.CrewRunStatus
import com.a207.runkk.app.domain.crew.CrewVisibility
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.LobbyAvailability
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import kotlin.math.roundToInt

// ---------- 내 크루 홈 ----------

/** 내 크루 홈(시안 HiMyCrew). [isAtTabRoot]면 크루 탭 자리에 그려 뒤로 단추를 두지 않는다. */
@Composable
fun CrewHomeRoute(key: Routes.CrewHome, isAtTabRoot: Boolean = false, viewModel: CrewHomeViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    // 관리·모집 화면에서 돌아오면 지금 탭을 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewHomeEvent.Left -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    CrewHomeScreen(
        state = state,
        isAtTabRoot = isAtTabRoot,
        actions = CrewHomeActions(
            onBack = navigator::back,
            onTab = viewModel::onTab,
            onPeriod = viewModel::onPeriod,
            onRetry = viewModel::refresh,
            onCheerDraft = viewModel::onCheerDraft,
            onCheerSubmit = viewModel::onCheerSubmit,
            onDeleteAsk = viewModel::onDeleteAsk,
            onDeleteDismiss = viewModel::onDeleteDismiss,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onVote = viewModel::onVote,
            onWriteCrewRun = { navigator.navigate(Routes.CrewRunWrite(state.crewId)) },
            onLobby = { post -> navigator.navigate(Routes.CrewRunLobby(post.postId)) },
            onResult = { post -> navigator.navigate(Routes.CrewRunResult(post.postId)) },
            onManage = { navigator.navigate(Routes.CrewManage(state.crewId)) },
            onLeaveAsk = viewModel::onLeaveAsk,
            onLeaveDismiss = viewModel::onLeaveDismiss,
            onLeaveConfirm = viewModel::onLeaveConfirm,
            onIntroEdit = viewModel::onIntroEdit,
            onIntroDraft = viewModel::onIntroDraft,
            onIntroDismiss = viewModel::onIntroDismiss,
            onIntroSave = viewModel::onIntroSave,
            onMessageShown = viewModel::onMessageShown,
            onLoadMore = viewModel::onLoadMore
        )
    )
}

data class CrewHomeActions(
    val onBack: () -> Unit = {},
    val onTab: (CrewTab) -> Unit = {},
    val onPeriod: (CrewRanking.Period) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onCheerDraft: (String) -> Unit = {},
    val onCheerSubmit: () -> Unit = {},
    val onDeleteAsk: (PostList.Item) -> Unit = {},
    val onDeleteDismiss: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onVote: (PostList.Item, VoteAnswer) -> Unit = { _, _ -> },
    val onWriteCrewRun: () -> Unit = {},
    val onLobby: (PostList.Item) -> Unit = {},
    val onResult: (PostList.Item) -> Unit = {},
    val onManage: () -> Unit = {},
    val onLeaveAsk: () -> Unit = {},
    val onLeaveDismiss: () -> Unit = {},
    val onLeaveConfirm: () -> Unit = {},
    val onIntroEdit: () -> Unit = {},
    val onIntroDraft: (String) -> Unit = {},
    val onIntroDismiss: () -> Unit = {},
    val onIntroSave: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLoadMore: () -> Unit = {}
)

@Composable
fun CrewHomeScreen(
    state: CrewHomeUiState,
    actions: CrewHomeActions,
    modifier: Modifier = Modifier,
    isAtTabRoot: Boolean = false
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val scroll = rememberScrollState()
    // 게시글 탭에서 끝 근처까지 내리면 다음 쪽을 이어 받는다(다음 쪽이 없으면 ViewModel이 무시한다).
    val nearEnd by remember { derivedStateOf { scroll.maxValue - scroll.value <= LOAD_MORE_PX } }
    val hasMore = when (state.tab) {
        CrewTab.CREW_RUN -> state.crewRuns?.nextCursor != null
        CrewTab.CHEER -> state.cheers?.nextCursor != null
        else -> false
    }
    LaunchedEffect(nearEnd, hasMore, state.tab, state.crewRuns?.posts?.size, state.cheers?.posts?.size) {
        if (nearEnd && hasMore) actions.onLoadMore()
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, actions, isAtTabRoot)
            RunkkTabRow(
                options = CrewTab.entries.map { stringResource(it.labelRes) },
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onTab(CrewTab.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(scroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeTabContent(state, actions)
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    HomeDialogs(state, actions)
}

/** 고른 탭의 내용(읽기 실패면 오류 상태). */
@Composable
private fun ColumnScope.HomeTabContent(state: CrewHomeUiState, actions: CrewHomeActions) {
    if (state.error != null && !state.isLoading) {
        RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
    } else {
        when (state.tab) {
            CrewTab.RACE -> RaceTab(state, actions)
            CrewTab.CREW_RUN -> CrewRunTab(state, actions)
            CrewTab.CHEER -> CheerTab(state, actions)
            CrewTab.MEMBERS -> MembersTab(state, actions)
        }
        if (state.isLoadingMore) Box(Modifier.fillMaxWidth().height(64.dp)) { RunkkLoading() }
    }
}

/** 크루 홈의 확인 대화상자: 글 삭제·크루 나가기·크루 내 소개 수정. */
@Composable
private fun HomeDialogs(state: CrewHomeUiState, actions: CrewHomeActions) {
    if (state.confirmDelete != null) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_home_delete_title),
            message = stringResource(R.string.crew_home_delete_message),
            confirmLabel = stringResource(R.string.crew_action_delete),
            onConfirm = actions.onDeleteConfirm,
            onDismiss = actions.onDeleteDismiss,
            isDestructive = true
        )
    }
    if (state.isConfirmingLeave) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_home_leave_title),
            message = stringResource(R.string.crew_home_leave_message),
            confirmLabel = stringResource(R.string.crew_home_leave),
            onConfirm = actions.onLeaveConfirm,
            onDismiss = actions.onLeaveDismiss,
            isDestructive = true
        )
    }
    if (state.isEditingIntro) IntroDialog(state.introDraft, actions)
}

/** 크루 내 한 줄 소개 수정 대화상자. */
@Composable
private fun IntroDialog(draft: String, actions: CrewHomeActions) {
    AlertDialog(
        onDismissRequest = actions.onIntroDismiss,
        containerColor = RunkkColors.Surface,
        title = {
            Text(
                stringResource(R.string.crew_home_intro_title),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary
            )
        },
        text = {
            RunkkTextField(
                value = draft,
                onValueChange = actions.onIntroDraft,
                label = stringResource(R.string.crew_home_intro_label),
                maxLength = CrewHomeUiState.INTRO_MAX
            )
        },
        confirmButton = {
            TextButton(onClick = actions.onIntroSave) {
                Text(
                    stringResource(R.string.crew_home_intro_save),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onIntroDismiss) {
                Text(
                    stringResource(R.string.crew_action_cancel),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    )
}

/** 끝에서 이 픽셀 안쪽까지 내리면 다음 쪽을 읽는다. */
private const val LOAD_MORE_PX = 600

@Composable
private fun Header(state: CrewHomeUiState, actions: CrewHomeActions, isAtTabRoot: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (isAtTabRoot) 16.dp else 8.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isAtTabRoot) {
            RunkkIconButton(
                RunkkIcons.Back,
                contentDescription = stringResource(R.string.crew_action_back),
                onClick = actions.onBack
            )
        }
        CrewLogo(state.crewName, size = 48, isAccent = true)
        HomeTitle(state, Modifier.weight(1f))
        HomeMenu(state, actions)
    }
}

/** 머리글의 크루 이름과 인원·내 역할. */
@Composable
private fun HomeTitle(state: CrewHomeUiState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            state.crewName,
            style = RunkkTextStyles.TopBarTitle,
            color = RunkkColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        (state.detail?.headcount ?: state.members?.members?.size)?.let { headcount ->
            val role = state.detail?.myRole?.let { stringResource(it.labelRes()) }
            Text(
                listOfNotNull(stringResource(R.string.crew_meta_headcount, headcount, CREW_MAX), role)
                    .joinToString(" · "),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

@StringRes
private fun CrewRole.labelRes(): Int = when (this) {
    CrewRole.LEADER -> R.string.crew_role_leader
    CrewRole.MEMBER -> R.string.crew_role_member
}

/** 머리글 오른쪽 설정 메뉴: 크루 관리(크루장만)·크루 내 소개 바꾸기·크루 나가기. */
@Composable
private fun HomeMenu(state: CrewHomeUiState, actions: CrewHomeActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        RunkkIconButton(
            RunkkIcons.Settings,
            contentDescription = stringResource(R.string.crew_home_menu),
            onClick = { menu = true }
        )
        DropdownMenu(expanded = menu, onDismissRequest = {
            menu = false
        }, modifier = Modifier.background(RunkkColors.Surface)) {
            listOfNotNull(
                (stringResource(R.string.crew_home_menu_manage) to actions.onManage).takeIf { state.canManage },
                stringResource(R.string.crew_home_intro_change) to actions.onIntroEdit,
                stringResource(R.string.crew_home_leave) to actions.onLeaveAsk
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) },
                    onClick = {
                        menu = false
                        action()
                    }
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.Loading(state: CrewHomeUiState) {
    if (state.isLoading) Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
}

/** 레이스 탭(FR-140·FR-137): 이번 주 크루 거리·마감·내 기여도, 주간·월간 크루 랭킹. */
@Composable
private fun ColumnScope.RaceTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val race = state.race
    if (race == null) {
        Loading(state)
        return
    }
    RaceCard(race, mine = race.contributors.firstOrNull { it.isMe })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.crew_race_ranking_title),
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        RunkkSegmentedTabs(
            options = listOf(
                stringResource(R.string.crew_race_period_weekly),
                stringResource(R.string.crew_race_period_monthly)
            ),
            selectedIndex = state.period.ordinal,
            onSelect = { actions.onPeriod(CrewRanking.Period.entries[it]) },
            isCompact = true
        )
    }
    val entries = state.ranking?.entries.orEmpty()
    if (entries.isEmpty()) {
        Text(
            stringResource(R.string.crew_race_ranking_empty),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
    // 기여율은 이번 주 크루 거리 대비(시안 31%), 레이스를 못 읽었거나 아직 0m면 랭킹 합 대비. 나누는 값은 1m 이상이다
    // (방금 만든 크루는 레이스 합계가 0이라 0/0 = NaN.roundToInt()로 비정상 종료했다, E2E 시나리오 5에서 찾은 결함).
    val weekly = if (state.period == CrewRanking.Period.WEEKLY) race.total.meters.takeIf { it > 0 } else null
    val total = (weekly ?: entries.sumOf { it.distance.meters }).coerceAtLeast(1)
    entries.forEach { entry ->
        val share = (entry.distance.meters * PERCENT / total.toDouble()).roundToInt().coerceIn(0, PERCENT)
        RankingRow(entry, share)
    }
}

/** 크루 랭킹 한 줄: 순위·표시명·거리·기여율. */
@Composable
private fun RankingRow(entry: CrewRanking.Entry, share: Int) {
    val description = stringResource(
        R.string.crew_race_ranking_description,
        entry.rank,
        entry.displayName,
        RunkkFormat.kmShort(entry.distance),
        share
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.Medium)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.Medium)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            entry.rank.toString(),
            style = RunkkTextStyles.display(24),
            color = if (entry.rank == 1) RunkkColors.Lime else RunkkColors.TextSecondary,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Text(
            entry.displayName,
            style = RunkkTextStyles.BodyStrong,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                RunkkFormat.kmShort(entry.distance),
                style = RunkkTextStyles.display(20),
                color = RunkkColors.TextPrimary
            )
            Text(
                stringResource(R.string.crew_race_share_percent, share),
                style = RunkkTextStyles.Micro,
                color = RunkkColors.TextSecondary
            )
        }
    }
}

@Composable
private fun RaceCard(race: RaceStatus, mine: RaceStatus.Contributor?) {
    val share = race.myShare
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RunkkShapes.ExtraLarge)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, RunkkShapes.ExtraLarge)
            .padding(24.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row {
            Text(
                stringResource(R.string.crew_race_weekly_distance),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.crew_race_deadline),
                style = RunkkTextStyles.text(12, FontWeight.Bold),
                color = RunkkColors.Lavender
            )
        }
        RaceTotal(race)
        Text(
            when {
                share == null -> stringResource(R.string.crew_race_my_share_empty)

                mine == null -> stringResource(R.string.crew_race_my_share, shareLabel(share))

                else -> stringResource(
                    R.string.crew_race_my_share_with_km,
                    shareLabel(share),
                    RunkkFormat.kmShort(mine.distance)
                )
            },
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary
        )
    }
}

/** 이번 주 크루 거리 큰 숫자. */
@Composable
private fun RaceTotal(race: RaceStatus) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(RunkkFormat.kmShort(race.total), style = RunkkTextStyles.display(64), color = RunkkColors.TextPrimary)
        Text(
            " KM",
            style = RunkkTextStyles.display(24),
            color = RunkkColors.TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** 기여율(0~1 또는 0~100)을 정수 %로(소수 버림, FR-140). */
internal fun shareLabel(share: Double): Int = (if (share <= 1.0) share * PERCENT else share).toInt()

private const val PERCENT = 100

/** 크루런 탭(FR-133·FR-145): 모집글, 갈래요·못 가요, 대기실·결과. */
@Composable
private fun ColumnScope.CrewRunTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    RunkkPrimaryButton(
        stringResource(R.string.crew_home_run_recruit),
        onClick = actions.onWriteCrewRun,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.crew_home_run_notice),
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary
    )
    val posts = state.crewRuns?.posts
    when {
        posts == null -> Loading(state)

        posts.isEmpty() -> RunkkEmptyState(
            stringResource(R.string.crew_home_run_empty_title),
            message = stringResource(R.string.crew_home_run_empty_message)
        )

        else -> posts.forEach { post -> CrewRunPost(post, state.lobbyEntries[post.postId], actions) }
    }
}

/**
 * 크루런 모집글(FR-133): 거리·모집 일시·참여 인원·상태와 내 응답(서버 crewRun.myAnswer). 투표 중이면 갈래요·못 가요,
 * 대기실 단추는 [lobby](모집 일시 30분 전부터 모집 일시 전까지 열림)를 따르고, 끝났으면 결과 단추를 보인다.
 */
@Composable
private fun CrewRunPost(post: PostList.Item, lobby: LobbyAvailability?, actions: CrewHomeActions) {
    val run = post.crewRun
    val status = run?.status
    val entry = CrewRunEntry.of(status, lobby)
    RunkkCard {
        CrewRunPostHeader(post, actions)
        if (post.summary.isNotBlank()) {
            Text(post.summary, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        }
        if (run != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    crewRunSummary(run),
                    style = RunkkTextStyles.Body,
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                status?.let {
                    RunkkBadge(stringResource(crewRunStatusLabelRes(it)), tone = RunkkBadgeTone.Muted)
                }
            }
        }
        CrewRunVote(post, run?.myAnswer, entry.canVote, actions)
        CrewRunEntryButtons(
            post,
            label = post.summary.ifBlank {
                run?.let { crewRunSummary(it) }.orEmpty()
            },
            entry,
            actions
        )
    }
}

/**
 * 모집글 상태로 고른 단추 표시: 투표(갈래요·못 가요), 대기실 단추([lobby], 끝·취소된 글이면 null), 지난 결과.
 * 상태를 모르면 투표·결과를 보이고 대기실은 시각으로만 판단한다.
 */
private data class CrewRunEntry(val canVote: Boolean, val lobby: LobbyAvailability?, val isFinished: Boolean) {
    companion object {
        fun of(status: CrewRunStatus?, lobby: LobbyAvailability?): CrewRunEntry = CrewRunEntry(
            canVote = status == null || status == CrewRunStatus.VOTING,
            lobby = lobby.takeUnless { status == CrewRunStatus.FINISHED || status == CrewRunStatus.CANCELLED },
            isFinished = status == null || status == CrewRunStatus.FINISHED
        )
    }
}

/** 모집글 머리줄: 크루런 배지·작성자·작성 시각과 내 글 삭제. */
@Composable
private fun CrewRunPostHeader(post: PostList.Item, actions: CrewHomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RunkkBadge(stringResource(R.string.crew_home_run_badge), tone = RunkkBadgeTone.Lime)
        Text(
            "  " + stringResource(R.string.crew_home_run_author_time, post.authorName, postedLabel(post.writtenAt)),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        if (post.isMine) {
            RunkkTextLink(
                stringResource(R.string.crew_action_delete),
                onClick = { actions.onDeleteAsk(post) },
                contentDescription = stringResource(R.string.crew_home_run_delete_description)
            )
        }
    }
}

/** 모집글의 응답: 투표 중이면 갈래요·못 가요 단추, 아니면 내 응답 문구. */
@Composable
private fun CrewRunVote(post: PostList.Item, vote: VoteAnswer?, canVote: Boolean, actions: CrewHomeActions) {
    if (canVote) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            VoteButton(
                stringResource(R.string.crew_home_run_vote_going),
                isSelected = vote == VoteAnswer.GOING,
                onClick = { actions.onVote(post, VoteAnswer.GOING) },
                modifier = Modifier.weight(1f)
            )
            VoteButton(
                stringResource(R.string.crew_home_run_vote_not_going),
                isSelected = vote == VoteAnswer.NOT_GOING,
                onClick = { actions.onVote(post, VoteAnswer.NOT_GOING) },
                modifier = Modifier.weight(1f)
            )
        }
    } else if (vote != null) {
        Text(
            stringResource(
                if (vote == VoteAnswer.GOING) R.string.crew_home_run_my_going else R.string.crew_home_run_my_not_going
            ),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
}

/**
 * 모집글 아래 단추: 대기실·지난 결과. 대기실은 열렸을 때만 누를 수 있고, 열리기 전에는 남은 분(1시간 넘게 남으면 "30분 전에 열려요"),
 * 모집 일시가 지났으면 닫혔다고 보인다(서버가 LOBBY_NOT_OPEN·ALREADY_STARTED로 거부하는 때를 미리 막는다).
 */
@Composable
private fun CrewRunEntryButtons(post: PostList.Item, label: String, entry: CrewRunEntry, actions: CrewHomeActions) {
    val resultDescription = stringResource(R.string.crew_home_run_result_description, label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        entry.lobby?.let { lobby ->
            val text = lobbyButtonText(lobby)
            val lobbyDescription = stringResource(R.string.crew_home_run_lobby_description, text, label)
            RunkkPillButton(
                text,
                onClick = { actions.onLobby(post) },
                isEnabled = lobby == LobbyAvailability.Open,
                modifier = Modifier.semantics { contentDescription = lobbyDescription }
            )
        }
        if (entry.isFinished) {
            RunkkPillButton(
                stringResource(R.string.crew_home_run_result),
                onClick = { actions.onResult(post) },
                modifier = Modifier.semantics { contentDescription = resultDescription }
            )
        }
    }
}

@Composable
private fun lobbyButtonText(lobby: LobbyAvailability): String = when (lobby) {
    LobbyAvailability.Open -> stringResource(R.string.crew_home_run_lobby)

    LobbyAvailability.Closed -> stringResource(R.string.crew_home_run_lobby_closed)

    is LobbyAvailability.NotYet -> if (lobby.minutesLeft <= LOBBY_COUNTDOWN_MINUTES) {
        stringResource(R.string.crew_home_run_lobby_opens_in, lobby.minutesLeft.toInt())
    } else {
        stringResource(R.string.crew_home_run_lobby_opens_before)
    }
}

/** 이만큼(분) 남았을 때부터 "N분 뒤 열려요"로 센다. */
private const val LOBBY_COUNTDOWN_MINUTES = 60L

@Composable
private fun VoteButton(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 갈래요·못 가요는 하나만 고르는 응답이라 선택 상태를 표준 의미로 알린다.
    val stateText =
        stringResource(if (isSelected) R.string.crew_home_vote_selected else R.string.crew_home_vote_unselected)
    val state = modifier.semantics {
        this.selected = isSelected
        stateDescription = stateText
    }
    if (isSelected) {
        RunkkPrimaryButton(label, onClick = onClick, modifier = state)
    } else {
        RunkkSecondaryButton(label, onClick = onClick, modifier = state)
    }
}

/** 응원보드(FR-129~FR-131): 200자 응원글 쓰기, 최신순, 내 글 삭제. */
@Composable
private fun ColumnScope.CheerTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    RunkkTextField(
        value = state.cheerDraft,
        onValueChange = actions.onCheerDraft,
        label = stringResource(R.string.crew_home_cheer_label, state.cheerDraft.length, CrewHomeUiState.CHEER_MAX),
        placeholder = stringResource(R.string.crew_home_cheer_placeholder),
        isSingleLine = false,
        maxLength = CrewHomeUiState.CHEER_MAX
    )
    RunkkPrimaryButton(
        stringResource(R.string.crew_home_cheer_submit),
        onClick = actions.onCheerSubmit,
        isEnabled = state.isCheerValid,
        isLoading = state.isBusy,
        modifier = Modifier.fillMaxWidth()
    )
    val posts = state.cheers?.posts
    when {
        posts == null -> Loading(state)

        posts.isEmpty() -> RunkkEmptyState(stringResource(R.string.crew_home_cheer_empty))

        else -> posts.forEach { post ->
            RunkkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.authorName, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
                    Text(
                        "  ${postedLabel(post.writtenAt)}",
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (post.isMine) {
                        RunkkTextLink(
                            stringResource(R.string.crew_action_delete),
                            onClick = { actions.onDeleteAsk(post) },
                            contentDescription = stringResource(R.string.crew_home_cheer_delete_description)
                        )
                    }
                }
                Text(post.summary, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary)
            }
        }
    }
}

/** 크루원(FR-135): 표시명·역할·티어·Runimal·크루 내 소개. 비공개 크루원은 표시명·역할·소개만 온다. */
@Composable
private fun ColumnScope.MembersTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val members = state.members?.members
    when {
        members == null -> Loading(state)

        else -> {
            members.forEach { member -> MemberRow(member) }
            RunkkSecondaryButton(
                stringResource(R.string.crew_home_intro_change),
                onClick = actions.onIntroEdit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun MemberRow(member: CrewMembers.Member, trailing: (@Composable () -> Unit)? = null) {
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(member.displayName, size = 40)
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.displayName, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
                    RunkkBadge(
                        stringResource(member.role.labelRes()),
                        tone = if (isLeader(member)) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
                    )
                    if (member.isMe) RunkkBadge(stringResource(R.string.crew_member_me), tone = RunkkBadgeTone.Neutral)
                }
                MemberTierAndRunimal(member)
                member.crewIntro?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary) }
            }
            trailing?.invoke()
        }
    }
}

/** 등급 · 러니멀 유형(한국어 이름과 작은 그림). 둘 다 비공개면 "프로필 비공개". */
@Composable
private fun MemberTierAndRunimal(member: CrewMembers.Member) {
    val tier = member.tier?.takeIf { it.isNotBlank() }
    val type = member.runimalType?.takeIf { it.isNotBlank() }
    if (tier == null && type == null) {
        Text(
            stringResource(R.string.crew_member_profile_private),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (tier != null) Text(tier, style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        if (tier != null && type != null) {
            Text("·", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
        if (type != null) RunimalTypeLabel(type)
    }
}

internal fun isLeader(member: CrewMembers.Member): Boolean = member.role == CrewRole.LEADER

// ---------- 크루 관리 ----------

@Composable
fun CrewManageRoute(key: Routes.CrewManage, viewModel: CrewManageViewModel = hiltViewModel()) {
    LaunchedEffect(key) { viewModel.bind(key) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewManageEvent.Deleted -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    CrewManageScreen(
        state = state,
        actions = CrewManageActions(
            onBack = navigator::back,
            onApprove = viewModel::onApprove,
            onReject = viewModel::onReject,
            onReissue = viewModel::onReissue,
            onForm = viewModel::onForm,
            onSaveInfo = viewModel::onSaveInfo,
            onAsk = viewModel::onAsk,
            onConfirm = viewModel::onConfirm,
            onDismiss = viewModel::onDismiss,
            onRetry = viewModel::load,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

data class CrewManageActions(
    val onBack: () -> Unit = {},
    val onApprove: (PendingRequests.Request) -> Unit = {},
    val onReject: (PendingRequests.Request) -> Unit = {},
    val onReissue: () -> Unit = {},
    val onForm: (CrewFormState) -> Unit = {},
    val onSaveInfo: () -> Unit = {},
    val onAsk: (ManageAction) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

@Composable
fun CrewManageScreen(state: CrewManageUiState, actions: CrewManageActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = stringResource(R.string.crew_manage_title), onBack = actions.onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.isLoading && state.members == null) {
                    Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
                }
                state.error?.let { RunkkErrorState(it.asString(), onRetry = actions.onRetry) }
                ManageRequests(state.requests?.requests.orEmpty(), actions)
                ManageInviteCode(state.inviteCode, actions.onReissue)
                ManageMembers(state.members?.members.orEmpty(), actions)
                ManageInfo(state, actions)
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    state.confirming?.let { action -> ManageConfirmDialog(action, state.crewName, actions) }
}

/** 가입 신청 목록과 승인·거절 단추. */
@Composable
private fun ManageRequests(requests: List<PendingRequests.Request>, actions: CrewManageActions) {
    Section(stringResource(R.string.crew_manage_section_requests))
    if (requests.isEmpty()) {
        Text(
            stringResource(R.string.crew_manage_requests_empty),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
    }
    requests.forEach { request ->
        val rejectDescription = stringResource(R.string.crew_manage_reject_description, request.displayName)
        val approveDescription = stringResource(R.string.crew_manage_approve_description, request.displayName)
        RunkkCard {
            Text(request.displayName, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
            Text(
                stringResource(R.string.crew_manage_requested_at, postedLabel(request.requestedAt)),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RunkkSecondaryButton(
                    stringResource(R.string.crew_manage_reject),
                    onClick = { actions.onReject(request) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = rejectDescription },
                    height = 48.dp
                )
                RunkkPrimaryButton(
                    stringResource(R.string.crew_manage_approve),
                    onClick = { actions.onApprove(request) },
                    modifier = Modifier.weight(1f).semantics { contentDescription = approveDescription }
                )
            }
        }
    }
}

/** 초대 코드와 새로 만들기 단추. */
@Composable
private fun ManageInviteCode(code: String?, onReissue: () -> Unit) {
    Section(stringResource(R.string.crew_manage_section_invite))
    val codeDescription =
        stringResource(R.string.crew_manage_invite_code_description, code.orEmpty().toList().joinToString(" "))
    RunkkCard {
        Text(
            code ?: stringResource(R.string.crew_manage_invite_empty),
            style = if (code != null) RunkkTextStyles.display(36) else RunkkTextStyles.Caption,
            color = if (code != null) RunkkColors.Lime else RunkkColors.TextSecondary,
            // 코드는 한 글자씩 읽어 전하기 쉽게, 새로 만들면 알린다.
            modifier = if (code != null) {
                Modifier.semantics {
                    contentDescription = codeDescription
                    liveRegion = LiveRegionMode.Polite
                }
            } else {
                Modifier
            }
        )
        RunkkPillButton(stringResource(R.string.crew_manage_invite_reissue), onClick = onReissue)
    }
}

/** 크루장을 뺀 크루원 목록과 크루장 넘기기·내보내기. */
@Composable
private fun ManageMembers(members: List<CrewMembers.Member>, actions: CrewManageActions) {
    Section(stringResource(R.string.crew_manage_section_members))
    members.filterNot(::isLeader).forEach { member ->
        val transferDescription = stringResource(R.string.crew_manage_transfer_description, member.displayName)
        val kickDescription = stringResource(R.string.crew_manage_kick_description, member.displayName)
        MemberRow(member) {
            Column(horizontalAlignment = Alignment.End) {
                RunkkTextLink(
                    stringResource(R.string.crew_manage_transfer),
                    onClick = { actions.onAsk(ManageAction.Transfer(member)) },
                    contentDescription = transferDescription
                )
                RunkkTextLink(
                    stringResource(R.string.crew_manage_kick),
                    onClick = { actions.onAsk(ManageAction.Kick(member)) },
                    contentDescription = kickDescription
                )
            }
        }
    }
}

/** 크루 정보 수정 칸·저장과 크루 삭제. */
@Composable
private fun ManageInfo(state: CrewManageUiState, actions: CrewManageActions) {
    Section(stringResource(R.string.crew_manage_section_info))
    CrewFormFields(state.form, actions.onForm)
    RunkkSecondaryButton(
        stringResource(R.string.crew_manage_info_save),
        onClick = actions.onSaveInfo,
        isEnabled = state.form.isValid && !state.isBusy,
        modifier = Modifier.fillMaxWidth()
    )
    HorizontalDivider(color = RunkkColors.Border)
    Text(
        stringResource(R.string.crew_manage_delete_notice),
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary
    )
    RunkkAccentOutlinedButton(
        stringResource(R.string.crew_manage_delete),
        onClick = { actions.onAsk(ManageAction.Delete) },
        modifier = Modifier.fillMaxWidth()
    )
}

/** 내보내기·크루장 넘기기·크루 삭제 확인 대화상자. */
@Composable
private fun ManageConfirmDialog(action: ManageAction, crewName: String, actions: CrewManageActions) {
    val (title, message, label) = when (action) {
        is ManageAction.Kick -> Triple(
            stringResource(R.string.crew_manage_kick_title, action.member.displayName),
            stringResource(R.string.crew_manage_kick_message),
            stringResource(R.string.crew_manage_kick)
        )

        is ManageAction.Transfer -> Triple(
            stringResource(R.string.crew_manage_transfer_title, action.member.displayName),
            stringResource(R.string.crew_manage_transfer_message),
            stringResource(R.string.crew_manage_transfer_confirm)
        )

        ManageAction.Delete -> Triple(
            stringResource(R.string.crew_manage_delete_title, crewName),
            stringResource(R.string.crew_manage_delete_message),
            stringResource(R.string.crew_action_delete)
        )
    }
    RunkkConfirmDialog(
        title,
        message,
        label,
        onConfirm = actions.onConfirm,
        onDismiss = actions.onDismiss,
        isDestructive = true
    )
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = RunkkTextStyles.CardTitle,
        color = RunkkColors.TextPrimary,
        modifier = Modifier.semantics {
            heading()
        }
    )
}

@Preview(name = "내 크루", widthDp = 390, heightDp = 844)
@Composable
private fun CrewHomePreview() {
    RunkkTheme(reduceMotion = true) {
        CrewHomeScreen(
            CrewHomeUiState(
                crewId = 11,
                detail = CrewDetail(
                    crewId = 11,
                    name = "새벽질주",
                    intro = null,
                    tags = emptySet(),
                    visibility = CrewVisibility.PUBLIC,
                    joinPolicy = JoinPolicy.FREE,
                    logoUrl = null,
                    leaderId = 1,
                    leaderName = "바람처럼",
                    headcount = 6,
                    weeklyDistance = Distance(42_300),
                    myRole = CrewRole.MEMBER,
                    inviteCode = null,
                    inviteCodeExpiresAt = null
                )
            ),
            CrewHomeActions()
        )
    }
}
