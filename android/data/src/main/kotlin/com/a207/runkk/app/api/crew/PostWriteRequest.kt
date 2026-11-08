package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/**
 * POST /crews/{id}/posts 요청(서버 CrewDtos.PostWriteRequest, F.2.2). 앱은 크루런 모집(CREW_RUN: body·distanceMeters·scheduledAt)만
 * 쓴다. 투표 마감 voteDeadline은 보내지 않아 서버가 모집 일시로 둔다. 서버 record의 대면 모임·공지·투표 항목은 앱에서 쓰지 않아 두지 않는다.
 */
@Serializable
internal data class PostWriteRequest(
    val kind: String,
    val body: String? = null,
    val distanceMeters: Long? = null,
    val scheduledAt: String? = null
)
