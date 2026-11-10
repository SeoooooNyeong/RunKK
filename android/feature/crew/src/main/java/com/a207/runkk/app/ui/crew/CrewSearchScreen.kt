package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.a207.runkk.app.core.designsystem.RunkkColors
import com.a207.runkk.app.core.designsystem.RunkkConfirmDialog
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkIconButton
import com.a207.runkk.app.core.designsystem.RunkkIcons
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkSnackbarHost
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.RunkkTopBar
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.CrewList
import com.a207.runkk.app.domain.crew.MyCrew

/** 크루 검색(Routes.CREW_SEARCH, 피그마 F-01d·F-01e). 가입되면 크루 탭으로 돌아가 내 크루 홈을 연다. */
@Composable
fun CrewSearchRoute(viewModel: CrewSearchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalRunkkNavigator.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CrewSearchEvent.Joined -> navigator.selectTab(Routes.CREW)
            }
        }
    }
    val loadLogo = remember(viewModel) { viewModel::logo }
    CompositionLocalProvider(LocalCrewLogoLoader provides loadLogo) {
        CrewSearchScreen(
            state,
            CrewSearchActions(
                onBack = navigator::back,
                onKeyword = viewModel::onKeyword,
                onSearch = viewModel::onSearch,
                onClear = viewModel::onClear,
                onLoadMore = viewModel::loadMore,
                onOpen = viewModel::onOpen,
                onCloseDetail = viewModel::onCloseDetail,
                onJoin = viewModel::onJoin,
                onCancelAsk = viewModel::onCancelAsk,
                onCancelDismiss = viewModel::onCancelDismiss,
                onCancelConfirm = viewModel::onCancelConfirm,
                onMessageShown = viewModel::onMessageShown
            )
        )
    }
}

/** 크루 검색 화면의 입력 묶음. */
data class CrewSearchActions(
    val onBack: () -> Unit = {},
    val onKeyword: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClear: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onOpen: (CrewList.Item) -> Unit = {},
    val onCloseDetail: () -> Unit = {},
    val onJoin: (CrewList.Item) -> Unit = {},
    val onCancelAsk: (MyCrew.PendingJoin) -> Unit = {},
    val onCancelDismiss: () -> Unit = {},
    val onCancelConfirm: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)

/**
 * 크루 검색(피그마 F-01d): 하단 내비 없음, 머리글 "크루 검색"(왼쪽 정렬)과 뒤로, 46 알약 검색 바(입력값, 오른쪽 ✕ 지우기),
 * "검색 결과 [n]개", 크루 카드 목록(간격 12). 결과가 없으면 F-01e 빈 화면. 결과를 누르면 크루 상세를 이 자리에 연다.
 */
@Composable
fun CrewSearchScreen(state: CrewSearchUiState, actions: CrewSearchActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(state.message) {
        message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    Box(modifier.fillMaxSize().background(RunkkColors.Background)) {
        val detail = state.detail
        if (detail != null) {
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
            Column(Modifier.fillMaxSize()) {
                RunkkTopBar(
                    title = stringResource(R.string.crew_search_title),
                    onBack = actions.onBack,
                    isTitleCentered = false
                )
                SearchInput(state.keyword, actions)
                SearchResults(state, actions)
            }
        }
        RunkkSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
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

/** 검색 바(입력값 14, 키보드 검색 단추로 찾는다, 오른쪽 ✕ 지우기). 보조 기술에는 "크루 검색" 입력칸으로 읽힌다. */
@Composable
private fun SearchInput(keyword: String, actions: CrewSearchActions) {
    val label = stringResource(R.string.crew_search_title)
    CrewSearchBarFrame(Modifier.padding(start = 20.dp, end = 20.dp, top = 11.dp)) {
        BasicTextField(
            value = keyword,
            onValueChange = actions.onKeyword,
            singleLine = true,
            textStyle = RunkkTextStyles.text(14).copy(color = RunkkColors.TextPrimary),
            cursorBrush = SolidColor(RunkkColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
            modifier = Modifier.weight(1f).semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (keyword.isEmpty()) {
                        Text(
                            stringResource(R.string.crew_find_search_placeholder),
                            style = RunkkTextStyles.text(14),
                            color = RunkkColors.TextSecondary
                        )
                    }
                    inner()
                }
            }
        )
        if (keyword.isNotEmpty()) {
            RunkkIconButton(
                RunkkIcons.Close,
                contentDescription = stringResource(R.string.crew_search_clear),
                onClick = actions.onClear,
                containerColor = Color.Transparent,
                iconTint = RunkkColors.TextSecondary
            )
        }
    }
}

/** 결과 수와 목록(끝에서 세 줄 안쪽이 보이면 다음 쪽), 빈 결과(F-01e), 오류. */
@Composable
private fun SearchResults(state: CrewSearchUiState, actions: CrewSearchActions) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - SEARCH_LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, state.nextCursor, state.results.size) {
        if (nearEnd && state.nextCursor != null) actions.onLoadMore()
    }
    when {
        state.isLoading && state.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(32.dp)) { RunkkLoading() }

        state.error != null && state.results.isEmpty() ->
            Box(Modifier.padding(20.dp)) { RunkkErrorState(state.error.asString(), onRetry = actions.onSearch) }

        !state.hasSearched -> Unit

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.crew_search_result_count, state.resultCount.toInt()),
                    style = RunkkTextStyles.text(13, FontWeight.Bold, lineHeight = 16),
                    color = RunkkColors.TextPrimary,
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    }
                )
            }
            if (state.results.isEmpty()) {
                item { SearchEmpty() }
            } else {
                items(state.results, key = {
                    it.crewId
                }) { crew -> FindCrewCard(crew, onClick = { actions.onOpen(crew) }) }
                if (state.isLoadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp)) { RunkkLoading() } }
            }
        }
    }
}

/** 검색 결과 없음(피그마 F-01e): 높이 420 가운데 "검색 결과가 없어요"(16 Medium) + 8 + 안내 두 줄(13, 가운데). 단추 없음. */
@Composable
private fun SearchEmpty() {
    Column(
        Modifier.fillMaxWidth().height(420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.crew_search_empty_title),
            style = RunkkTextStyles.text(16, FontWeight.Medium, lineHeight = 19),
            color = RunkkColors.TextPrimary
        )
        Box(Modifier.size(8.dp))
        Text(
            stringResource(R.string.crew_search_empty_body),
            style = RunkkTextStyles.text(13, lineHeight = 16),
            color = RunkkColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private const val SEARCH_LOAD_AHEAD = 3
