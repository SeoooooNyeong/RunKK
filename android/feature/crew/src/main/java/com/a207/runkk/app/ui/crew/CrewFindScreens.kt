package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkBottomSheet
import com.a207.runkk.app.core.designsystem.RunkkButton
import com.a207.runkk.app.core.designsystem.RunkkButtonSize
import com.a207.runkk.app.core.designsystem.RunkkButtonStyle
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.CrewPreview
import com.a207.runkk.app.domain.crew.MyCrew

/**
 * 크루 탭: 들어올 때마다 내 크루(GET /crews/me)를 확인해 크루가 있으면 내 크루 홈을 이 자리에 보이고(뒤로 단추 없음),
 * 없으면 크루 찾기·상세·초대 코드·대기 중인 내 가입 신청을 보인다. 검색과 만들기는 다른 화면(Routes.CREW_SEARCH·CREW_CREATE)이다.
 */
@Composable
fun CrewFindRoute(initialTab: CrewTab? = null, viewModel: CrewFindViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    // 크루 만들기·검색 등에서 돌아오면 소속을 다시 확인한다(처음 들어올 때는 ViewModel init이 확인한다).
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshMine()
        onPauseOrDispose { }
    }
    val mine = state.mine
    when {
        mine != null -> {
            // 알림으로 들어오면(initialTab) 그 탭을 먼저 열고 뒤로 단추를 둔다(D-150).
            CrewHomeRoute(
                Routes.CrewHome(mine.crewId),
                isAtTabRoot = initialTab == null,
                initialTab = initialTab ?: CrewTab.RACE,
                onLeft = viewModel::onLeftCrew
            )
            return
        }

        state.isCheckingMine -> {
            Box(Modifier.fillMaxSize().background(RunkkColors.Background)) { RunkkLoading() }
            return
        }

        state.mineError != null -> {
            Box(Modifier.fillMaxSize().background(RunkkColors.Background).padding(16.dp)) {
                RunkkErrorState(state.mineError?.asString() ?: "", onRetry = { viewModel.loadMine() })
            }
            return
        }
    }
    val loadLogo = remember(viewModel) { viewModel::logo }
    CompositionLocalProvider(LocalCrewLogoLoader provides loadLogo) {
        CrewFindScreen(
            state = state,
            actions = crewFindActions(
                viewModel,
                onCreate = { if (viewModel.onCreateRequested()) navigator.navigate(Routes.CREW_CREATE) },
                onSearchOpen = { navigator.navigate(Routes.CREW_SEARCH) }
            )
        )
    }
}

/** ViewModel의 동작을 화면 입력 묶음으로 잇는다. */
private fun crewFindActions(viewModel: CrewFindViewModel, onCreate: () -> Unit, onSearchOpen: () -> Unit) =
    CrewFindActions(
        onSearchOpen = onSearchOpen,
        onConditionOpen = viewModel::onConditionOpen,
        onConditionToggle = viewModel::onConditionToggle,
        onConditionApply = viewModel::onConditionApply,
        onConditionDismiss = viewModel::onConditionDismiss,
        onOpen = viewModel::onOpen,
        onCloseDetail = viewModel::onCloseDetail,
        onJoin = viewModel::onJoin,
        onCreate = onCreate,
        onInviteOpen = viewModel::onInviteOpen,
        onInviteClose = viewModel::onInviteClose,
        onInviteCode = viewModel::onInviteCode,
        onInviteConfirm = viewModel::onInviteConfirm,
        onInviteSubmit = viewModel::onInviteSubmit,
        onInvitePreviewOpen = viewModel::onInvitePreviewOpen,
        onRetry = viewModel::load,
        onMessageShown = viewModel::onMessageShown,
        onLoadMore = viewModel::loadMore,
        onCancelAsk = viewModel::onCancelAsk,
        onCancelDismiss = viewModel::onCancelDismiss,
        onCancelConfirm = viewModel::onCancelConfirm
    )

/** 크루 찾기 화면의 입력 묶음. */
data class CrewFindActions(
    /** 검색 바를 누르면 크루 검색 화면(피그마 F-01d)으로 간다. */
    val onSearchOpen: () -> Unit = {},
    /** 조건 설정 팝업(피그마 F-01b) 열기, 칩 누름, 적용, 닫기. */
    val onConditionOpen: () -> Unit = {},
    val onConditionToggle: (String) -> Unit = {},
    val onConditionApply: () -> Unit = {},
    val onConditionDismiss: () -> Unit = {},
    val onOpen: (CrewList.Item) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onJoin: (CrewList.Item) -> Unit = {},
    val onCreate: () -> Unit = {},
    /** 초대 코드 시트(피그마 F-03): 열기·닫기, 코드 입력, "확인"(조회), "가입하기", 미리보기 카드(상세 열기). */
    val onInviteOpen: () -> Unit = {},
    val onInviteClose: () -> Unit = {},
    val onInviteCode: (String) -> Unit = {},
    val onInviteConfirm: () -> Unit = {},
    val onInviteSubmit: () -> Unit = {},
    val onInvitePreviewOpen: (CrewPreview) -> Unit = {},
    /** 목록 다시 읽기(새로고침 단추, 오류 다시 시도). */
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onCancelAsk: (MyCrew.PendingJoin) -> Unit = {},
    val onCancelDismiss: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {}
)

