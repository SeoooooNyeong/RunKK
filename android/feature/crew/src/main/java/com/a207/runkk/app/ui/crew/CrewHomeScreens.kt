package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTabRow
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PostList

// ---------- 내 크루 홈 ----------

/**
 * 내 크루 홈(시안 HiMyCrew + v0.11, 와이어 E2). [isAtTabRoot]면 크루 탭 자리에 그려 뒤로 단추를 두지 않는다. [initialTab]은 알림으로
 * 들어올 때 먼저 보일 탭(게시판·일정, D-150), [initialFilter]는 그때 게시판에서 고를 칩(공지·투표)이다. 게시판 탭과 신고 시트는
 * 각자 ViewModel이 맡고, 그 안내 문구는 이 화면의 안내 줄로 모은다.
 */
@Composable
fun CrewHomeRoute(
    key: Routes.CrewHome,
    isAtTabRoot: Boolean = false,
    initialTab: CrewTab = CrewTab.RACE,
    initialFilter: BoardFilter = BoardFilter.ALL,
    viewModel: CrewHomeViewModel = hiltViewModel(),
    board: CrewBoardViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key, initialTab) }
    // 관리·일정·글쓰기 화면에서 돌아오면 지금 탭을 다시 읽는다.
    // 게시판 탭은 게시판 ViewModel이 다시 읽는다(투표 만들기·글쓰기·공지 쓰기에서 돌아온 뒤 새 글이 보이게).
    var hasResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        if (hasResumed && viewModel.state.value.tab == CrewTab.BOARD) board.refresh()
        hasResumed = true
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val boardState by board.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.tab) {
        if (state.tab == CrewTab.BOARD) board.bind(key.crewId, initialFilter)
    }
    ForwardMessages(viewModel, boardState.message to board::onMessageShown)
    ForwardMessages(viewModel, reportState.message to report::onMessageShown)
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
        hasMoreBoard = boardState.hasMore,
        lazyTab = {
            boardTabItems(boardState, crewBoardActions(board, report::onAsk)) {
                navigator.navigate(Routes.CrewBoardWrite(state.crewId))
            }
        },
        actions = crewHomeActions(viewModel, report::onAsk) {
            if (state.tab == CrewTab.BOARD) board.onLoadMore() else viewModel.onLoadMore()
        }.withNavigation(state.crewId, navigator::navigate).copy(
            onBack = navigator::back,
            onNotices = {
                // 고정 공지 줄: 게시판 탭의 공지 칩으로(와이어 E2에서 E3).
                board.bind(key.crewId)
                board.onFilter(BoardFilter.NOTICE)
                viewModel.onTab(CrewTab.BOARD)
            }
        )
    )
    OtherTabDialogs(
        deletes = listOf(
            TabDeleteConfirm(boardState.confirmDelete != null, board::onDeleteConfirm, board::onDeleteDismiss)
        ),
        viewerPhotoId = boardState.viewer,
        boardActions = crewBoardActions(board) {}
    )
    CrewReportDialog(
        reportState,
        CrewReportActions(report::onReason, report::onDetail, report::onSubmit, report::onDismiss)
    )
}

/**
 * 크루 홈에서 다른 화면으로 가는 입력을 경로로 잇는다(시험은 [navigate]에 넣어 어느 경로를 부르는지 본다): 일정 만들기, 크루런
 * 대기실·결과, 대면 모임 상세(M7), 크루 관리, 채팅방(M4), 주간 공동 목표(M8), 공지 쓰기(M1), 다른 러너 프로필(F8).
 */
