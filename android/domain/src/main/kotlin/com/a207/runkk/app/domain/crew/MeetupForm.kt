package com.a207.runkk.app.domain.crew

import java.time.Instant

/** 대면 모임 일정 쓰기 입력(FR-207). [capacity]는 작성자 포함 정원이고 null이면 크루 정원이다. */
data class MeetupForm(val body: String, val scheduledAt: Instant, val placeName: String, val capacity: Int?)
