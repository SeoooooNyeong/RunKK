package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 크루 앨범(FR-212): 게시판 사진 최신순. [nextCursor]가 null이면 마지막 쪽이다. */
data class Album(val photos: List<Photo>, val nextCursor: Long?) {
    data class Photo(val photoId: Long, val postId: Long, val url: String, val postedAt: Instant)
}
