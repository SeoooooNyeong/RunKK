package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 투표 글의 요약(FR-206): 방식, 마감 일시, 마감 여부, 응답 수, 내가 고른 선택지 번호(답하지 않았으면 null). */
data class Poll(
    val method: PollMethod,
    val closesAt: Instant,
    val isClosed: Boolean,
    val responseCount: Int,
    val myOptionNo: Int?
)