@Composable
fun CrewFindScreen(state: CrewFindUiState, actions: CrewFindActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val listState = rememberLazyListState()
    // 끝에서 세 줄 안쪽이 보이면 다음 쪽을 이어 받는다(nextCursor가 없으면 ViewModel이 무시한다).
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor, state.crews.size) {
        if (nearEnd && state.nextCursor != null) actions.onLoadMore()
    }
    Box(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        val detail = state.detail
        if (detail != null) {
            // 크루 정보(F-02)는 목록 위에 덮는 전체 화면이다(시스템 뒤로 가기로 닫는다).
            CrewDetailPage(
                crew = detail,
                info = state.detailInfo,
                pending = state.pending.firstOrNull { it.crewId == detail.crewId },
                joinRemaining = state.joinRemaining,
                isJoining = state.isJoining,
                onBack = actions.onCloseDetail,
                onJoin = { actions.onJoin(detail) },
                onCancel = actions.onCancelAsk
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 목록 끝은 솟은 탭 원에 가리지 않게 원 높이만큼 더 비운다(D100).
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 11.dp,
                    bottom =
                        16.dp + RUNKK_BOTTOM_BAR_RISE
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                crewFindItems(state, actions)
            }
        }
        RunkkSnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + RUNKK_BOTTOM_BAR_RISE)
        )
    }
    if (state.isConditionOpen) ConditionSheet(state.conditionDraft, actions)
    state.invite?.let { CrewInviteSheet(it, actions) }
    state.confirmCancel?.let { request ->
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_find_cancel_confirm_title),
            message = stringResource(R.string.crew_find_cancel_confirm_message, request.crewName),
            confirmLabel = stringResource(R.string.crew_find_cancel_confirm_action),
            onConfirm = actions.onCancelConfirm,
            onDismiss = actions.onCancelDismiss,
            isDestructive = true
        )
    }
}

/** 크루 찾기 목록의 줄들(피그마 F-01): 머리글, 검색 바, 조건 설정 칸, 대기 중 신청, 목록 제목·새로고침, 공개 크루. */
private fun LazyListScope.crewFindItems(state: CrewFindUiState, actions: CrewFindActions) {
    item { FindHeaderRow(onInviteOpen = actions.onInviteOpen, onCreate = actions.onCreate) }
    item { FindSearchBar(onOpen = actions.onSearchOpen) }
    // 요일과 시간대 조건이 크루 찾기의 주 경로다(FR-117, 피그마 F-01). 추천 영역은 지웠다(FR-118 삭제).
    item { ConditionBox(selected = state.tags, onOpen = actions.onConditionOpen) }
    if (state.pending.isNotEmpty()) {
        items(state.pending, key = { "pending-${it.requestId}" }) { request ->
            PendingJoinCard(request, isBusy = state.isJoining, onCancel = { actions.onCancelAsk(request) })
        }
    }
    item { FindListTitle(hasCondition = state.tags.isNotEmpty(), onRefresh = actions.onRetry) }
    when {
        state.isLoading && state.crews.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().padding(32.dp)) { RunkkLoading() }
        }

        state.error != null && state.crews.isEmpty() -> item {
            RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
        }

        state.crews.isEmpty() -> item {
            RunkkEmptyState(
                stringResource(R.string.crew_find_empty_title),
                actionLabel = stringResource(R.string.crew_create_title),
                onAction = actions.onCreate
            )
        }

        else -> {
            items(state.crews, key = { it.crewId }) { crew -> FindCrewCard(crew, onClick = { actions.onOpen(crew) }) }
            if (state.isLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp)) { RunkkLoading() } }
            }
        }
    }
}

/** 머리글(피그마 F-01): "크루" 18 Bold, 오른쪽 Outline Small "초대 코드" + 8 + Lime Small "크루 만들기"(아이콘 없음). */
@Composable
private fun FindHeaderRow(onInviteOpen: () -> Unit, onCreate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.crew_find_title),
            style = RunkkTextStyles.text(18, FontWeight.Bold, lineHeight = 22),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkButton(
            stringResource(R.string.crew_find_invite_code),
            onClick = onInviteOpen,
            style = RunkkButtonStyle.Outline,
            size = RunkkButtonSize.Small
        )
        RunkkButton(stringResource(R.string.crew_create_title), onClick = onCreate, size = RunkkButtonSize.Small)
    }
}

/** 검색 바(피그마 F-01): 누르면 크루 검색 화면으로 간다. 자리 문구 "크루명 검색". */
@Composable
private fun FindSearchBar(onOpen: () -> Unit) {
    val label = stringResource(R.string.crew_find_search_open)
    CrewSearchBarFrame(
        Modifier
            .clip(RunkkShapes.Pill)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = label }
    ) {
        Text(
            stringResource(R.string.crew_find_search_placeholder),
            style = RunkkTextStyles.text(14),
            color = RunkkColors.TextSecondary
        )
    }
}

