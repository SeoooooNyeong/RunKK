package com.a207.runkk.app.api.crew

import kotlinx.serialization.Serializable

/** PUT /posts/{id}/attendance 요청(서버 CrewDtos.AttendanceRequest, D-99). answer는 GOING·NOT_GOING·UNDECIDED. */
@Serializable
internal data class AttendanceRequest(val answer: String)
