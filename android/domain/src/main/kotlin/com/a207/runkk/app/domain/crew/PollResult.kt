package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 투표 결과(FR-206): 선택지별 수와 응답자(익명 아님). */
data class PollResult(
    val postId: Long,
    val question: String,
    val method: PollMethod,
    val closesAt: Instant,
    val isClosed: Boolean,
    val options: List<Option>,
    val myOptionNo: Int?
) {
    data class Option(val optionNo: Int, val label: String, val count: Int, val respondents: List<Respondent>)

    /** [memberId]는 탈퇴했으면 null이다. */
    data class Respondent(val memberId: Long?, val displayName: String)
}
