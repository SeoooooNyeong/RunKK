package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import com.a207.runkk.app.domain.crew.PostList

/**
 * 게시판 종류 칩(와이어프레임 E3·M6): 전체, 공지, 투표, 응원, 게시글, 앨범. [kind]는 서버에 물을 글 종류이고 전체는 null(모든 종류),
 * 앨범은 글 목록이 아니라 사진 모음이다.
 */
enum class BoardFilter(@StringRes val labelRes: Int, val kind: PostList.Kind?) {
    ALL(R.string.crew_board_filter_all, null),
    NOTICE(R.string.crew_kind_notice, PostList.Kind.NOTICE),
    POLL(R.string.crew_kind_poll, PostList.Kind.POLL),
    CHEER(R.string.crew_kind_cheer, PostList.Kind.CHEER),
    POST(R.string.crew_kind_board, PostList.Kind.BOARD),
    ALBUM(R.string.crew_board_filter_album, null);

    companion object {
        /** 게시판에 보이는 글 종류. 크루런·대면 모임은 일정 탭에 있다. */
        val BOARD_KINDS: Set<PostList.Kind> =
            setOf(PostList.Kind.NOTICE, PostList.Kind.POLL, PostList.Kind.CHEER, PostList.Kind.BOARD)
    }
}
