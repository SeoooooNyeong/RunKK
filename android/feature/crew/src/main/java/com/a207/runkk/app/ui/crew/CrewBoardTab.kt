package com.a207.runkk.app.ui.crew

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.a207.runkk.app.core.designsystem.RunkkEmptyState
import com.a207.runkk.app.core.designsystem.RunkkErrorState
import com.a207.runkk.app.core.designsystem.RunkkLoading
import com.a207.runkk.app.core.designsystem.RunkkTextField
import com.a207.runkk.app.core.designsystem.RunkkTextStyles
import com.a207.runkk.app.core.designsystem.asString
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.core.model.AppError
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.navigation.LocalRunkkNavigator
import com.a207.runkk.app.core.navigation.Routes
import com.a207.runkk.app.domain.crew.PostList

/**
 * 게시판 탭의 입력 묶음. [loadPhoto]는 사진 한 장의 JPEG 바이트를 인증된 연결로 받는다(D-100). [onNavigate]가 없으면 앱 내비게이터로
 * 투표 만들기·공지 쓰기·투표 화면을 연다(시험은 여기에 넣어 이동을 본다).
 */
internal data class CrewBoardActions(
    val onFilter: (BoardFilter) -> Unit = {},
    val onOpenPhoto: (Long) -> Unit = {},
    val onClosePhoto: () -> Unit = {},
    val onDeleteAsk: (PostList.Item) -> Unit = {},
    val onReport: (PostList.Item) -> Unit = {},
    val onPinToggle: (PostList.Item) -> Unit = {},
    val onCheerDraft: (String) -> Unit = {},
    val onCheerSubmit: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onNavigate: ((NavKey) -> Unit)? = null,
    val loadPhoto: suspend (Long) -> Outcome<ByteArray> = { Outcome.Failure(AppError.Network) }
)

internal fun crewBoardActions(vm: CrewBoardViewModel, onReport: (PostList.Item) -> Unit) = CrewBoardActions(
    onFilter = vm::onFilter,
    onOpenPhoto = vm::onOpenPhoto,
    onClosePhoto = vm::onClosePhoto,
    onDeleteAsk = vm::onDeleteAsk,
    onReport = onReport,
    onPinToggle = vm::onPinToggle,
    onCheerDraft = vm::onCheerDraft,
    onCheerSubmit = vm::onCheerSubmit,
    onRetry = vm::refresh,
    loadPhoto = vm::photoBytes
)

/**
 * 게시판 탭(와이어프레임 E3·M6): 종류 칩(전체·공지·투표·응원·게시글·앨범), 단추 줄(글쓰기, 투표 만들기, 운영진은 공지), 글 목록.
 * 전체·공지 칩에서는 고정 공지를 맨 위에 둔다. 응원 칩에서는 단추 줄 아래에 응원 쓰기 칸을 둔다(FR-129). 앨범은 사진만 3열로 보인다.
 * 크루 홈의 지연 목록(LazyColumn)에 글 하나·앨범 한 줄마다 항목을 더해 보이는 것만 그린다(A4). [onWrite]는 게시글 쓰기로 간다.
 */
internal fun LazyListScope.boardTabItems(state: CrewBoardUiState, actions: CrewBoardActions, onWrite: () -> Unit) {
    item(key = "board:head") { BoardHead(state, actions, onWrite) }
    when {
        state.error != null -> item(key = "board:error") {
            RunkkErrorState(state.error.asString(), onRetry = actions.onRetry)
        }

        state.isAlbum -> albumItems(state, actions)

        else -> postItems(state, actions)
    }
    if (state.isLoadingMore) item(key = "board:loading-more") { BoardLoading() }
}

