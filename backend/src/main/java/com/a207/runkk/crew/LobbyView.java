package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루런 대기실(자료사전 크루런대기실: 게시글ID + 모집희망일시 + 크루런거리 + 남은시간 + {표시명 + 준비상태}).
 *
 * @param postId 크루런 게시글 ID
 * @param scheduledAt 모집희망일시
 * @param distance 크루런 거리
 * @param remaining 시작까지 남은 시간(지났으면 0)
 * @param status 크루런 상태
 * @param participants 갈래요 참가자의 준비 상태
 */
public record LobbyView(
        long postId,
        LocalDateTime scheduledAt,
        StandardDistance distance,
        Duration remaining,
        CrewRunStatus status,
        List<Entry> participants) {

    public LobbyView {
        participants = List.copyOf(participants);
    }

    /**
     * 참가자 한 명.
     *
     * @param memberId 회원 ID
     * @param name 표시명
     * @param readiness 준비 상태
     */
    public record Entry(long memberId, DisplayName name, Readiness readiness) {}
}
