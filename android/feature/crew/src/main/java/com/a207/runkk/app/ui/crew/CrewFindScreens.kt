package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RUNKK_BOTTOM_BAR_RISE
import com.a207.runkk.app.core.designsystem.RunkkBadge
import com.a207.runkk.app.core.designsystem.RunkkBadgeTone
import com.a207.runkk.app.core.designsystem.RunkkCard
import com.a207.runkk.app.core.designsystem.RunkkChoiceChip
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLimePillButton
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkPillButton
import com.a207.runkk.app.core.designsystem.RunkkPrimaryButton
import com.a207.runkk.app.core.designsystem.RunkkShapes
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTags
import com.a207.runkk.app.core.designsystem.RunkkTextLink
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.MyCrew

/**
 * 크루 탭: 들어올 때마다 내 크루(GET /crews/me)를 확인해 크루가 있으면 내 크루 홈을 이 자리에 보이고(뒤로 단추 없음),
 * 없으면 크루 찾기·상세·초대 코드·만들기·대기 중인 내 가입 신청을 보인다.
 */
@Composable
fun CrewFindRoute(
    initialTab: CrewTab? = null,
    initialFilter: BoardFilter = BoardFilter.ALL,
    viewModel: CrewFindViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    // 크루 만들기 등에서 돌아오면 소속을 다시 확인한다(처음 들어올 때는 ViewModel init이 확인한다).
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshMine()
        onPauseOrDispose { }
    }
    val mine = state.mine
    when {
        mine != null -> {
            // 알림으로 들어오면(initialTab) 그 탭(게시판이면 initialFilter 칩)을 먼저 열고 뒤로 단추를 둔다(D-150).
            CrewHomeRoute(
                Routes.CrewHome(mine.crewId),
                isAtTabRoot = initialTab == null,
                initialTab = initialTab ?: CrewTab.RACE,
                initialFilter = initialFilter
            )
            return
        }

        state.isCheckingMine -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { RunkkLoading() }
            return
        }

        state.mineError != null -> {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                RunkkErrorState(state.mineError?.asString() ?: "", onRetry = { viewModel.loadMine() })
            }
            return
        }
    }
    CrewFindScreen(
        state = state,
        actions = crewFindActions(viewModel) { navigator.navigate(Routes.CREW_CREATE) }
    )
}

/** ViewModel의 동작을 화면 입력 묶음으로 잇는다. */
private fun crewFindActions(viewModel: CrewFindViewModel, onCreate: () -> Unit) = CrewFindActions(
    onKeyword = viewModel::onKeyword,
    onSearch = viewModel::load,
    onTag = viewModel::onTagToggle,
    onOpen = viewModel::onOpen,
    onCloseDetail = viewModel::onCloseDetail,
    onJoin = viewModel::onJoin,
    onCreate = onCreate,
    onInviteOpen = viewModel::onInviteOpen,
    onInviteClose = viewModel::onInviteClose,
    onInviteCode = viewModel::onInviteCode,
    onInviteSubmit = viewModel::onInviteSubmit,
    onRetry = viewModel::load,
    onMessageShown = viewModel::onMessageShown,
    onLoadMore = viewModel::loadMore,
    onCancelAsk = viewModel::onCancelAsk,
    onCancelDismiss = viewModel::onCancelDismiss,
    onCancelConfirm = viewModel::onCancelConfirm
)

/** 크루 찾기 화면의 입력 묶음. */
data class CrewFindActions(
    val onKeyword: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onTag: (String) -> Unit = {},
    val onOpen: (CrewList.Item) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onJoin: (CrewList.Item) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onInviteOpen: () -> Unit = {},
    val onInviteClose: () -> Unit = {},
    val onInviteCode: (String) -> Unit = {},
    val onInviteSubmit: () -> Unit = {},
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
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val detail = state.detail
        when {
            // 초대 코드(E5)와 크루 정보(I1)는 목록 위에 덮는 전체 화면이다(시스템 뒤로 가기로 닫는다).
            state.isInviteOpen -> CrewInvitePage(state, actions)

            detail != null -> CrewDetailPage(state, detail, actions)

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 목록 끝은 솟은 탭 원에 가리지 않게 원 높이만큼 더 비운다(D100).
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom =
                        16.dp + RUNKK_BOTTOM_BAR_RISE
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                crewFindItems(state, actions)
            }
        }
        RunkkSnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp + RUNKK_BOTTOM_BAR_RISE)
        )
    }
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