/** 칩 줄, 단추 줄(앨범 제외), 응원 쓰기 칸(응원 칩), 앨범 정렬. */
@Composable
private fun BoardHead(state: CrewBoardUiState, actions: CrewBoardActions, onWrite: () -> Unit) {
    val navigator = LocalRunkkNavigator.current
    val navigate = actions.onNavigate ?: navigator::navigate
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val group = stringResource(R.string.crew_board_filter_group)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .semantics { contentDescription = group },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BoardFilter.entries.forEach { filter ->
                BoardChip(stringResource(filter.labelRes), filter == state.filter, onClick = {
                    actions.onFilter(filter)
                })
            }
        }
        if (!state.isAlbum) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoardButton(
                    stringResource(R.string.crew_board_write),
                    onWrite,
                    Modifier.weight(1f),
                    isPrimary = true,
                    isWide = true
                )
                BoardButton(stringResource(R.string.crew_poll_write), { navigate(Routes.CrewPollWrite(state.crewId)) })
                if (state.canManage) {
                    BoardButton(
                        stringResource(R.string.crew_kind_notice),
                        { navigate(Routes.CrewNoticeWrite(state.crewId)) },
                        contentDescription = stringResource(R.string.crew_board_notice_write_description)
                    )
                }
            }
        }
        if (state.filter == BoardFilter.CHEER) CheerComposer(state, actions)
        if (state.isAlbum) {
            Text(
                stringResource(R.string.crew_album_order),
                style = RunkkTextStyles.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 응원 쓰기 칸(200자, FR-129)과 남기기 단추. */
@Composable
private fun CheerComposer(state: CrewBoardUiState, actions: CrewBoardActions) {
    Column(horizontalAlignment = Alignment.End) {
        RunkkTextField(
            value = state.cheerDraft,
            onValueChange = actions.onCheerDraft,
            label = stringResource(R.string.crew_board_cheer_label),
            supporting = stringResource(
                R.string.crew_count,
                state.cheerDraft.codePointLength(),
                CrewBoardUiState.CHEER_MAX
            ),
            isSingleLine = false,
            modifier = Modifier.fillMaxWidth()
        )
        BoardButton(
            stringResource(R.string.crew_board_cheer_submit),
            actions.onCheerSubmit,
            isPrimary = true,
            isEnabled = state.isCheerValid && !state.isCheerBusy
        )
    }
}

private fun LazyListScope.postItems(state: CrewBoardUiState, actions: CrewBoardActions) {
    val posts = state.shownPosts
    when {
        posts == null -> if (state.isLoading) item(key = "board:loading") { BoardLoading() }

        posts.isEmpty() -> item(key = "board:empty") {
            RunkkEmptyState(stringResource(state.filter.emptyRes()))
        }

        else -> items(posts, key = { "board:post:${it.postId}" }) { post -> BoardPostCard(post, state, actions) }
    }
}

/** 사진 줄(한 줄 3장, 넘치면 다음 줄). [description]은 이 줄들 안의 순번(0부터)으로 설명을 만든다. */
@Composable
internal fun PhotoRow(photoIds: List<Long>, actions: CrewBoardActions, description: @Composable (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        photoIds.chunked(COLUMNS).forEachIndexed { row, ids ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ids.forEachIndexed { i, id ->
                    CrewPhoto(
                        photoId = id,
                        load = actions.loadPhoto,
                        description = description(row * COLUMNS + i),
                        onClick = { actions.onOpenPhoto(id) },
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    )
                }
                repeat(COLUMNS - ids.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 앨범: 사진 3장마다 한 항목(지연 격자). */
private fun LazyListScope.albumItems(state: CrewBoardUiState, actions: CrewBoardActions) {
    val photos = state.album?.photos
    when {
        photos == null -> if (state.isLoading) item(key = "album:loading") { BoardLoading() }

        photos.isEmpty() -> item(key = "album:empty") { RunkkEmptyState(stringResource(R.string.crew_album_empty)) }

        else -> {
            val rows = photos.chunked(COLUMNS)
            items(rows, key = { row -> "album:${row.first().photoId}" }) { row ->
                PhotoRow(row.map { it.photoId }, actions) { index ->
                    stringResource(R.string.crew_album_photo_description, postedLabel(row[index].postedAt))
                }
            }
        }
    }
}

@Composable
private fun BoardLoading() {
    Box(Modifier.fillMaxWidth().height(160.dp)) { RunkkLoading() }
}

/** 칩마다 빈 목록 문구. */
private fun BoardFilter.emptyRes(): Int = when (this) {
    BoardFilter.ALL -> R.string.crew_board_empty_all
    BoardFilter.NOTICE -> R.string.crew_notice_empty
    BoardFilter.POLL -> R.string.crew_poll_empty
    BoardFilter.CHEER -> R.string.crew_home_cheer_empty
    BoardFilter.POST -> R.string.crew_board_empty
    BoardFilter.ALBUM -> R.string.crew_album_empty
}

internal const val COLUMNS = 3
