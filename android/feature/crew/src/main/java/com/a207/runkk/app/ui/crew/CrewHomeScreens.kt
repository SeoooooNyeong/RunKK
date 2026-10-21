package com.a207.runkk.app.ui.crew

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewMembers
import com.a207.runkk.app.domain.crew.CrewRanking
import com.a207.runkk.app.domain.crew.PendingRequests
import com.a207.runkk.app.domain.crew.PostList
import com.a207.runkk.app.domain.crew.RaceStatus
import com.a207.runkk.app.domain.crew.VoteAnswer
import kotlin.math.roundToInt

// ---------- 내 크루 홈 ----------

/** 내 크루 홈(시안 HiMyCrew). */
@Composable
fun CrewHomeRoute(key: Routes.CrewHome, viewModel: CrewHomeViewModel = hiltViewModel()) {
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
            onLobby = { post ->
                navigator.navigate(Routes.CrewRunLobby(post.postId, distanceHint(post.summary)?.meters))
            },
            onResult = { post -> navigator.navigate(Routes.CrewRunResult(post.postId)) },
            onManage = { navigator.navigate(Routes.CrewManage(state.crewId, state.crewName)) },
            onLeaveAsk = viewModel::onLeaveAsk,
            onLeaveDismiss = viewModel::onLeaveDismiss,
            onLeaveConfirm = viewModel::onLeaveConfirm,
            onIntroEdit = viewModel::onIntroEdit,
            onIntroDraft = viewModel::onIntroDraft,
            onIntroDismiss = viewModel::onIntroDismiss,
            onIntroSave = viewModel::onIntroSave,
            onMessageShown = viewModel::onMessageShown
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
    val onMessageShown: () -> Unit = {}
)

