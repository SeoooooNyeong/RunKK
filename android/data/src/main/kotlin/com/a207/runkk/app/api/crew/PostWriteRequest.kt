package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/**
 * POST /crews/{id}/posts 요청(서버 CrewDtos.PostWriteRequest, F.2.2). 종류마다 쓰는 항목이 다르다: CHEER는 body, CREW_RUN은
 * body·distanceMeters·scheduledAt(투표 마감 voteDeadline은 비우면 모집 일시), MEETUP은 body·scheduledAt·placeName·capacity,
 * NOTICE는 body·pinned, POLL은 body(질문)·pollMethod·options(CHOICES만)·closesAt. BOARD는 board-posts(multipart)로 쓴다.
 */
@Serializable
internal data class PostWriteRequest(
    val kind: String,
    val body: String? = null,
    val distanceMeters: Long? = null,
    val scheduledAt: String? = null,
    val voteDeadline: String? = null,
    val placeName: String? = null,
    val capacity: Int? = null,
    val pinned: Boolean? = null,
    val pollMethod: String? = null,
    val options: List<String>? = null,
    val closesAt: String? = null
)
