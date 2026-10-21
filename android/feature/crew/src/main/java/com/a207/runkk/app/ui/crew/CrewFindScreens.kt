package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTheme
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.JoinPolicy
import com.a207.runkk.app.domain.crew.MyCrew

/**
 * 크루 탭: 들어올 때마다 내 크루(GET /crews/me)를 확인해 크루가 있으면 내 크루 홈을 이 자리에 보이고(뒤로 단추 없음),
 * 없으면 크루 찾기·상세·초대 코드·만들기·대기 중인 내 가입 신청을 보인다.
 */
@Composable
fun CrewFindRoute(viewModel: CrewFindViewModel = hiltViewModel()) {
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
            CrewHomeRoute(Routes.CrewHome(mine.crewId), atTabRoot = true)
            return
        }

        state.checkingMine -> {
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

@OptIn(ExperimentalMaterial3Api::class)
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            crewFindItems(state, actions)
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
    FindOverlays(state, actions)
}

/** 크루 상세 시트, 초대 코드 대화상자, 가입 신청 취소 확인. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FindOverlays(state: CrewFindUiState, actions: CrewFindActions) {
    state.detail?.let { crew ->
        ModalBottomSheet(onDismissRequest = actions.onCloseDetail, containerColor = RunkkColors.Surface) {
            CrewDetailContent(crew, joining = state.joining, onJoin = { actions.onJoin(crew) })
        }
    }
    if (state.inviteOpen) InviteDialog(state, actions)
    state.confirmCancel?.let { request ->
        RunkkConfirmDialog(
            title = stringResource(R.string.crew_find_cancel_confirm_title),
            message = stringResource(R.string.crew_find_cancel_confirm_message, request.crewName),
            confirmLabel = stringResource(R.string.crew_find_cancel_confirm_action),
            onConfirm = actions.onCancelConfirm,
            onDismiss = actions.onCancelDismiss,
            destructive = true
        )
    }
}

/** 크루 찾기 목록의 줄들: 머리글, 대기 중 신청, 검색, 태그, 추천, 공개 크루 목록. */
private fun LazyListScope.crewFindItems(state: CrewFindUiState, actions: CrewFindActions) {
    item { FindHeaderRow(onInviteOpen = actions.onInviteOpen, onCreate = actions.onCreate) }
    if (state.pending.isNotEmpty()) {
        items(state.pending, key = { "pending-${it.requestId}" }) { request ->
            PendingJoinCard(request, busy = state.joining, onCancel = { actions.onCancelAsk(request) })
        }
    }
    item { FindSearchRow(state.keyword, actions.onKeyword, actions.onSearch) }
    item { FindTagRow(selected = state.tags, onTag = actions.onTag) }
    state.recommendations.firstOrNull()?.let { top ->
        item { ForYouCard(top, onOpen = { actions.onOpen(top) }, onJoin = { actions.onJoin(top) }) }
    }
    item { FindListTitle(loading = state.loading, count = state.crews.size) }
    when {
        state.loading && state.crews.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().padding(32.dp)) { RunkkLoading() }
        }

        state.error != null && state.crews.isEmpty() -> item {
            RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
        }

        state.crews.isEmpty() -> item {
            RunkkEmptyState(
                stringResource(R.string.crew_find_empty_title),
                message = stringResource(R.string.crew_find_empty_message),
                actionLabel = stringResource(R.string.crew_create_title),
                onAction = actions.onCreate
            )
        }

        else -> {
            items(state.crews, key = {
                it.crewId
            }) { crew -> CrewRow(crew, onClick = { actions.onOpen(crew) }) }
            if (state.loadingMore) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp)) { RunkkLoading() } }
            }
        }
    }
}

/** 머리글: 제목과 초대 코드·만들기 단추. */
@Composable
private fun FindHeaderRow(onInviteOpen: () -> Unit, onCreate: () -> Unit) {
    val createDescription = stringResource(R.string.crew_create_title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.crew_find_title),
            style = RunkkTextStyles.Headline,
            color = RunkkColors.TextPrimary,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        RunkkPillButton(stringResource(R.string.crew_find_invite_code), onClick = onInviteOpen)
        RunkkLimePillButton(
            stringResource(R.string.crew_find_create),
            onClick = onCreate,
            leadingIcon = RunkkIcons.Plus,
            modifier = Modifier.semantics { contentDescription = createDescription }
        )
    }
}