@Composable
fun CrewHomeScreen(state: CrewHomeUiState, actions: CrewHomeActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, actions)
            CrewTabRow(
                options = CrewTab.entries.map { it.label },
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onTab(CrewTab.entries[it]) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.error != null && !state.loading) {
                    RunkkErrorState(state.error, onRetry = actions.onRetry)
                } else {
                    when (state.tab) {
                        CrewTab.RACE -> RaceTab(state, actions)
                        CrewTab.CREW_RUN -> CrewRunTab(state, actions)
                        CrewTab.CHEER -> CheerTab(state, actions)
                        CrewTab.MEMBERS -> MembersTab(state, actions)
                    }
                }
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    if (state.confirmDelete != null) {
        RunkkConfirmDialog(
            title = "글을 지울까요?",
            message = "지운 글은 되돌릴 수 없어요.",
            confirmLabel = "삭제",
            onConfirm = actions.onDeleteConfirm,
            onDismiss = actions.onDeleteDismiss,
            destructive = true
        )
    }
    if (state.confirmLeave) {
        RunkkConfirmDialog(
            title = "크루를 나갈까요?",
            message = "나가면 24시간 동안 다른 크루에 가입할 수 없어요. 이번 주 크루 거리에 더한 기록은 그대로 남아요.",
            confirmLabel = "크루 나가기",
            onConfirm = actions.onLeaveConfirm,
            onDismiss = actions.onLeaveDismiss,
            destructive = true
        )
    }
    if (state.editingIntro) {
        AlertDialog(
            onDismissRequest = actions.onIntroDismiss,
            containerColor = RunkkColors.Surface,
            title = { Text("크루 내 한 줄 소개", style = RunkkTextStyles.CardTitle, color = RunkkColors.TextPrimary) },
            text = {
                RunkkTextField(
                    value = state.introDraft,
                    onValueChange = actions.onIntroDraft,
                    label = "40자 · 이 크루 안에서만 보여요",
                    maxLength = CrewHomeUiState.INTRO_MAX
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onIntroSave) {
                    Text("저장", style = RunkkTextStyles.BodyStrong, color = RunkkColors.Lime)
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onIntroDismiss) {
                    Text("취소", style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun Header(state: CrewHomeUiState, actions: CrewHomeActions) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RunkkIconButton(RunkkIcons.Back, contentDescription = "뒤로", onClick = actions.onBack)
        CrewLogo(state.crewName, size = 48, accent = true)
        Column(Modifier.weight(1f)) {
            Text(
                state.crewName,
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            state.members?.let {
                Text(
                    "${it.members.size}/${CREW_MAX}명",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
        }
        Box {
            RunkkIconButton(RunkkIcons.Settings, contentDescription = "크루 메뉴", onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = {
                menu = false
            }, modifier = Modifier.background(RunkkColors.Surface)) {
                listOf(
                    "크루 관리(크루장)" to actions.onManage,
                    "크루 내 소개 바꾸기" to actions.onIntroEdit,
                    "크루 나가기" to actions.onLeaveAsk
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
}

@Composable
private fun ColumnScope.Loading(state: CrewHomeUiState) {
    if (state.loading) Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
}

/** 레이스 탭(FR-140·FR-137): 이번 주 크루 거리·마감·내 기여도, 주간·월간 크루 랭킹. */
@Composable
private fun ColumnScope.RaceTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    val race = state.race
    if (race == null) {
        Loading(state)
        return
    }
    RaceCard(race)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "크루 랭킹",
            style = RunkkTextStyles.CardTitle,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        RunkkSegmentedTabs(
            options = listOf("주간", "월간"),
            selectedIndex = state.period.ordinal,
            onSelect = { actions.onPeriod(CrewRanking.Period.entries[it]) },
            compact = true
        )
    }
    val entries = state.ranking?.entries.orEmpty()
    if (entries.isEmpty()) {
        Text("아직 이번 기간 기록이 없어요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
    }
    // 기여율은 이번 주 크루 거리 대비(시안 31%), 레이스를 못 읽었으면 랭킹 합 대비.
    val total = (if (state.period == CrewRanking.Period.WEEKLY) race.total.meters else null)
        ?: entries.sumOf { it.distance.meters }.coerceAtLeast(1)
    entries.forEach { entry ->
        val share = (entry.distance.meters * PERCENT / total.toDouble()).roundToInt()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RunkkShapes.Medium)
                .background(RunkkColors.Surface)
                .border(1.dp, RunkkColors.Border, RunkkShapes.Medium)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clearAndSetSemantics {
                    contentDescription =
                        "${entry.rank}위 ${entry.displayName} ${RunkkFormat.kmShort(entry.distance)}킬로미터, $share%"
                },
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
                Text("$share%", style = RunkkTextStyles.Micro, color = RunkkColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun RaceCard(race: RaceStatus) {
    val mine = race.myShare
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
                "이번 주 크루 거리",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text("일요일 자정 마감", style = RunkkTextStyles.text(12, FontWeight.Bold), color = RunkkColors.Lavender)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(RunkkFormat.kmShort(race.total), style = RunkkTextStyles.display(64), color = RunkkColors.TextPrimary)
            Text(
                " KM",
                style = RunkkTextStyles.display(24),
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            if (mine != null) {
                val meters = race.contributors.firstOrNull { it.share == mine }?.distance
                "내 기여도 ${shareLabel(mine)}%" + (meters?.let { " · ${RunkkFormat.kmShort(it)}km" } ?: "")
            } else {
                "이번 주에 달리면 크루 거리에 자동으로 더해져요"
            },
            style = RunkkTextStyles.Body,
            color = RunkkColors.TextPrimary
        )
    }
}

/** 기여율(0~1 또는 0~100)을 정수 %로(소수 버림, FR-140). */
internal fun shareLabel(share: Double): Int = (if (share <= 1.0) share * PERCENT else share).toInt()

private const val PERCENT = 100

/** 크루런 탭(FR-133·FR-145): 모집글, 갈래요·못 가요, 대기실·결과. */
@Composable
private fun ColumnScope.CrewRunTab(state: CrewHomeUiState, actions: CrewHomeActions) {
    RunkkPrimaryButton("크루런 모집하기", onClick = actions.onWriteCrewRun, modifier = Modifier.fillMaxWidth())
    Text(
        "같은 시간에 각자 다른 곳에서 함께 출발해요. 위치 대신 달린 거리만 공유돼요.",
        style = RunkkTextStyles.Caption,
        color = RunkkColors.TextSecondary
    )
    val posts = state.crewRuns?.posts
    when {
        posts == null -> Loading(state)
        posts.isEmpty() -> RunkkEmptyState("아직 모집 중인 크루런이 없어요", message = "첫 크루런을 모집해 보세요")
        else -> posts.forEach { post -> CrewRunPost(post, state.votes[post.postId], actions) }
    }
}

@Composable
private fun CrewRunPost(post: PostList.Item, vote: VoteAnswer?, actions: CrewHomeActions) {
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RunkkBadge("크루런", tone = RunkkBadgeTone.Lime)
            Text(
                "  ${post.authorName} · ${postedLabel(post.writtenAt)}",
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            if (post.mine) RunkkTextLink("삭제", onClick = { actions.onDeleteAsk(post) }, contentDescription = "모집글 삭제")
        }
        Text(post.summary, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            VoteButton("갈래요", selected = vote == VoteAnswer.GOING, onClick = {
                actions.onVote(post, VoteAnswer.GOING)
            }, modifier = Modifier.weight(1f))
            VoteButton(
                "못 가요",
                selected = vote == VoteAnswer.NOT_GOING,
                onClick = { actions.onVote(post, VoteAnswer.NOT_GOING) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunkkPillButton(
                "대기실 들어가기",
                onClick = { actions.onLobby(post) },
                modifier = Modifier.semantics { contentDescription = "대기실 들어가기, ${post.summary}" }
            )
            RunkkPillButton(
                "지난 결과",
                onClick = { actions.onResult(post) },
                modifier = Modifier.semantics { contentDescription = "지난 결과, ${post.summary}" }
            )
        }
    }
}

@Composable
private fun VoteButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // 갈래요·못 가요는 하나만 고르는 응답이라 선택 상태를 표준 의미로 알린다.
    val state = modifier.semantics {
        this.selected = selected
        stateDescription = if (selected) "선택됨" else "선택 안 됨"
    }
    if (selected) {
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
        label = "응원 한마디 · ${state.cheerDraft.length}/${CrewHomeUiState.CHEER_MAX}",
        placeholder = "이번 주도 같이 달려요!",
        singleLine = false,
        maxLength = CrewHomeUiState.CHEER_MAX
    )
    RunkkPrimaryButton(
        "응원 남기기",
        onClick = actions.onCheerSubmit,
        enabled = state.cheerValid,
        loading = state.busy,
        modifier = Modifier.fillMaxWidth()
    )
    val posts = state.cheers?.posts
    when {
        posts == null -> Loading(state)

        posts.isEmpty() -> RunkkEmptyState("첫 응원을 남겨 보세요")

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
                    if (post.mine) {
                        RunkkTextLink("삭제", onClick = {
                            actions.onDeleteAsk(post)
                        }, contentDescription = "응원글 삭제")
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
            RunkkSecondaryButton("크루 내 소개 바꾸기", onClick = actions.onIntroEdit, modifier = Modifier.fillMaxWidth())
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
                        roleLabel(member.role),
                        tone = if (isLeader(member)) RunkkBadgeTone.Lime else RunkkBadgeTone.Muted
                    )
                }
                Text(
                    listOfNotNull(member.tier, member.runimalType).joinToString(" · ").ifEmpty { "프로필 비공개" },
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                member.crewIntro?.let { Text(it, style = RunkkTextStyles.Caption, color = RunkkColors.TextPrimary) }
            }
            trailing?.invoke()
        }
    }
}

internal fun isLeader(member: CrewMembers.Member): Boolean = member.role.uppercase() == "LEADER" || member.role == "크루장"

internal fun roleLabel(role: String): String = when (role.uppercase()) {
    "LEADER" -> "크루장"
    "MEMBER" -> "크루원"
    else -> role
}

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
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            RunkkTopBar(title = "크루 관리", onBack = actions.onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.loading && state.members == null) {
                    Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
                }
                state.error?.let { RunkkErrorState(it, onRetry = actions.onRetry) }
                Section("가입 신청")
                val requests = state.requests?.requests.orEmpty()
                if (requests.isEmpty()) {
                    Text("대기 중인 가입 신청이 없어요", style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                }
                requests.forEach { request ->
                    RunkkCard {
                        Text(request.displayName, style = RunkkTextStyles.BodyStrong, color = RunkkColors.TextPrimary)
                        Text(
                            "신청 ${postedLabel(request.requestedAt)}",
                            style = RunkkTextStyles.Caption,
                            color = RunkkColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RunkkSecondaryButton(
                                "거절",
                                onClick = { actions.onReject(request) },
                                modifier = Modifier.weight(1f).semantics {
                                    contentDescription = "${request.displayName} 신청 거절"
                                },
                                height = 48.dp
                            )
                            RunkkPrimaryButton(
                                "승인",
                                onClick = { actions.onApprove(request) },
                                modifier = Modifier.weight(1f).semantics {
                                    contentDescription = "${request.displayName} 신청 승인"
                                }
                            )
                        }
                    }
                }
                Section("초대 코드")
                RunkkCard {
                    val code = state.inviteCode
                    Text(
                        code ?: "새로 만들면 이전 코드는 바로 쓸 수 없어요",
                        style = if (code != null) RunkkTextStyles.display(36) else RunkkTextStyles.Caption,
                        color = if (code != null) RunkkColors.Lime else RunkkColors.TextSecondary,
                        // 코드는 한 글자씩 읽어 전하기 쉽게, 새로 만들면 알린다.
                        modifier = if (code != null) {
                            Modifier.semantics {
                                contentDescription = "초대 코드 " + code.toList().joinToString(" ")
                                liveRegion = LiveRegionMode.Polite
                            }
                        } else {
                            Modifier
                        }
                    )
                    RunkkPillButton("초대 코드 새로 만들기", onClick = actions.onReissue)
                }
                Section("크루원 관리")
                state.members?.members.orEmpty().filterNot(::isLeader).forEach { member ->
                    MemberRow(member) {
                        Column(horizontalAlignment = Alignment.End) {
                            RunkkTextLink(
                                "크루장 넘기기",
                                onClick = { actions.onAsk(ManageAction.Transfer(member)) },
                                contentDescription = "${member.displayName}에게 크루장 넘기기"
                            )
                            RunkkTextLink(
                                "내보내기",
                                onClick = { actions.onAsk(ManageAction.Kick(member)) },
                                contentDescription = "${member.displayName} 내보내기"
                            )
                        }
                    }
                }
                Section("크루 정보 바꾸기")
                CrewFormFields(state.form, actions.onForm)
                RunkkSecondaryButton(
                    "정보 저장",
                    onClick = actions.onSaveInfo,
                    enabled = state.form.valid && !state.busy,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = RunkkColors.Border)
                Text(
                    "크루를 지우면 크루원·게시글·이번 주 거리가 모두 지워지고 크루원에게 알려요.",
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
                RunkkAccentOutlinedButton(
                    "크루 삭제",
                    onClick = { actions.onAsk(ManageAction.Delete) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    state.confirming?.let { action ->
        val (title, message, label) = when (action) {
            is ManageAction.Kick -> Triple(
                "${action.member.displayName}님을 내보낼까요?",
                "내보내면 24시간 동안 다른 크루에 가입할 수 없어요.",
                "내보내기"
            )

            is ManageAction.Transfer -> Triple(
                "${action.member.displayName}님에게 크루장을 넘길까요?",
                "넘기면 나는 크루원이 되고 크루 관리를 할 수 없어요.",
                "넘기기"
            )

            ManageAction.Delete -> Triple("${state.crewName}를 지울까요?", "지운 크루는 되돌릴 수 없어요.", "삭제")
        }
        RunkkConfirmDialog(
            title,
            message,
            label,
            onConfirm = actions.onConfirm,
            onDismiss = actions.onDismiss,
            destructive = true
        )
    }
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
    RunkkTheme(reduceMotion = true) { CrewHomeScreen(CrewHomeUiState(crewName = "새벽질주"), CrewHomeActions()) }
}
