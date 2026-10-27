package com.a207.runkk.app.ui.crew

import androidx.annotation.StringRes
import com.a207.runkk.app.domain.crew.PostList

/** 글 종류 이름(게시판 꼬리표, 신고 시트 대상 한 줄). 크루런·대면 모임은 일정이다. */
@StringRes
internal fun PostList.Kind.kindLabelRes(): Int = when (this) {
    PostList.Kind.CHEER -> R.string.crew_kind_cheer
    PostList.Kind.BOARD -> R.string.crew_kind_board
    PostList.Kind.NOTICE -> R.string.crew_kind_notice
    PostList.Kind.POLL -> R.string.crew_kind_poll
    PostList.Kind.CREW_RUN, PostList.Kind.MEETUP -> R.string.crew_kind_schedule
}
