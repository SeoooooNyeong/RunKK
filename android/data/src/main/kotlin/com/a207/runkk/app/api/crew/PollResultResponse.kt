package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** GET /posts/{id}/poll·PUT /posts/{id}/poll-response 응답(서버 CrewDtos.PollResultResponse). 응답자는 익명이 아니다(FR-207). */
@Serializable
internal data class PollResultResponse(
    val postId: Long,
    val question: String,
    val method: String,
    val closesAt: String,
    val closed: Boolean = false,
    val options: List<Option> = emptyList(),
    val myOptionNo: Int? = null
) {
    @Serializable
    data class Option(
        val optionNo: Int,
        val label: String,
        val count: Int = 0,
        val respondents: List<Respondent> = emptyList()
    )

    @Serializable
    data class Respondent(val memberId: Long? = null, val displayName: String)
}
