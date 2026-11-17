package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CrewRunPost.depart 결과(6.2 A-13, 설계 05-32 [임시:D-성-881]~[임시:D-성-883]).
 *
 * @param kind 예약·출발·취소·해당 없음
 * @param startAt 예약·출발이면 측정 시작 절대 시각(예약 시각 + 3초)
 * @param memberIds 예약이면 출발 후보, 출발이면 진행 참가자, 취소면 갈래요 응답자
 * @param notifiedIds 출발이면 출발 사실을 받을 사람(그때 대기실 입장자 전원과 진행 참가자), 그 밖은 빈 목록
 * @param countdownToken 예약·출발이면 예약 식별값, 아니면 null
 */
public record DepartureOutcome(
        Kind kind,
        LocalDateTime startAt,
        List<Long> memberIds,
        List<Long> notifiedIds,
        String countdownToken) {

    /** 결과 종류. */
    public enum Kind {
        NONE,
        PLANNED,
        STARTED,
        CANCELLED
    }

    static final DepartureOutcome NONE =
            new DepartureOutcome(Kind.NONE, null, List.of(), List.of(), null);

    public DepartureOutcome {
        memberIds = List.copyOf(memberIds);
        notifiedIds = List.copyOf(notifiedIds);
    }

    static DepartureOutcome planned(LocalDateTime startAt, List<Long> candidates, String token) {
        return new DepartureOutcome(Kind.PLANNED, startAt, candidates, List.of(), token);
    }

    static DepartureOutcome started(
            LocalDateTime startAt, List<Long> runners, List<Long> notified, String token) {
        return new DepartureOutcome(Kind.STARTED, startAt, runners, notified, token);
    }

    static DepartureOutcome cancelled(List<Long> going) {
        return new DepartureOutcome(Kind.CANCELLED, null, going, List.of(), null);
    }
}
