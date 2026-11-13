package com.a207.runkk.app.domain.crew

/**
 * 일정(크루런·대면 모임) 참석 응답(서버 crew.AttendanceAnswer, FR-145). v0.10 VoteAnswer를 이름을 바꾸고 UNDECIDED를 더했다(D-99).
 * "투표"는 PollPost만 뜻한다. 피그마 맞춤 W6부터 앱은 참석하기에 GOING만 보내고 참석 취소는 응답을 거둔다(withdrawAttendance).
 * NOT_GOING·UNDECIDED는 옛 앱이 남긴 응답을 읽을 때만 온다.
 */
enum class AttendanceAnswer {
    GOING,
    NOT_GOING,
    UNDECIDED
}
