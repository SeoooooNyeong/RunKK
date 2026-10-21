package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CrewRunPost.depart 결과(6.2 A-13).
 *
 * @param kind 출발·취소·해당 없음
 * @param startAt 출발이면 시작 일시(모집희망일시 + 3초)
 * @param memberIds 출발이면 진행 참가자(READY), 취소면 갈래요 응답자
 */
public record DepartureOutcome(Kind kind, LocalDateTime startAt, List<Long> memberIds) {

    /** 결과 종류. */
    public enum Kind {
        NONE,
        STARTED,
        CANCELLED
    }

    static final DepartureOutcome NONE = new DepartureOutcome(Kind.NONE, null, List.of());

    public DepartureOutcome {
        memberIds = List.copyOf(memberIds);
    }

    static DepartureOutcome started(LocalDateTime startAt, List<Long> runners) {
        return new DepartureOutcome(Kind.STARTED, startAt, runners);
    }

    static DepartureOutcome cancelled(List<Long> going) {
        return new DepartureOutcome(Kind.CANCELLED, null, going);
    }
}
