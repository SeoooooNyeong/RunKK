package com.a207.runkk.app.ui.crew

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSegmented
import com.a207.runkk.app.core.designsystem.RunkkSegmentedSelection
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.LocalSlotCrewId
import com.a207.runkk.app.core.navigation.LocalSlots
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.core.navigation.SlotKey
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PostList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onLeft: () -> Unit = {},
    viewModel: CrewHomeViewModel = hiltViewModel(),
    report: CrewReportViewModel = hiltViewModel()
) {
    LaunchedEffect(key) { viewModel.bind(key, initialTab) }
    // 관리·일정·목표 화면에서 돌아오면 크루 정보와 지금 탭을 다시 읽는다. 멈추면 크루런 1초 갱신을 멈춘다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose { viewModel.onPaused() }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportState by report.state.collectAsStateWithLifecycle()
    ForwardMessages(viewModel, reportState.message to report::onMessageShown)
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // 크루 탭 자리에 그렸으면 그 탭의 내 크루를 비워 크루 찾기로 바꾼다(같은 탭 항목이 남아 있어도, DV-7).
                CrewHomeEvent.Left -> {
                    onLeft()
                    navigator.selectTab(Routes.CREW)
                }
            }
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 정보 수정 로고(F-26, DV-6): 크루 만들기와 같이 사진 고르기 → JPEG로 줄여(EXIF 방향 반영) 올린다.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { runCatching { CrewLogoEncoder.fromUri(context, uri) }.getOrNull() }
            if (jpeg == null) viewModel.onEditLogoUnreadable() else viewModel.onEditLogoPicked(jpeg)
        }
    }
    val loadLogo = remember(viewModel) { viewModel::logo }
    CompositionLocalProvider(LocalCrewLogoLoader provides loadLogo) {
        CrewHomeScreen(
            state = state,
            isAtTabRoot = isAtTabRoot,
            actions = crewHomeActions(viewModel, report::onAsk).withNavigation(state.crewId, navigator::navigate)
                .copy(
                    onBack = navigator::back,
                    onEditLogoPick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                ),
            // 채팅 탭(피그마 F-09, D-653): ui.chat이 칸 SlotKey.CREW_CHAT_TAB에 넣은 ChatTabContent를 그린다.
            chatContent = { chatModifier -> CrewChatSlot(state.crewId, chatModifier) }
        )
    }
    CrewReportDialog(
        reportState,
        CrewReportActions(report::onReason, report::onDetail, report::onSubmit, report::onDismiss)
    )
}

/**
 * 크루 홈에서 다른 화면으로 가는 입력을 경로로 잇는다(시험은 [navigate]에 넣어 어느 경로를 부르는지 본다): 크루런 모집, 크루런
 * 대기실·결과, 채팅방(M4), 주간 공동 목표(M8), 지난 크루런(F-20)·지난 목표(F-21). 크루 관리 화면은 지웠다(W7).
 */
internal fun CrewHomeActions.withNavigation(crewId: Long, navigate: (NavKey) -> Unit): CrewHomeActions = copy(
    onWriteSchedule = { navigate(Routes.CrewRunWrite(crewId)) },
    onRunOpen = { post -> navigate(Routes.CrewRunDetail(post.postId)) },
    onPastRuns = { navigate(Routes.CrewPastRuns(crewId)) },
    onLobby = { post -> navigate(Routes.CrewRunLobby(post.postId)) },
    onResult = { post -> navigate(Routes.CrewRunResult(post.postId)) },
    onGoalEdit = { navigate(Routes.CrewGoalSet(crewId)) },
    onPastGoals = { navigate(Routes.CrewPastGoals(crewId)) }
)