/** 키워드 검색 줄. */
@Composable
private fun FindSearchRow(keyword: String, onKeyword: (String) -> Unit, onSearch: () -> Unit) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RunkkTextField(
            value = keyword,
            onValueChange = onKeyword,
            label = stringResource(R.string.crew_find_search_label),
            placeholder = stringResource(R.string.crew_find_search_placeholder),
            imeAction = ImeAction.Search,
            onImeAction = onSearch,
            modifier = Modifier.weight(1f)
        )
        RunkkPillButton(
            stringResource(R.string.crew_find_search_action),
            onClick = onSearch,
            leadingIcon = RunkkIcons.Search
        )
    }
}

/** 태그 필터 칩 줄. */
@Composable
private fun FindTagRow(selected: Set<String>, onTag: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(RunkkTags.categories.flatMap { it.tags }, key = { it.id }) { tag ->
            RunkkChoiceChip(stringResource(tag.labelRes), selected = tag.id in selected, onSelectedChange = {
                onTag(tag.id)
            })
        }
    }
}

/** 공개 크루 목록 제목(읽는 중에는 개수를 숨긴다). */
@Composable
private fun FindListTitle(loading: Boolean, count: Int) {
    Text(
        if (loading) {
            stringResource(R.string.crew_find_list_title_loading)
        } else {
            stringResource(R.string.crew_find_list_title, count)
        },
        style = RunkkTextStyles.text(13, FontWeight.Bold),
        color = RunkkColors.TextPrimary,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        }
    )
}

/** 끝에서 이만큼 안쪽 줄이 보이면 다음 쪽을 읽는다. */
private const val LOAD_MORE_AHEAD = 3

/** 대기 중인 내 가입 신청 한 건(FR-143): 크루명·신청 일시와 취소 단추. */
@Composable
private fun PendingJoinCard(request: MyCrew.PendingJoin, busy: Boolean, onCancel: () -> Unit) {
    val cancelDescription = stringResource(R.string.crew_find_pending_cancel_description, request.crewName)
    RunkkCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text(
                    request.crewName,
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.crew_find_pending_caption, postedLabel(request.requestedAt)),
                    style = RunkkTextStyles.Caption,
                    color = RunkkColors.TextSecondary
                )
            }
            RunkkBadge(stringResource(R.string.crew_find_pending_badge), tone = RunkkBadgeTone.Muted)
            RunkkPillButton(
                stringResource(R.string.crew_action_cancel),
                onClick = onCancel,
                enabled = !busy,
                modifier = Modifier.semantics { contentDescription = cancelDescription }
            )
        }
    }
}

@Composable
private fun ForYouCard(crew: CrewList.Item, onOpen: () -> Unit, onJoin: () -> Unit) {
    RunkkCard(
        shape = RunkkShapes.ExtraLarge,
        borderColor = RunkkColors.Lime,
        onClick = onOpen,
        onClickLabel = stringResource(R.string.crew_find_open_detail, crew.name),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.crew_find_for_you),
            style = RunkkTextStyles.display(14, letterSpacingEm = 0.08f),
            color = RunkkColors.Lime
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name, size = 56, accent = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    crew.name,
                    style = RunkkTextStyles.CardTitle,
                    color = RunkkColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(crewMeta(crew, leader = false), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
            }
            RunkkLimePillButton(
                stringResource(
                    if (crew.joinPolicy ==
                        JoinPolicy.FREE
                    ) {
                        R.string.crew_find_join_action
                    } else {
                        R.string.crew_find_apply_action
                    }
                ),
                onClick = onJoin,
                enabled = crew.headcount < CREW_MAX
            )
        }
    }
}

