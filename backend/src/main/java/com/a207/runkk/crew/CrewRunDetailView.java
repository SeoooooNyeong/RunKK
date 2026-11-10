package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 일정 상세(피그마 F-13·F-11d, W6). 참석자는 GOING인 참여이고 작성자가 참석 중이면 맨 앞, 나머지는 응답 순이다.
 *
 * @param postId 게시글 ID
 * @param crewId 크루 ID
 * @param title 제목
 * @param body 내용
 * @param authorId 작성자, 탈퇴했으면 null
 * @param authorName 작성자 표시명(탈퇴면 "탈퇴한 사용자")
 * @param mine 열람자가 쓴 글
 * @param writtenAt 작성 일시
 * @param scheduledAt 일시
 * @param voteDeadline 참석 응답 마감
 * @param distance 거리, 제한 없음이면 null
 * @param status 크루런 상태
 * @param goingCount 참석 인원
 * @param myAnswer 열람자 응답, 없으면 null
 * @param canEdit 열람자가 고칠 수 있다(작성자이고 VOTING이고 일시 전)
 * @param canDelete 열람자가 지울 수 있다(작성자 또는 크루장·운영진)
 * @param repeat 진행 중인 반복의 최신 회차면 WEEKLY
 * @param repeatDayOfWeek 반복 요일, NONE이면 null
 * @param attendees 참석자
 */
public record CrewRunDetailView(
        long postId,
        long crewId,
        String title,
        String body,
        Long authorId,
        DisplayName authorName,
        boolean mine,
        LocalDateTime writtenAt,
        LocalDateTime scheduledAt,
        LocalDateTime voteDeadline,
        StandardDistance distance,
        CrewRunStatus status,
        int goingCount,
        AttendanceAnswer myAnswer,
        boolean canEdit,
        boolean canDelete,
        CrewRunRepeat repeat,
        DayOfWeek repeatDayOfWeek,
        List<Attendee> attendees) {

    public CrewRunDetailView {
        attendees = List.copyOf(attendees);
    }

    /**
     * 참석자 한 명.
     *
     * @param memberId 회원 ID, 탈퇴했으면 null
     * @param name 표시명
     * @param author 작성자
     * @param me 열람자 본인
     * @param shownGender 표시 성별(MALE·FEMALE), 없으면 null
     */
    public record Attendee(
            Long memberId, DisplayName name, boolean author, boolean me, String shownGender) {}
}
