package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/**
 * 서버 CrewDtos.PostListResponse. 본문은 body이고, 종류에 따라 crewRun(크루런 모집)·meetup(대면 모임)·notice(공지)·poll(투표)·
 * photos(게시판 글 사진)가 함께 온다. canDelete는 본인 글이거나 운영진 이상이다(FR-213). nextCursor는 끝이면 null이다.
 */
@Serializable
internal data class PostListResponse(val posts: List<Item> = emptyList(), val nextCursor: Long? = null) {
    @Serializable
    data class Item(
        val postId: Long,
        val kind: String,
        val authorName: String? = null,
        val body: String? = null,
        val writtenAt: String,
        val mine: Boolean = false,
        val crewRun: CrewRun? = null,
        val canDelete: Boolean = false,
        val meetup: Meetup? = null,
        val notice: Notice? = null,
        val poll: Poll? = null,
        val photos: List<Photo>? = null
    ) {
        /** 대면 모임(서버 PostListResponse.Meetup). myAnswer는 GOING·NOT_GOING·UNDECIDED, 답하지 않았으면 null. */
        @Serializable
        data class Meetup(
            val scheduledAt: String,
            val placeName: String,
            val capacity: Int? = null,
            val goingCount: Int = 0,
            val notGoingCount: Int = 0,
            val undecidedCount: Int = 0,
            val myAnswer: String? = null
        )

        @Serializable
        data class Notice(val pinned: Boolean = false)

        /** 투표(서버 PostListResponse.Poll). method는 YES_NO·CHOICES. */
        @Serializable
        data class Poll(
            val method: String,
            val closesAt: String,
            val closed: Boolean = false,
            val responseCount: Int = 0,
            val myOptionNo: Int? = null
        )

        @Serializable
        data class Photo(val photoId: Long, val url: String)

        /**
         * 크루런 모집글의 크루런 정보(서버 PostListResponse.CrewRun). myAnswer는 GOING·NOT_GOING·UNDECIDED, 답하지 않았으면 null.
         * distanceMeters가 null이면 제한 없음, title은 크루런 제목(W6, 옛 서버면 null)이다.
         */
        @Serializable
        data class CrewRun(
            val distanceMeters: Long? = null,
            val scheduledAt: String,
            val voteDeadline: String? = null,
            val status: String? = null,
            val goingCount: Int = 0,
            val myAnswer: String? = null,
            val finisherCount: Int = 0,
            val raceContributionMeters: Long? = null,
            val notGoingCount: Int = 0,
            val undecidedCount: Int = 0,
            val title: String? = null
        )
    }
}
