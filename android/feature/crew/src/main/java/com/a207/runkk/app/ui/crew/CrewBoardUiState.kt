package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.codePointLength
import com.a207.runkk.app.domain.crew.Album
import com.a207.runkk.app.domain.crew.PostList
import java.time.Instant

/**
 * 게시판 탭(와이어프레임 E3·M6, FR-205·FR-206·FR-129·FR-211·FR-212) 상태. [filter] 칩의 글을 최신순으로 [posts]에 담고, 전체·공지 칩에서는
 * 고정 공지([pinned], 최대 3)를 맨 위에 둔다. [headcount]는 투표 응답 막대의 분모, [canManage]는 공지 쓰기·고정 단추를 보일지,
 * [now]는 "2시간 전" 같은 상대 시각의 기준이다. [viewer]는 크게 보고 있는 사진 ID다.
 */
data class CrewBoardUiState(
    val crewId: Long = 0,
    val filter: BoardFilter = BoardFilter.ALL,
    val posts: PostList? = null,
    val pinned: List<PostList.Item> = emptyList(),
    val album: Album? = null,
    val headcount: Int? = null,
    val canManage: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val viewer: Long? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val confirmDelete: PostList.Item? = null,
    val cheerDraft: String = "",
    val isCheerBusy: Boolean = false,
    val message: UiText? = null,
    val error: UiText? = null
) {
    val isAlbum: Boolean get() = filter == BoardFilter.ALBUM

    /** 지금 보는 칩에 다음 쪽이 있는가. */
    val hasMore: Boolean get() = if (isAlbum) album?.nextCursor != null else posts?.nextCursor != null

    /** 보이는 글: 전체·공지 칩이면 고정 공지를 맨 위에 두고 목록에서는 뺀다. */
    val shownPosts: List<PostList.Item>?
        get() {
            val list = posts?.posts ?: return null
            if (filter != BoardFilter.ALL && filter != BoardFilter.NOTICE) return list
            val pinnedIds = pinned.map { it.postId }.toSet()
            return pinned + list.filterNot { it.postId in pinnedIds }
        }

    /** 응원 1~200자(앞뒤 공백 제외, FR-129, 코드 포인트 D-143). */
    val isCheerValid: Boolean get() = cheerDraft.isNotBlank() && cheerDraft.trim().codePointLength() <= CHEER_MAX

    companion object {
        const val CHEER_MAX = 200
        const val PIN_MAX = 3
    }
}
