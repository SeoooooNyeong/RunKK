package com.a207.runkk.crew;

/**
 * 일정 참석 응답 결과(FR-145).
 *
 * @param postId 게시글 ID
 * @param myAnswer 본인 응답
 * @param goingCount 참석 인원
 * @param notGoingCount 불참 인원
 * @param undecidedCount 미정 인원
 */
public record AttendanceView(
        long postId,
        AttendanceAnswer myAnswer,
        int goingCount,
        int notGoingCount,
        int undecidedCount) {

    static AttendanceView of(long postId, AttendanceAnswer myAnswer, AttendanceCounts counts) {
        return new AttendanceView(
                postId, myAnswer, counts.going(), counts.notGoing(), counts.undecided());
    }
}
