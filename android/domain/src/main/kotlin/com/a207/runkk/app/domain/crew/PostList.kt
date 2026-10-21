package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 게시판(서버 crew.PostListView, FR-131·FR-133). [nextCursor]가 null이면 마지막 쪽이다. */
data class PostList(val posts: List<Item>, val nextCursor: Long? = null) {
    enum class Kind {
        CHEER,
        CREW_RUN
    }

    data class Item(
        val postId: Long,
        val kind: Kind,
        val authorName: String,
        val summary: String,
        val writtenAt: Instant,
        val mine: Boolean,
        /** 크루런 모집글의 크루런 정보. 응원글이면 null이다. */
        val crewRun: CrewRunInfo? = null
    )

    /**
     * 모집글의 크루런 정보(FR-133): 거리·모집 일시·투표 마감·상태·참여 인원·내 응답·완주 인원·크루 반영 거리(지난 크루런).
     * [myAnswer]는 아직 답하지 않았으면 null, [status]는 앱이 모르는 상태면 null이다.
     */
    data class CrewRunInfo(
        val distance: Distance,
        val scheduledAt: Instant,
        val voteDeadline: Instant?,
        val status: CrewRunStatus?,
        val goingCount: Int,
        val myAnswer: VoteAnswer?,
        val finisherCount: Int,
        val raceContribution: Distance?
    )
}