internal fun CrewHomeActions.withNavigation(crewId: Long, navigate: (NavKey) -> Unit): CrewHomeActions = copy(
    onWriteSchedule = { navigate(Routes.CrewRunWrite(crewId)) },
    onLobby = { post -> navigate(Routes.CrewRunLobby(post.postId)) },
    onResult = { post -> navigate(Routes.CrewRunResult(post.postId)) },
    onMeetup = { post -> navigate(Routes.CrewMeetupDetail(crewId, post.postId)) },
    onManage = { navigate(Routes.CrewManage(crewId)) },
    onChat = { navigate(Routes.CrewChat(crewId)) },
    onGoalEdit = { navigate(Routes.CrewGoalSet(crewId)) },
    onNoticeWrite = { navigate(Routes.CrewNoticeWrite(crewId)) },
    onMember = { member -> navigate(Routes.MemberProfile(member.memberId)) }
)

/** 다른 ViewModel의 안내 문구를 크루 홈 안내 줄로 넘기고 그 ViewModel에서는 지운다. */
@Composable
private fun ForwardMessages(home: CrewHomeViewModel, message: Pair<UiText?, () -> Unit>) {
    val (text, shown) = message
    LaunchedEffect(text) {
        text?.let {
            home.onMessage(it)
            shown()
        }
    }
}

/** ViewModel 동작을 화면 입력 묶음으로 잇는다(이동은 Route가 덧붙인다). */
private fun crewHomeActions(vm: CrewHomeViewModel, onReport: (PostList.Item) -> Unit, onLoadMore: () -> Unit) =
    CrewHomeActions(
        onTab = vm::onTab,
        onPeriod = vm::onPeriod,
        onRetry = vm::refresh,
        onDeleteAsk = vm::onDeleteAsk,
        onDeleteDismiss = vm::onDeleteDismiss,
        onDeleteConfirm = vm::onDeleteConfirm,
        onReport = onReport,
        onScheduleKind = vm::onScheduleKind,
        onAttendance = vm::onAttendance,
        onShareCelebration = vm::onShareCelebration,
        onLeaveAsk = vm::onLeaveAsk,
        onLeaveDismiss = vm::onLeaveDismiss,
        onLeaveConfirm = vm::onLeaveConfirm,
        onLeaveBlockedDismiss = vm::onLeaveBlockedDismiss,
        onIntroEdit = vm::onIntroEdit,
        onIntroDraft = vm::onIntroDraft,
        onIntroDismiss = vm::onIntroDismiss,
        onIntroSave = vm::onIntroSave,
        onMessageShown = vm::onMessageShown,
        onLoadMore = onLoadMore
    )

/**
 * 상태 없는 크루 홈 화면: 머리글(크루명, 인원과 태그, 관리 메뉴), 탭 줄, 레이스 탭 위 고정 공지(최대 3), 탭 내용. 레이스·일정·크루원은
 * [state]로, 게시판(칩별 글, 앨범)은 [lazyTab](지연 목록 항목)으로 그린다. [hasMoreBoard]는 게시판에 다음 쪽이 있는가다.
 * 탭 내용 전체를 지연 목록(LazyColumn) 하나에 담아 스크롤 안에 스크롤을 두지 않는다(A4).
 */
@Composable
fun CrewHomeScreen(
    state: CrewHomeUiState,
    actions: CrewHomeActions,
    modifier: Modifier = Modifier,
    isAtTabRoot: Boolean = false,
    hasMoreBoard: Boolean = false,
    lazyTab: LazyListScope.() -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val list = rememberLazyListState()
    // 게시글 탭에서 끝 근처까지 내리면 다음 쪽을 이어 받는다(다음 쪽이 없으면 ViewModel이 무시한다).
    val nearEnd by remember { derivedStateOf { list.remainingPx() <= LOAD_MORE_PX } }
    val hasMore = when (state.tab) {
        CrewTab.SCHEDULE -> (state.crewRuns?.nextCursor ?: state.meetups?.nextCursor) != null
        CrewTab.BOARD -> hasMoreBoard
        else -> false
    }
    // 탭을 바꾸면 맨 위부터 보인다(앞 탭의 항목 위치를 새 탭에 쓰지 않는다).
    LaunchedEffect(state.tab) { list.scrollToItem(0) }
    LaunchedEffect(nearEnd, hasMore, state.tab) {
        if (nearEnd && hasMore) actions.onLoadMore()
    }
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Header(state, actions, isAtTabRoot)
            CrewTabStrip(state.tab, onTab = actions.onTab, onChat = actions.onChat)
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 고정 공지는 레이스 탭 위에 둔다(와이어 E2). 공지를 쓸 수 있는 크루장, 운영진에게는 "+ 공지"가 있다.
                if (state.tab == CrewTab.RACE && (state.pinnedNotices.isNotEmpty() || state.canManage)) {
                    item(key = "pinned") {
                        PinnedNotices(
                            state.pinnedNotices,
                            canWrite = state.canManage,
                            onWrite = actions.onNoticeWrite,
                            onOpen = actions.onNotices
                        )
                    }
                }
                homeTabItems(state, actions, lazyTab)
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    HomeDialogs(state, actions)
}

