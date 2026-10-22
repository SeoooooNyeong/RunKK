package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 게시글 목록 한 줄(자료사전 크루게시목록의 한 항목, CrewPost.summary). 작성자 표시명은 CrewPostService가 붙인다.
 *
 * @param postId 게시글 ID
 * @param kind 게시 종류
 * @param authorId 작성자, 탈퇴했으면 null("탈퇴한 사용자")
 * @param body 본문
 * @param writtenAt 작성 일시
 * @param mine 열람자가 쓴 글인가
 * @param crewRun 크루런 모집글이면 크루런 정보, 응원글이면 null
 */
public record PostSummary(
        long postId,
        PostKind kind,
        Long authorId,
        String body,
        LocalDateTime writtenAt,
        boolean mine,
        CrewRunInfo crewRun) {

    /**
     * 모집글 줄의 크루런 정보(FR-133).
     *
     * @param distance 크루런 거리
     * @param scheduledAt 모집희망일시
     * @param voteDeadline 투표 마감
     * @param status 크루런 상태
     * @param goingCount 갈래요 인원
     * @param myAnswer 열람자 응답, 없으면 null
     * @param finisherCount 완주 인원(지난 크루런)
     * @param linkedRecordIds 연결된 기록(크루 반영 거리 계산용)
     */
    public record CrewRunInfo(
            StandardDistance distance,
            LocalDateTime scheduledAt,
            LocalDateTime voteDeadline,
            CrewRunStatus status,
            int goingCount,
            VoteAnswer myAnswer,
            int finisherCount,
            Set<String> linkedRecordIds) {

        public CrewRunInfo {
            linkedRecordIds = Set.copyOf(linkedRecordIds);
        }
    }
}
