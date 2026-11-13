package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** PUT·DELETE /posts/{id}/attendance 응답(서버 CrewDtos.AttendanceResponse). 참석 취소(DELETE) 뒤에는 myAnswer가 null이다. */
@Serializable
internal data class AttendanceResponse(
    val postId: Long,
    val myAnswer: String? = null,
    val goingCount: Int = 0,
    val notGoingCount: Int = 0,
    val undecidedCount: Int = 0
)