/**
 * 고른 탭의 내용(읽기 실패면 오류 상태). 일정은 이 화면이, 게시판은 [lazyTab]이 글마다 항목을 더한다(보이는 것만 그린다, AN5-D-6).
 * 레이스·크루원은 글 목록이 아니라 한 항목 안의 Column으로 그린다.
 */
private fun LazyListScope.homeTabItems(
    state: CrewHomeUiState,
    actions: CrewHomeActions,
    lazyTab: LazyListScope.() -> Unit
) {
    if (state.error != null && !state.isLoading && state.tab != CrewTab.BOARD) {
        item(key = "error") { RunkkErrorState(state.error.asString(), onRetry = actions.onRetry) }
        return
    }
    when (state.tab) {
        CrewTab.BOARD -> {
            lazyTab()
            return
        }

        CrewTab.SCHEDULE -> scheduleTabItems(state, actions)

        CrewTab.RACE, CrewTab.MEMBERS -> item(key = "tab:${state.tab}") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state.tab == CrewTab.RACE) RaceTab(state, actions) else MembersTab(state, actions)
            }
        }
    }
    if (state.isLoadingMore) {
        item(key = "loading-more") {
            Box(Modifier.fillMaxWidth().height(64.dp)) { RunkkLoading() }
        }
    }
}

/**
 * 목록 끝까지 남은 픽셀(대략). 마지막으로 보이는 항목의 아래 끝에서 화면 끝을 빼고, 아직 그리지 않은 항목은 보이는 항목 평균 높이로 센다.
 * 탭 내용이 한 항목일 때도(레이스·일정 등) 그 항목 안에서 끝까지 남은 거리가 된다.
 */
private fun LazyListState.remainingPx(): Int {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return Int.MAX_VALUE
    val average = info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size
    val unseen = info.totalItemsCount - 1 - last.index
    return last.offset + last.size - info.viewportEndOffset + unseen * average
}

