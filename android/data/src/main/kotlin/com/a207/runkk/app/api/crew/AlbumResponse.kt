package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /crews/{id}/album 응답(서버 CrewDtos.AlbumResponse). 최신순, nextCursor는 끝이면 null. */
@Serializable
internal data class AlbumResponse(val photos: List<Photo> = emptyList(), val nextCursor: Long? = null) {
    @Serializable
    data class Photo(val photoId: Long, val postId: Long, val url: String, val postedAt: String)
}
