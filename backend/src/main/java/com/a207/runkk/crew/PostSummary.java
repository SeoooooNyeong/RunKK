package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 게시글 목록 한 줄(자료사전 크루게시목록의 한 항목, CrewPost.summary, FR-133, FR-212). 작성자 표시명은 CrewPostService가 붙인다.
 * 종류별 정보는 그 종류의 글에만 있고 나머지는 null이다.
 *
 * @param postId 게시글 ID
 * @param kind 게시 종류
 * @param authorId 작성자, 탈퇴했으면 null("탈퇴한 사용자")
 * @param body 본문(투표는 질문)
 * @param writtenAt 작성 일시
 * @param mine 열람자가 쓴 글인가
 * @param crewRun 크루런 정보(CREW_RUN)
 * @param meetup 대면 모임 정보(MEETUP)
 * @param notice 공지 정보(NOTICE)
 * @param poll 투표 요약(POLL)
 * @param photoIds 게시글 사진 ID, seq 순(BOARD)
 */
public record PostSummary(
        long postId,
        PostKind kind,
        Long authorId,
        String body,
        LocalDateTime writtenAt,
        boolean mine,
        CrewRunInfo crewRun,
        MeetupInfo meetup,
        NoticeInfo notice,
        PollInfo poll,
        List<Long> photoIds) {

    public PostSummary {
        photoIds = photoIds == null ? null : List.copyOf(photoIds);
    }

    static PostSummary of(PostHeader header, boolean mine) {
        return new PostSummary(
                header.postId(),
                header.kind(),
                header.authorId(),
                header.body(),
                header.writtenAt(),
                mine,
                null,
                null,
                null,
                null,
                null);
    }

    PostSummary withCrewRun(CrewRunInfo run) {
        return new PostSummary(
                postId, kind, authorId, body, writtenAt, mine, run, null, null, null, null);
    }

    PostSummary withMeetup(MeetupInfo info) {
        return new PostSummary(
                postId, kind, authorId, body, writtenAt, mine, null, info, null, null, null);
    }

    PostSummary withNotice(NoticeInfo info) {
        return new PostSummary(
                postId, kind, authorId, body, writtenAt, mine, null, null, info, null, null);
    }

    PostSummary withPoll(PollInfo info) {
        return new PostSummary(
                postId, kind, authorId, body, writtenAt, mine, null, null, null, info, null);
    }

    PostSummary withPhotos(List<Long> ids) {
        return new PostSummary(
                postId, kind, authorId, body, writtenAt, mine, null, null, null, null, ids);
    }

    /**
     * 모집글 줄의 크루런 정보(FR-133).
     *
     * @param title 제목(W6)
     * @param distance 크루런 거리, 제한 없음이면 null
     * @param scheduledAt 모집희망일시
     * @param voteDeadline 참석 응답 마감
     * @param status 크루런 상태
     * @param counts 참석·불참·미정 인원
     * @param myAnswer 열람자 응답, 없으면 null
     * @param finisherCount 완주 인원(지난 크루런)
     * @param linkedRecordIds 연결된 기록(크루 반영 거리 계산용)
     */
    public record CrewRunInfo(
            String title,
            StandardDistance distance,
            LocalDateTime scheduledAt,
            LocalDateTime voteDeadline,
            CrewRunStatus status,
            AttendanceCounts counts,
            AttendanceAnswer myAnswer,
            int finisherCount,
            Set<String> linkedRecordIds) {

        public CrewRunInfo {
            linkedRecordIds = Set.copyOf(linkedRecordIds);
        }
    }

    /**
     * 대면 모임 줄(FR-132, FR-133).
     *
     * @param scheduledAt 일정일시
     * @param placeName 장소명
     * @param capacity 정원(작성자 포함)
     * @param counts 참석·불참·미정 인원
     * @param myAnswer 열람자 응답, 없으면 null
     */
    public record MeetupInfo(
            LocalDateTime scheduledAt,
            String placeName,
            int capacity,
            AttendanceCounts counts,
            AttendanceAnswer myAnswer) {}

    /**
     * 공지 줄(FR-205).
     *
     * @param pinned 상단 고정
     */
    public record NoticeInfo(boolean pinned) {}

    /**
     * 투표 줄(FR-207). 마감 여부는 조회 시각으로 정하므로 closesAt으로 판단한다.
     *
     * @param method 투표 방식
     * @param closesAt 마감 일시
     * @param responseCount 응답 수
     * @param myOptionNo 열람자 선택, 없으면 null
     */
    public record PollInfo(
            PollMethod method, LocalDateTime closesAt, int responseCount, Integer myOptionNo) {

        /** now ≥ closesAt이면 마감(정각 포함, D-165). */
        public boolean closedAt(LocalDateTime now) {
            return !now.isBefore(closesAt);
        }
    }
}