/** 같은 너비 탭 다섯 칸(와이어 E2, 하이파이 HiMyCrew): 레이스, 일정, 게시판, 채팅, 크루원. 채팅 칸은 채팅방(M4)을 연다. */
@Composable
private fun CrewTabStrip(selected: CrewTab, onTab: (CrewTab) -> Unit, onChat: () -> Unit) {
    val items = CrewStripItem.entries
    val current = CrewStripItem.of(selected)
    RunkkTabRow(
        options = items.map { stringResource(it.labelRes) },
        selectedIndex = items.indexOf(current),
        onSelect = { index ->
            val tab = items[index].tab
            when {
                tab == null -> onChat()
                items[index] == current -> Unit
                else -> onTab(tab)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}

/**
 * 고정 공지(최대 3, FR-205, 와이어 E2): 제목, 개수, "+ 공지"(크루장, 운영진, 공지 쓰기 M1로), 줄마다 공지 배지. 줄을 누르면 게시판 탭의
 * 공지 칩으로.
 */
@Composable
private fun PinnedNotices(pinned: List<PostList.Item>, canWrite: Boolean, onWrite: () -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.crew_home_pinned_title),
                style = RunkkTextStyles.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                stringResource(R.string.crew_home_pinned_count, pinned.size, CrewHomeUiState.PINNED_MAX),
                style = RunkkTextStyles.Caption,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (canWrite) RunkkTextLink(stringResource(R.string.crew_home_pinned_write), onClick = onWrite)
        }
        pinned.forEach { notice ->
            val description = stringResource(R.string.crew_home_pinned_description, notice.summary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RunkkShapes.Medium)
                    .background(colors.surface)
                    .border(1.dp, colors.outlineVariant, RunkkShapes.Medium)
                    .clickable(role = Role.Button, onClick = onOpen)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RunkkBadge(stringResource(R.string.crew_home_pinned_badge), tone = RunkkBadgeTone.Lime)
                Text(
                    notice.summary,
                    style = RunkkTextStyles.Body,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    RunkkIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 크루 홈의 확인 대화상자: 글 삭제, 크루 탈퇴, 크루장 탈퇴 불가(E7), 크루 내 정보 편집(E6). */
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
    if (state.isLeaveBlocked) {
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_home_leave_blocked_title),
            message = stringResource(R.string.crew_home_leave_blocked_message),
            confirmLabel = stringResource(R.string.crew_manage_transfer),
            onConfirm = {
                actions.onLeaveBlockedDismiss()
                actions.onManage()
            },
            onDismiss = actions.onLeaveBlockedDismiss,
            dismissLabel = stringResource(R.string.crew_home_leave_blocked_close)
        )
    }
    if (state.isEditingIntro) IntroSheet(state, actions)
}

/** 크루에서 보이는 내 정보 편집 시트(와이어 E6): 한줄 소개(40자)와 저장. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntroSheet(state: CrewHomeUiState, actions: CrewHomeActions) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = actions.onIntroDismiss, containerColor = colors.surface) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.crew_home_intro_title),
                style = RunkkTextStyles.CardTitle,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            RunkkTextField(
                value = state.introDraft,
                onValueChange = actions.onIntroDraft,
                label = stringResource(
                    R.string.crew_home_intro_label,
                    state.introDraft.length,
                    CrewHomeUiState.INTRO_MAX
                ),
                imeAction = ImeAction.Done,
                onImeAction = actions.onIntroSave
            )
            RunkkPrimaryButton(
                stringResource(R.string.crew_home_intro_save),
                onClick = actions.onIntroSave,
                isEnabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 끝에서 이 픽셀 안쪽까지 내리면 다음 쪽을 읽는다. */
private const val LOAD_MORE_PX = 600

/** 머리글(와이어 E2): 뒤로(탭 자리가 아닐 때), 로고, 크루명과 "인원, 태그", 크루 관리로 가는 ⋯(크루장, 운영진). */
@Composable
private fun Header(state: CrewHomeUiState, actions: CrewHomeActions, isAtTabRoot: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (isAtTabRoot) 16.dp else 8.dp,
            end = 8.dp,
            top = 8.dp,
            bottom = 12.dp
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        if (state.canManage) {
            CrewMoreButton(contentDescription = stringResource(R.string.crew_home_menu), onClick = actions.onManage)
        }
    }
}

/** 머리글의 크루 이름과 "6/10명, 함께가 좋아요, 저녁". */
@Composable
private fun HomeTitle(state: CrewHomeUiState, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            state.crewName,
            style = RunkkTextStyles.TopBarTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        state.headcount?.let { headcount ->
            val tags = state.detail?.tags.orEmpty().map { RunkkTags.label(resources, it) }
            Text(
                (listOf(stringResource(R.string.crew_meta_headcount, headcount, CREW_MAX)) + tags).joinToString(", "),
                style = RunkkTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@StringRes
internal fun CrewRole.labelRes(): Int = when (this) {
    CrewRole.LEADER -> R.string.crew_role_leader
    CrewRole.MANAGER -> R.string.crew_role_manager
    CrewRole.MEMBER -> R.string.crew_role_member
}

/** 탭을 처음 읽는 동안의 자리. */
@Composable
internal fun ColumnScope.TabLoading(isLoading: Boolean) {
    if (isLoading) Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
}