/** 채팅 탭 칸(SlotKey.CREW_CHAT_TAB): ui.chat이 넣은 내용을 이 크루 ID로 그린다. 넣은 기능이 없으면(미리보기) 빈 칸이다. */
@Composable
private fun CrewChatSlot(crewId: Long, modifier: Modifier) {
    Box(modifier) {
        CompositionLocalProvider(LocalSlotCrewId provides crewId) {
            LocalSlots.current[SlotKey.CREW_CHAT_TAB].orEmpty().forEach { it() }
        }
    }
}

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
    onCelebrationOpen = vm::onCelebrationOpen,
    onCelebrationClose = vm::onCelebrationClose,
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
    onLoadMore = vm::onLoadMore,
    onMenuOpen = vm::onMenuOpen,
    onMenuClose = vm::onMenuClose,
    onApprove = vm::onApprove,
    onReject = vm::onReject,
    onMemberOpen = vm::onMemberOpen,
    onMemberClose = vm::onMemberClose,
    onTransferAsk = vm::onTransferAsk,
    onTransferDismiss = vm::onTransferDismiss,
    onTransferConfirm = vm::onTransferConfirm,
    onKickAsk = vm::onKickAsk,
    onKickDismiss = vm::onKickDismiss,
    onKickConfirm = vm::onKickConfirm,
    onCrewDeleteAsk = vm::onCrewDeleteAsk,
    onCrewDeleteDismiss = vm::onCrewDeleteDismiss,
    onCrewDeleteConfirm = vm::onCrewDeleteConfirm,
    onEditForm = vm::onEditForm,
    onEditSave = vm::onEditSave,
    onAgePublic = vm::onAgePublic,
    onGenderPublic = vm::onGenderPublic
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
    isAtTabRoot: Boolean = false,
    /** 채팅 탭 내용(ui.chat ChatTabContent). 시험·미리보기는 비워 둔다. */
    chatContent: @Composable (Modifier) -> Unit = {}
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        when (state.page) {
            CrewHomePage.INFO -> CrewInfoPage(state, actions)
            CrewHomePage.MEMBERS -> CrewMembersPage(state, actions)
            CrewHomePage.EDIT -> CrewEditPage(state, actions)
            CrewHomePage.HOME -> HomeTabs(state, actions, isAtTabRoot, chatContent)
        }
        if (state.isMenuOpen && state.page == CrewHomePage.HOME) {
            CrewHomeMenu(
                onInfo = { actions.onPage(CrewHomePage.INFO) },
                onMembers = { actions.onPage(CrewHomePage.MEMBERS) },
                onClose = actions.onMenuClose
            )
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    HomeDialogs(state, actions)
}

/** 홈 탭 화면: 머리글, 탭 줄, 지금 탭의 내용. 크루런 탭은 끝 근처까지 내리면 다음 쪽을 이어 받는다. */
@Composable
private fun HomeTabs(
    state: CrewHomeUiState,
    actions: CrewHomeActions,
    isAtTabRoot: Boolean,
    chatContent: @Composable (Modifier) -> Unit
) {
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
        CrewTabStrip(state.tab, onTab = actions.onTab)
        // 채팅 탭은 자기 목록과 입력 줄을 가지므로 바깥 지연 목록에 넣지 않는다(스크롤 안에 스크롤을 두지 않는다).
        if (state.tab == CrewTab.CHAT) {
            chatContent(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp))
            return@Column
        }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { RaceTab(state, actions) }
        }

        // 채팅 탭은 HomeTabs가 지연 목록 밖에 그린다.
        CrewTab.CHAT -> Unit
    }
    if (state.isLoadingMore) {
        item(key = "loading-more") {
            RunkkLoading(height = 64.dp)
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

/** 같은 너비 탭 세 칸(피그마 F-05·F-09): 레이스, 크루런, 채팅. 라임 선택 세그먼트(위 14, 좌우 20). */
@Composable
private fun CrewTabStrip(selected: CrewTab, onTab: (CrewTab) -> Unit) {
    val items = CrewStripItem.entries
    val current = CrewStripItem.of(selected)
    RunkkSegmented(
        options = items.map { stringResource(it.labelRes) },
        selectedIndex = items.indexOf(current),
        onSelect = { index -> if (items[index] != current) onTab(items[index].tab) },
        selection = RunkkSegmentedSelection.Lime,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 2.dp)
    )
}

/** 크루 홈의 대화상자: 글 삭제, 크루 정보·크루원 쪽 대화상자(CrewInfoDialogs), 내 정보 시트(F-23). */
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
    CrewInfoDialogs(state, actions)
    if (state.isEditingIntro) MyInfoSheet(state, actions)
    // 공동 목표 축하 팝업(피그마 F-08b): 그 주 첫 진입 때 한 번, 달성 띠의 「자세히 보기」로 다시.
    val celebrated = state.celebration
    val card = celebrated?.celebration
    if (celebrated != null && card != null) CelebrationDialog(celebrated, card, onClose = actions.onCelebrationClose)
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
        // 올린 로고가 있으면 그 로고, 없으면 groups 기본 그림(DV-8, 결정: 로고는 어디서나 같은 그림).
        CrewImage(state.detail?.logoUrl, size = 48.dp, corner = 16.dp)
        Text(
            state.crewName,
            style = RunkkTextStyles.TopBarTitle,
            color = RunkkColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        CrewMoreDots(stringResource(R.string.crew_home_menu), onClick = actions.onMenuOpen)
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
    if (isLoading) RunkkLoading(height = 160.dp)
}
