package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** PUT /posts/{id}/attendance 응답(서버 CrewDtos.AttendanceResponse). */
@Serializable
internal data class AttendanceResponse(
    val postId: Long,
    val myAnswer: String,
    val goingCount: Int = 0,
    val notGoingCount: Int = 0,
    val undecidedCount: Int = 0
)
