package com.a207.runkk.app.domain.crew

/**
 * 일정(크루런·대면 모임) 참석 응답(서버 crew.AttendanceAnswer, FR-145). v0.10 VoteAnswer를 이름을 바꾸고 UNDECIDED를 더했다(D-99).
 * "투표"는 PollPost만 뜻한다.
 */
enum class AttendanceAnswer {
    GOING,
    NOT_GOING,
    UNDECIDED
}