@Composable
private fun CrewRow(crew: CrewList.Item, onClick: () -> Unit) {
    val resources = LocalContext.current.resources
    RunkkCard(onClick = onClick, onClickLabel = stringResource(R.string.crew_find_open_detail, crew.name)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CrewLogo(crew.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(crew.name, style = RunkkTextStyles.text(15, FontWeight.Black), color = RunkkColors.TextPrimary)
                Text(crewMeta(crew, leader = true), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
                if (crew.tags.isNotEmpty()) {
                    Text(
                        crew.tags.joinToString(" · ") { RunkkTags.label(resources, it) },
                        style = RunkkTextStyles.Caption,
                        color = RunkkColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RunkkBadge(stringResource(crew.joinPolicy.labelRes))
        }
    }
}

/** "크루장 X · 4/10명 · 이번 주 12.3km"(크루 밖 열람 범위, FR-117·W-16). */
@Composable
internal fun crewMeta(crew: CrewList.Item, leader: Boolean): String = listOfNotNull(
    crew.leaderName.takeIf { leader && it.isNotBlank() }?.let { stringResource(R.string.crew_meta_leader, it) },
    if (leader) null else stringResource(crew.joinPolicy.labelRes),
    stringResource(R.string.crew_meta_headcount, crew.headcount, CREW_MAX),
    stringResource(R.string.crew_meta_weekly_distance, RunkkFormat.kmShort(crew.weeklyDistance))
).joinToString(" · ")

internal const val CREW_MAX = 10

/** 크루 상세(크루 밖 열람 범위: 소개·태그·로고·인원·크루장·이번 주 거리, Q-45). */
@Composable
internal fun CrewDetailContent(crew: CrewList.Item, joining: Boolean, onJoin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(
            rememberScrollState()
        ).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CrewDetailHeader(crew)
        crew.intro?.let { Text(it, style = RunkkTextStyles.Body, color = RunkkColors.TextPrimary) }
        if (crew.tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(crew.tags.toList(), key = { it }) { RunkkBadge(RunkkTags.label(it)) }
            }
        }
        InfoLine(stringResource(R.string.crew_detail_join_policy_label), stringResource(crew.joinPolicy.labelRes))
        HorizontalDivider(color = RunkkColors.Border)
        Text(
            stringResource(R.string.crew_detail_capacity_notice),
            style = RunkkTextStyles.Caption,
            color = RunkkColors.TextSecondary
        )
        val full = crew.headcount >= CREW_MAX
        RunkkPrimaryButton(
            text = stringResource(
                when {
                    full -> R.string.crew_detail_full
                    crew.joinPolicy == JoinPolicy.FREE -> R.string.crew_detail_join_free
                    else -> R.string.crew_detail_join_apply
                }
            ),
            onClick = onJoin,
            enabled = !full,
            loading = joining,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 크루 상세 머리글: 로고·이름·한 줄 요약. */
@Composable
private fun CrewDetailHeader(crew: CrewList.Item) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CrewLogo(crew.name, size = 56, accent = true)
        Column(Modifier.weight(1f)) {
            Text(
                crew.name,
                style = RunkkTextStyles.TopBarTitle,
                color = RunkkColors.TextPrimary,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Text(crewMeta(crew, leader = true), style = RunkkTextStyles.Caption, color = RunkkColors.TextSecondary)
        }
    }
}

@Composable
private fun InviteDialog(state: CrewFindUiState, actions: CrewFindActions) {
    AlertDialog(
        onDismissRequest = actions.onInviteClose,
        containerColor = RunkkColors.Surface,
        title = {
            Text(
                stringResource(R.string.crew_invite_title),
                style = RunkkTextStyles.CardTitle,
                color = RunkkColors.TextPrimary
            )
        },
        text = {
            RunkkTextField(
                value = state.inviteCode,
                onValueChange = actions.onInviteCode,
                label = stringResource(R.string.crew_invite_label),
                placeholder = "AB12CD"
            )
        },
        confirmButton = {
            TextButton(
                onClick = actions.onInviteSubmit,
                enabled =
                    state.inviteCode.length == INVITE_LENGTH && !state.joining
            ) {
                Text(
                    stringResource(R.string.crew_find_join_action),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.Lime
                )
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onInviteClose) {
                Text(
                    stringResource(R.string.crew_action_cancel),
                    style = RunkkTextStyles.BodyStrong,
                    color = RunkkColors.TextSecondary
                )
            }
        }
    )
}

private const val INVITE_LENGTH = 6

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

@Composable
fun CrewCreateScreen(
    state: CrewCreateUiState,
    onBack: () -> Unit,
    onForm: (CrewFormState) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(RunkkColors.Background)) {
        RunkkTopBar(title = stringResource(R.string.crew_create_title), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CrewFormFields(state.form, onForm)
            Text(
                stringResource(R.string.crew_create_notice),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary
            )
            state.error?.let {
                Text(it.asString(), style = RunkkTextStyles.text(13, FontWeight.Bold), color = RunkkColors.Lavender)
            }
        }
        HorizontalDivider(color = RunkkColors.Border)
        if (!state.form.valid) {
            Text(
                stringResource(R.string.crew_create_invalid_hint),
                style = RunkkTextStyles.Caption,
                color = RunkkColors.TextSecondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }
        RunkkPrimaryButton(
            stringResource(R.string.crew_create_title),
            onClick = onSubmit,
            enabled = state.form.valid,
            loading = state.saving,
            // E2E(android/e2e/maestro) 선택자: 상단 "만들기"와 이름이 같아 태그로 찾는다.
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
    RunkkTheme(reduceMotion = true) { CrewFindScreen(CrewFindUiState(loading = false), CrewFindActions()) }
}
