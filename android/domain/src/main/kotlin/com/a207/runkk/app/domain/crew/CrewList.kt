package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import java.time.Instant
import java.time.LocalDate

/** 크루 목록·추천(서버 crew.CrewListView·RecommendationView). [nextCursor]가 null이면 마지막 쪽이다. */
data class CrewList(
    val crews: List<Item>,
    val nextCursor: Long? = null,
    /** 같은 조건(키워드·태그)의 공개 크루 전체 수. 커서와 상관없다(피그마 F-01d "검색 결과 [n]개", contract-W4 2절). */
    val totalCount: Long = 0
) {
    data class Item(
        val crewId: Long,
        val name: String,
        val intro: String?,
        val tags: Set<String>,
        val leaderName: String,
        val headcount: Int,
        val weeklyDistance: Distance,
        val joinPolicy: JoinPolicy,
        /** 로고 주소(/api/v1/crew-logos/{id}), 없으면 기본 이미지. */
        val logoUrl: String? = null
    )
}