/** 크루 찾기 목록의 줄들(와이어 E1): 머리글, 검색, 태그로 찾기, 대기 중 신청, 목록 제목, 공개 크루. */
private fun LazyListScope.crewFindItems(state: CrewFindUiState, actions: CrewFindActions) {
    item { FindHeaderRow(onInviteOpen = actions.onInviteOpen, onCreate = actions.onCreate) }
    item { FindSearchField(state.keyword, actions.onKeyword, actions.onSearch) }
    // 성향 태그 필터가 크루 찾기의 주 경로다(FR-117, CR-04). 추천 영역은 지웠다(FR-118 삭제).
    item { FindTagRow(selected = state.tags, onTag = actions.onTag) }
    if (state.pending.isNotEmpty()) {
        items(state.pending, key = { "pending-${it.requestId}" }) { request ->
            PendingJoinCard(request, isBusy = state.isJoining, onCancel = { actions.onCancelAsk(request) })
        }
    }
    item { FindListTitle(isLoading = state.isLoading, count = state.crews.size, tags = state.tags) }
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
            items(state.crews, key = { it.crewId }) { crew -> CrewRow(crew, onClick = { actions.onOpen(crew) }) }
            if (state.isLoadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp)) { RunkkLoading() } }
            }
        }
    }
}

/** 머리글: 제목과 초대 코드, 크루 만들기 단추. */
@Composable
private fun FindHeaderRow(onInviteOpen: () -> Unit, onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.crew_find_title),
            style = RunkkTextStyles.Headline,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkPillButton(stringResource(R.string.crew_find_invite_code), onClick = onInviteOpen)
        RunkkLimePillButton(
            stringResource(R.string.crew_create_title),
            onClick = onCreate,
            leadingIcon = RunkkIcons.Plus
        )
    }
}

/** 검색 칸(와이어 E1): 돋보기와 자리 문구. 키보드 검색 단추로 찾는다. 보조 기술에는 "크루 찾기" 입력칸으로 읽힌다. */
@Composable
private fun FindSearchField(keyword: String, onKeyword: (String) -> Unit, onSearch: () -> Unit) {
    val label = stringResource(R.string.crew_find_search_label)
    val shape = RunkkShapes.Medium
    BasicTextField(
        value = keyword,
        onValueChange = onKeyword,
        singleLine = true,
        textStyle = RunkkTextStyles.text(15).copy(color = RunkkColors.TextPrimary),
        cursorBrush = SolidColor(RunkkColors.Lime),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(RunkkColors.Surface)
                    .border(1.dp, RunkkColors.InputBorder, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    RunkkIcons.Search,
                    contentDescription = null,
                    tint = RunkkColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Box(Modifier.weight(1f)) {
                    if (keyword.isEmpty()) {
                        Text(
                            stringResource(R.string.crew_find_search_placeholder),
                            style = RunkkTextStyles.text(15),
                            color = RunkkColors.TextSecondary
                        )
                    }
                    inner()
                }
            }
        }
    )
}

