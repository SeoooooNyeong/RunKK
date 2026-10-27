package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.takeCodePoints
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.map
import com.a207.runkk.app.domain.crew.Album
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.CrewRole
import com.a207.runkk.app.domain.crew.PostList
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 크루 게시판(와이어프레임 E3·M6): 종류 칩(전체·공지·투표·응원·게시글·앨범)마다 글을 최신순으로 읽고, 전체·공지 칩에서는 고정 공지를
 * 맨 위에 둔다. 응원 칩에서는 응원을 쓴다(FR-129). 공지 고정·해제(운영진, FR-205), 삭제(canDelete, FR-213), 사진 크게 보기(FR-212).
 * 크루 상세에서 인원(투표 응답 막대)과 내 역할(공지 단추)을 읽는다. 사진은 CrewRepository.photo(photoId)로 인증된 연결에서 받고
 * [PHOTO_CACHE]장까지 기억한다(D-100). 숨김·삭제된 글과 그 사진은 서버가 빼고 준다(FR-212).
 */
@HiltViewModel
class CrewBoardViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewBoardUiState())
    val state: StateFlow<CrewBoardUiState> = _state.asStateFlow()

    private var bound: Long? = null
    private var moreJob: Job? = null

    /** 최근에 받은 사진 바이트(가장 오래 안 쓴 것부터 버린다). */
    private val photos = object : LinkedHashMap<Long, ByteArray>(PHOTO_CACHE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > PHOTO_CACHE
    }

    /** 크루 게시판을 연다. [filter]는 처음 열 때 고를 칩이다(알림으로 공지·투표에 들어올 때, D-150). 이미 열었으면 칩을 그대로 둔다. */
    fun bind(crewId: Long, filter: BoardFilter = BoardFilter.ALL) {
        if (bound == crewId) return
        bound = crewId
        _state.value = CrewBoardUiState(crewId = crewId, filter = filter, now = clock.instant())
        viewModelScope.launch { loadDetail() }
        refresh()
    }

    private suspend fun loadDetail() {
        val detail = (crews.detail(_state.value.crewId) as? Outcome.Success)?.value ?: return
        val role = detail.myRole
        _state.update {
            it.copy(headcount = detail.headcount, canManage = role == CrewRole.LEADER || role == CrewRole.MANAGER)
        }
    }

    /** 지금 칩을 처음부터 다시 읽는다(전체·공지 칩이면 고정 공지도). */
    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null, now = clock.instant()) }
        viewModelScope.launch {
            moreJob?.cancel()
            val filter = _state.value.filter
            if (filter == BoardFilter.ALBUM) loadAlbum() else reload(filter, loaded = 0)
        }
    }

    /** 칩 바꾸기. 앞 칩의 목록은 버리고 새로 읽는다. */
    fun onFilter(filter: BoardFilter) {
        if (filter == _state.value.filter) return
        moreJob?.cancel()
        _state.update { it.copy(filter = filter, posts = null, album = null, isLoadingMore = false, error = null) }
        refresh()
    }

    /** [filter] 칩의 글을 [loaded]개까지 다시 읽는다. 전체 칩은 일정 글을 빼고, 보이는 글이 없으면 다음 쪽을 몇 번 더 읽는다. */
    private suspend fun reload(filter: BoardFilter, loaded: Int) {
        val pinnedJob = viewModelScope.async { if (filter.showsPinned()) pinnedNotices() else emptyList() }
        val r = crews.postsCovering(_state.value.crewId, filter.kind, loaded).map { it.boardOnly() }
        val pinned = pinnedJob.await()
        when (r) {
            is Outcome.Success -> {
                _state.update {
                    if (it.filter != filter) return@update it
                    it.copy(isLoading = false, isLoadingMore = false, posts = r.value, pinned = pinned)
                }
                fillIfEmpty(filter)
            }

            is Outcome.Failure -> _state.update {
                if (it.filter != filter) it else it.copy(isLoading = false, error = CrewErrorText.of(r.error))
            }
        }
    }

    /** 고정 공지(최대 3, FR-205). 공지 첫 쪽에서 고른다. 읽지 못하면 빈 목록. */
    private suspend fun pinnedNotices(): List<PostList.Item> =
        (crews.posts(_state.value.crewId, PostList.Kind.NOTICE) as? Outcome.Success)?.value?.posts.orEmpty()
            .filter { it.notice?.isPinned == true }
            .take(CrewBoardUiState.PIN_MAX)

    /** 전체 칩에서 한 쪽이 모두 일정 글이면 보이는 글이 없으니 몇 쪽 더 이어 읽는다(끝까지 내리기 전에 빈 화면이 되지 않게). */
    private suspend fun fillIfEmpty(filter: BoardFilter) {
        repeat(FILL_PAGES_MAX) {
            val s = _state.value
            val cursor = s.posts?.nextCursor
            if (s.filter != filter || cursor == null || s.posts.posts.size >= FILL_MIN) return
            val next = (crews.posts(s.crewId, filter.kind, cursor) as? Outcome.Success)?.value ?: return
            _state.update { if (it.filter != filter) it else it.copy(posts = it.posts.append(next.boardOnly())) }
        }
    }

    private suspend fun loadAlbum() {
        when (val r = crews.album(_state.value.crewId)) {
            is Outcome.Success -> _state.update {
                if (it.isAlbum) it.copy(isLoading = false, isLoadingMore = false, album = r.value) else it
            }

            is Outcome.Failure -> _state.update {
                if (it.isAlbum) it.copy(isLoading = false, error = CrewErrorText.of(r.error)) else it
            }
        }
    }

    fun onLoadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        moreJob = viewModelScope.launch {
            if (s.isAlbum) {
                when (val r = crews.album(s.crewId, s.album?.nextCursor)) {
                    is Outcome.Success -> _state.update {
                        it.copy(isLoadingMore = false, album = it.album.append(r.value))
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error))
                    }
                }
            } else {
                when (val r = crews.posts(s.crewId, s.filter.kind, s.posts?.nextCursor)) {
                    is Outcome.Success -> _state.update {
                        it.copy(isLoadingMore = false, posts = it.posts.append(r.value.boardOnly()))
                    }

                    is Outcome.Failure -> _state.update {
                        it.copy(isLoadingMore = false, message = CrewErrorText.of(r.error))
                    }
                }
            }
        }
    }

    fun onOpenPhoto(photoId: Long) = _state.update { it.copy(viewer = photoId) }

    fun onClosePhoto() = _state.update { it.copy(viewer = null) }

    /** 사진 한 장의 JPEG 바이트. 기억해 둔 사진이면 다시 받지 않는다. */
    suspend fun photoBytes(photoId: Long): Outcome<ByteArray> {
        synchronized(photos) { photos[photoId] }?.let { return Outcome.Success(it) }
        val r = crews.photo(photoId)
        if (r is Outcome.Success) synchronized(photos) { photos[photoId] = r.value }
        return r
    }

    /** 응원 쓰기 칸. 200자(코드 포인트)까지만 받는다. */
    fun onCheerDraft(text: String) = _state.update { it.copy(cheerDraft = text.takeCodePoints(CHEER_INPUT_MAX)) }

    fun onCheerSubmit() {
        val s = _state.value
        if (!s.isCheerValid || s.isCheerBusy) return
        _state.update { it.copy(isCheerBusy = true) }
        viewModelScope.launch {
            when (val r = crews.writeCheer(s.crewId, s.cheerDraft.trim())) {
                is Outcome.Success -> {
                    _state.update { it.copy(isCheerBusy = false, cheerDraft = "") }
                    refresh()
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isCheerBusy = false, message = CrewErrorText.of(r.error))
                }
            }
        }
    }

    /** 공지 고정·해제(크루장·운영진, FR-205). 고정은 3개까지이고 넘으면 서버가 PIN_LIMIT로 거부한다. */
    fun onPinToggle(item: PostList.Item) {
        val notice = item.notice ?: return
        if (!_state.value.canManage) return
        viewModelScope.launch {
            val message = when (val r = crews.pin(item.postId, !notice.isPinned)) {
                is Outcome.Success -> uiText(
                    if (notice.isPinned) R.string.crew_notice_unpinned else R.string.crew_notice_pinned
                )

                is Outcome.Failure -> CrewErrorText.of(r.error)
            }
            _state.update { it.copy(message = message) }
            reloadKeeping()
        }
    }

    fun onDeleteAsk(item: PostList.Item) {
        if (item.canDelete) _state.update { it.copy(confirmDelete = item) }
    }

    fun onDeleteDismiss() = _state.update { it.copy(confirmDelete = null) }

    /** 글을 지우면 그 사진도 앨범에서 빠지므로 앨범을 비워 다음에 다시 읽게 한다. */
    fun onDeleteConfirm() {
        val item = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            val message = when (val r = crews.deletePost(item.postId)) {
                is Outcome.Success -> uiText(R.string.crew_home_post_deleted)
                is Outcome.Failure -> CrewErrorText.of(r.error)
            }
            _state.update { it.copy(message = message, album = if (it.isAlbum) it.album else null) }
            reloadKeeping()
        }
    }

    /** 고정·삭제 뒤 지금 받아 둔 만큼 다시 읽는다(이어 받았던 뒤쪽이 사라지지 않게, A4). */
    private suspend fun reloadKeeping() {
        moreJob?.cancel()
        val s = _state.value
        if (s.isAlbum) loadAlbum() else reload(s.filter, loaded = s.posts?.posts?.size ?: 0)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private companion object {
        const val PHOTO_CACHE = 12
        const val LOAD_FACTOR = 0.75f

        /** 전체 칩에서 빈 쪽을 이어 읽는 횟수와 그때 채울 글 수. */
        const val FILL_PAGES_MAX = 3
        const val FILL_MIN = 5

        /** 응원 칸이 받는 글자 수(넘으면 자른다). */
        const val CHEER_INPUT_MAX = CrewBoardUiState.CHEER_MAX
    }
}

private fun BoardFilter.showsPinned(): Boolean = this == BoardFilter.ALL || this == BoardFilter.NOTICE

/** 게시판에 보이는 종류만 남긴다(전체 칩이 받은 크루런·대면 모임은 일정 탭에 있다). */
private fun PostList.boardOnly(): PostList = copy(posts = posts.filter { it.kind in BoardFilter.BOARD_KINDS })

/** 앨범 다음 쪽을 이어 붙인다(이미 받은 사진은 빼고). */
private fun Album?.append(next: Album): Album {
    val current = this ?: return next
    val known = current.photos.map { it.photoId }.toSet()
    return Album(current.photos + next.photos.filterNot { it.photoId in known }, next.nextCursor)
}
