package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** 초대 코드로 찾은 크루 요약(서버 CrewDtos.InvitePreviewResponse, 피그마 F-03c). */
@Serializable
internal data class InvitePreviewResponse(
    val crewId: Long,
    val name: String,
    val intro: String? = null,
    val tags: Set<String> = emptySet(),
    val logoUrl: String? = null,
    val visibility: String,
    val joinPolicy: String,
    val headcount: Int,
    val createdAt: String
)
