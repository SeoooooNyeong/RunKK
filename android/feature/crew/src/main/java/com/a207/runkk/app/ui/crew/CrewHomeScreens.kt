package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTabRow
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PostList
import kotlinx.coroutines.launch

// ---------- 내 크루 홈 ----------

/**
 * 내 크루 홈(피그마 F-05·F-06·F-11, 와이어 E2). [isAtTabRoot]면 크루 탭 자리에 그려 뒤로 단추를 두지 않는다. [initialTab]은 알림으로
 * 들어올 때 먼저 보일 탭(크루런, D-150)이다. 신고 시트는 따로 ViewModel이 맡고, 그 안내 문구는 이 화면의 안내 줄로 모은다.
 */
@Composable
fun CrewHomeRoute(
    key: Routes.CrewHome,
    isAtTabRoot: Boolean = false,
    initialTab: CrewTab = CrewTab.RACE,
    viewModel: CrewHomeViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key, initialTab) }
    // 관리·일정·목표 화면에서 돌아오면 크루 정보와 지금 탭을 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
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
        actions = crewHomeActions(viewModel, report::onAsk).withNavigation(state.crewId, navigator::navigate)
            .copy(onBack = navigator::back)
    )
    CrewReportDialog(
        reportState,
        CrewReportActions(report::onReason, report::onDetail, report::onSubmit, report::onDismiss)
    )
}

/**
 * 크루 홈에서 다른 화면으로 가는 입력을 경로로 잇는다(시험은 [navigate]에 넣어 어느 경로를 부르는지 본다): 크루런 모집, 크루런
 * 대기실·결과, 크루 관리, 채팅방(M4), 주간 공동 목표(M8), 다른 러너 프로필(F8).
 */
internal fun CrewHomeActions.withNavigation(crewId: Long, navigate: (NavKey) -> Unit): CrewHomeActions = copy(
    onWriteSchedule = { navigate(Routes.CrewRunWrite(crewId)) },
    onRunOpen = { post -> navigate(Routes.CrewRunDetail(post.postId)) },
    onPastRuns = { navigate(Routes.CrewPastRuns(crewId)) },
    onLobby = { post -> navigate(Routes.CrewRunLobby(post.postId)) },
    onResult = { post -> navigate(Routes.CrewRunResult(post.postId)) },
    onManage = { navigate(Routes.CrewManage(crewId)) },
    onChat = { navigate(Routes.CrewChat(crewId)) },
    onGoalEdit = { navigate(Routes.CrewGoalSet(crewId)) },
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
private fun crewHomeActions(vm: CrewHomeViewModel, onReport: (PostList.Item) -> Unit) = CrewHomeActions(
    onTab = vm::onTab,
    onPage = vm::onPage,
    onPeriod = vm::onPeriod,
    onRetry = vm::refresh,
    onDeleteAsk = vm::onDeleteAsk,
    onDeleteDismiss = vm::onDeleteDismiss,
    onDeleteConfirm = vm::onDeleteConfirm,
    onReport = onReport,
    onAttendance = vm::onAttendance,
    onAttendanceWithdraw = vm::onAttendanceWithdraw,
    onShareCelebration = vm::onShareCelebration,
    onLeaveAsk = vm::onLeaveAsk,
    onLeaveDismiss = vm::onLeaveDismiss,
    onLeaveConfirm = vm::onLeaveConfirm,
    onLeaveBlockedDismiss = vm::onLeaveBlockedDismiss,
    onIntroEdit = vm::onIntroEdit,
    onIntroDraft = vm::onIntroDraft,
    onIntroDismiss = vm::onIntroDismiss,
    onIntroSave = vm::onIntroSave,
    onInviteOpen = vm::onInviteOpen,
    onInviteClose = vm::onInviteClose,
    onInviteCopied = vm::onInviteCopied,
    onMessageShown = vm::onMessageShown,
    onLoadMore = vm::onLoadMore
)

/**
 * 상태 없는 크루 홈 화면: 머리글(로고, 크루명, ⋯ 메뉴), 탭 줄(레이스, 크루런, 채팅), 탭 내용. ⋯ 메뉴로 연 크루 정보(F-18)·크루원(F-22)
 * 쪽은 이 화면 자리에 그린다(시스템 뒤로 가기로 닫는다). 탭 내용 전체를 지연 목록 하나에 담아 스크롤 안에 스크롤을 두지 않는다(A4).
 */
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
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.page) {
            CrewHomePage.INFO -> CrewInfoPage(state, actions)
            CrewHomePage.MEMBERS -> CrewMembersPage(state, actions)
            CrewHomePage.HOME -> HomeTabs(state, actions, isAtTabRoot)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    HomeDialogs(state, actions)
}

