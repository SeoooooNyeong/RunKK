package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 대면 모임 일정(FR-207): 일시, 장소, 정원(없으면 null), 참석 응답 수 셋, 내 응답(답하지 않았으면 null). */
data class Meetup(
    val scheduledAt: Instant,
    val placeName: String,
    val capacity: Int?,
    val goingCount: Int,
    val notGoingCount: Int,
    val undecidedCount: Int,
    val myAnswer: AttendanceAnswer?
)