/**
 * 태그로 찾기(주 경로, FR-117, 와이어 E1): 고른 태그를 앞에 두고 처음에는 다섯 개와 "태그 더보기"만 보인다. 고른 태그를 모두 가진
 * 크루만 남는다(AND).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindTagRow(selected: Set<String>, onTag: (String) -> Unit) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val all = RunkkTags.categories.flatMap { it.tags }
    val ordered = all.filter { it.id in selected } + all.filterNot { it.id in selected }
    val shown = if (isExpanded) ordered else ordered.take(maxOf(TAGS_COLLAPSED, selected.size))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.crew_find_tag_title),
            style = RunkkTextStyles.CardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { tag ->
                RunkkChoiceChip(
                    stringResource(tag.labelRes),
                    isSelected = tag.id in selected,
                    onSelectedChange = { onTag(tag.id) }
                )
            }
            RunkkTextLink(
                stringResource(if (isExpanded) R.string.crew_find_tag_less else R.string.crew_find_tag_more),
                onClick = { isExpanded = !isExpanded }
            )
        }
    }
}

/** 목록 제목: "함께가 좋아요, 저녁 태그 크루 n개, 최근 생성순"(태그를 골랐을 때), 아니면 "공개 크루 n개, 최근 생성순". */
@Composable
private fun FindListTitle(isLoading: Boolean, count: Int, tags: Set<String>) {
    val resources = LocalContext.current.resources
    val tagNames = tags.joinToString(", ") { RunkkTags.label(resources, it) }
    Text(
        when {
            isLoading -> stringResource(R.string.crew_find_list_title_loading)
            tags.isNotEmpty() -> stringResource(R.string.crew_find_list_title_tags, tagNames, count)
            else -> stringResource(R.string.crew_find_list_title, count)
        },
        style = RunkkTextStyles.text(13, FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/** 끝에서 이만큼 안쪽 줄이 보이면 다음 쪽을 읽는다. */
private const val LOAD_MORE_AHEAD = 3

/** 접었을 때 보이는 태그 수. */
private const val TAGS_COLLAPSED = 5

/** 대기 중인 내 가입 신청 한 건(FR-143): 크루명, 신청 일시와 취소 단추. */
@Composable
private fun PendingJoinCard(request: MyCrew.PendingJoin, isBusy: Boolean, onCancel: () -> Unit) {
    val cancelDescription = stringResource(R.string.crew_find_pending_cancel_description, request.crewName)
    CrewCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    request.crewName,
                    style = RunkkTextStyles.BodyStrong,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.crew_find_pending_caption, postedLabel(request.requestedAt)),
                    style = RunkkTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RunkkBadge(stringResource(R.string.crew_find_pending_badge), tone = RunkkBadgeTone.Muted)
            RunkkPillButton(
                stringResource(R.string.crew_action_cancel),
                onClick = onCancel,
                isEnabled = !isBusy,
                modifier = Modifier.semantics { contentDescription = cancelDescription }
            )
        }
    }
}

@Composable
private fun CrewRow(crew: CrewList.Item, onClick: () -> Unit) {
    val resources = LocalContext.current.resources
    RunkkCard(
        onClick = onClick,
        onClickLabel = stringResource(R.string.crew_find_open_detail, crew.name),
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        background = MaterialTheme.colorScheme.surface
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    crew.name,
                    style = RunkkTextStyles.text(15, FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    crewMeta(crew),
                    style = RunkkTextStyles.Caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (crew.tags.isNotEmpty()) {
                    Text(
                        crew.tags.joinToString(", ") { RunkkTags.label(resources, it) },
                        style = RunkkTextStyles.Caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RunkkBadge(stringResource(crew.joinPolicy.labelRes))
        }
    }
}

/** "크루장 X, 4/10명, 이번 주 12.3km"(크루 밖 열람 범위, FR-117·W-16). */
@Composable
internal fun crewMeta(crew: CrewList.Item): String = listOfNotNull(
    crew.leaderName.takeIf { it.isNotBlank() }?.let { stringResource(R.string.crew_meta_leader, it) },
    stringResource(R.string.crew_meta_headcount, crew.headcount, CREW_MAX),
    stringResource(R.string.crew_meta_weekly_distance, RunkkFormat.kmShort(crew.weeklyDistance))
).joinToString(", ")

internal const val CREW_MAX = 10

/** 크루 만들기. */
@Composable
fun CrewCreateRoute(viewModel: CrewCreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // 크루 탭을 새로 열면 내 크루를 다시 확인해 방금 만든 크루 홈을 보인다.
                CrewEvent.Created -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    CrewCreateScreen(state, onBack = navigator::back, onForm = viewModel::onForm, onSubmit = viewModel::onSubmit)
}

/** 크루 만들기(와이어 I2): 크루명, 한줄 소개, 성향 태그, 공개 여부, 가입 방식, 크루 만들기 단추. */
@Composable
fun CrewCreateScreen(
    state: CrewCreateUiState,
    onBack: () -> Unit,
    onForm: (CrewFormState) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RunkkTopBar(title = stringResource(R.string.crew_create_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CrewFormFields(state.form, onForm)
            state.error?.let {
                Text(
                    it.asString(),
                    style = RunkkTextStyles.text(13, FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RunkkPrimaryButton(
            stringResource(R.string.crew_create_title),
            onClick = onSubmit,
            isEnabled = state.form.isValid,
            isLoading = state.isSaving,
            // E2E(android/e2e/maestro) 선택자: 상단 "크루 만들기"와 이름이 같아 태그로 찾는다.
            modifier = Modifier
                .testTag("crew_create_submit")
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
        )
    }
}

@Preview(name = "크루 찾기", widthDp = 390, heightDp = 844)
@Composable
private fun CrewFindPreview() {
    RunkkTheme(reduceMotion = true) { CrewFindScreen(CrewFindUiState(isLoading = false), CrewFindActions()) }
}
