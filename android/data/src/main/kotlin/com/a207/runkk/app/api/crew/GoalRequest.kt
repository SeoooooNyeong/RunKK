package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** PUT /crews/{id}/goal 요청(서버 CrewDtos.GoalRequest, 1,000m 이상). */
@Serializable
internal data class GoalRequest(val goalMeters: Long)
