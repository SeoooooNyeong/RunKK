package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** POST /crews/join·DELETE /join-requests/{id} 결과(서버 CrewDtos.JoinResponse). status는 JOINED·PENDING·CANCELLED. */
@Serializable
internal data class JoinResponse(val status: String, val crewId: Long, val requestId: Long? = null)