/** 홈 탭 화면: 머리글, 탭 줄, 지금 탭의 내용. 크루런 탭은 끝 근처까지 내리면 다음 쪽을 이어 받는다. */
@Composable
private fun HomeTabs(state: CrewHomeUiState, actions: CrewHomeActions, isAtTabRoot: Boolean) {
    val list = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { list.remainingPx() <= LOAD_MORE_PX } }
    val hasMore = state.tab == CrewTab.SCHEDULE && state.crewRuns?.nextCursor != null
    // 탭을 바꾸면 맨 위부터 보인다(앞 탭의 항목 위치를 새 탭에 쓰지 않는다).
    LaunchedEffect(state.tab) { list.scrollToItem(0) }
    LaunchedEffect(nearEnd, hasMore, state.tab) {
        if (nearEnd && hasMore) actions.onLoadMore()
    }
    Column(Modifier.fillMaxSize()) {
        Header(state, actions, isAtTabRoot)
        CrewTabStrip(state.tab, onTab = actions.onTab, onChat = actions.onChat)
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            homeTabItems(state, actions)
        }
    }
}

/** 고른 탭의 내용(읽기 실패면 오류 상태). 크루런은 글마다 항목을 더하고(보이는 것만 그린다, AN5-D-6), 레이스는 한 항목으로 그린다. */
private fun LazyListScope.homeTabItems(state: CrewHomeUiState, actions: CrewHomeActions) {
    if (state.error != null && !state.isLoading) {
        item(key = "error") { RunkkErrorState(state.error.asString(), onRetry = actions.onRetry) }
        return
    }
    when (state.tab) {
        CrewTab.SCHEDULE -> scheduleTabItems(state, actions)

        CrewTab.RACE -> item(key = "tab:${state.tab}") {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { RaceTab(state, actions) }
        }
    }
    if (state.isLoadingMore) {
        item(key = "loading-more") {
            Box(Modifier.fillMaxWidth().height(64.dp)) { RunkkLoading() }
        }
    }
}

/** 목록 끝까지 남은 픽셀(대략). 아직 그리지 않은 항목은 보이는 항목 평균 높이로 센다. */
private fun LazyListState.remainingPx(): Int {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return Int.MAX_VALUE
    val average = info.visibleItemsInfo.sumOf { it.size } / info.visibleItemsInfo.size
    val unseen = info.totalItemsCount - 1 - last.index
    return last.offset + last.size - info.viewportEndOffset + unseen * average
}

/** 같은 너비 탭 세 칸(피그마 F-05): 레이스, 크루런, 채팅. 채팅 칸은 채팅방(M4)을 연다. */
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

/** 크루 홈의 확인 대화상자: 글 삭제, 크루 탈퇴, 크루장 탈퇴 불가(E7), 크루 내 정보 편집(E6), 초대 코드(F-18b). */
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
    val code = state.detail?.inviteCode
    if (state.isInviteOpen && code != null) InviteCodeDialog(code, actions)
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

/** 머리글(피그마 F-05): 뒤로(탭 자리가 아닐 때), 로고, 크루명, ⋯ 메뉴(F-17·F-24). */
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
        Text(
            state.crewName,
            style = RunkkTextStyles.TopBarTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        MoreMenu(stringResource(R.string.crew_home_menu), homeMenuEntries(state, actions))
    }
}

/** ⋯ 메뉴 줄(피그마 F-17·F-24): 크루 정보, 크루원, 크루장·운영진이면 크루 관리. */
@Composable
private fun homeMenuEntries(state: CrewHomeUiState, actions: CrewHomeActions): List<MenuEntry> = listOfNotNull(
    MenuEntry(stringResource(R.string.crew_home_menu_info)) { actions.onPage(CrewHomePage.INFO) },
    MenuEntry(stringResource(R.string.crew_home_tab_members)) { actions.onPage(CrewHomePage.MEMBERS) },
    if (state.canManage) MenuEntry(stringResource(R.string.crew_home_menu_manage), actions.onManage) else null
)

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
