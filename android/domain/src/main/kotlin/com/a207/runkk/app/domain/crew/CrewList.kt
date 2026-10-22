package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 목록·추천(서버 crew.CrewListView·RecommendationView). [nextCursor]가 null이면 마지막 쪽이다. */
data class CrewList(val crews: List<Item>, val nextCursor: Long? = null) {
    data class Item(
        val crewId: Long,
        val name: String,
        val intro: String?,
        val tags: Set<String>,
        val leaderName: String,
        val headcount: Int,
        val weeklyDistance: Distance,
        val joinPolicy: JoinPolicy
    )
}