/**
 * 조건 설정 칸(피그마 F-01·F-01c): surface + divider, 모서리 24, 안쪽 12, 간격 8. 제목 "조건 설정하기"(14 Bold)와 안내 한 줄(13),
 * 조건을 고르면 안내 대신 Medium 태그 줄. 누르면 조건 설정 팝업(F-01b)을 연다.
 */
@Composable
private fun ConditionBox(selected: Set<String>, onOpen: () -> Unit) {
    val shape = RunkkShapes.ExtraLarge
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .clickable(
                onClickLabel = stringResource(R.string.crew_find_condition_open),
                role = Role.Button,
                onClick = onOpen
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.crew_find_condition_title),
            style = RunkkTextStyles.text(14, FontWeight.Bold, lineHeight = 17),
            color = RunkkColors.TextPrimary
        )
        if (selected.isEmpty()) {
            Text(
                stringResource(R.string.crew_find_condition_hint),
                style = RunkkTextStyles.text(13, lineHeight = 16),
                color = RunkkColors.TextSecondary
            )
        } else {
            CrewTagPills(selected)
        }
    }
}

/** 조건 설정 팝업(피그마 F-01b): 바텀시트 제목·✕, "평일"·"주말" 줄마다 시간대 칩 넷, "적용하기"(Medium 48). */
@Composable
private fun ConditionSheet(draft: Set<String>, actions: CrewFindActions) {
    RunkkBottomSheet(
        onDismissRequest = actions.onConditionDismiss,
        title = stringResource(R.string.crew_find_condition_title)
    ) {
        CrewTagRows(selected = draft, onToggle = actions.onConditionToggle, isLabelBold = true, rowGap = 12)
        RunkkButton(
            stringResource(R.string.crew_find_condition_apply),
            onClick = actions.onConditionApply,
            size = RunkkButtonSize.Medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 크루 태그 고르기(피그마 F-01b·F-04): 줄마다 "평일"·"주말"(14) + 12 + 시간대 칩 넷(32, 간격 8). 3개를 넘기면
 * [com.a207.runkk.app.core.designsystem.RunkkTags.toggle]이 더 켜지 않는다. 조건 팝업은 이름이 Bold·줄 간격 12, 만들기는 Regular·8이다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrewTagRows(selected: Set<String>, onToggle: (String) -> Unit, isLabelBold: Boolean, rowGap: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(rowGap.dp)) {
        CREW_TAG_CATEGORIES.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(category.nameRes),
                    style = RunkkTextStyles.text(
                        14,
                        if (isLabelBold) FontWeight.Bold else FontWeight.Normal,
                        lineHeight = 17
                    ),
                    color = RunkkColors.TextPrimary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.tags.forEach { tag ->
                        RunkkChoiceChip(
                            label = stringResource(tag.slotRes),
                            isSelected = tag.id in selected,
                            onSelectedChange = { onToggle(tag.id) }
                        )
                    }
                }
            }
        }
    }
}

/** 목록 제목 줄(피그마 F-01·F-01c): "전체 크루"·"조건에 맞는 크루"(14 Bold) + 오른쪽 새로고침 단추. */
@Composable
private fun FindListTitle(hasCondition: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(if (hasCondition) R.string.crew_find_list_title_tags else R.string.crew_find_list_title),
            style = RunkkTextStyles.text(14, FontWeight.Bold, lineHeight = 17),
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            }
        )
        RefreshButton(onClick = onRefresh)
    }
}

/** 끝에서 이만큼 안쪽 줄이 보이면 다음 쪽을 읽는다. */
private const val LOAD_MORE_AHEAD = 3

/** 대기 중인 내 가입 신청 한 건(FR-143, 피그마에 없는 앱 상태): 크루명, 신청 일시와 취소 단추. */
@Composable
private fun PendingJoinCard(request: MyCrew.PendingJoin, isBusy: Boolean, onCancel: () -> Unit) {
    val cancelDescription = stringResource(R.string.crew_find_pending_cancel_description, request.crewName)
    val shape = RunkkShapes.ExtraLarge
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RunkkColors.Surface)
            .border(1.dp, RunkkColors.Border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(
                request.crewName,
                style = RunkkTextStyles.text(14, FontWeight.Bold),
                color = RunkkColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.crew_find_pending_caption, postedLabel(request.requestedAt)),
                style = RunkkTextStyles.Meta,
                color = RunkkColors.TextSecondary
            )
        }
        RunkkButton(
            stringResource(R.string.crew_action_cancel),
            onClick = onCancel,
            style = RunkkButtonStyle.Outline,
            size = RunkkButtonSize.Small,
            isEnabled = !isBusy,
            modifier = Modifier.semantics { contentDescription = cancelDescription }
        )
    }
}

internal const val CREW_MAX = 10

@Preview(name = "크루 찾기", widthDp = 390, heightDp = 844)
@Composable
private fun CrewFindPreview() {
    RunkkTheme(reduceMotion = true) { CrewFindScreen(CrewFindUiState(isLoading = false), CrewFindActions()) }
}
