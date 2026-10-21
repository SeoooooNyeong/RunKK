package com.a207.runkk.battle;

import java.util.List;
import java.util.Optional;

/**
 * 매칭 대기열 포트(설계 6.1.6, D-07). 구현은 infra.battle의 RedisMatchQueue(기본)와 JdbcMatchQueue(runkk.queue=jdbc
 * 대안)다. battle 밖에서 쓰지 않는다(ArchUnit 규칙 4).
 */
public interface MatchQueue {

    /** memberId 키로 없을 때만 넣는다(Redis SETNX, PostgreSQL 기본 키). 이미 있으면 false(FR-080). */
    boolean enqueue(MatchTicket ticket);

    /** 지웠으면 true. */
    boolean remove(long memberId);

    /** WAITING일 때만 원자적으로 지운다(취소, FR-085). 이미 배정됐거나 없으면 false. */
    boolean removeWaiting(long memberId);

    /** 회원의 대기열 표(WAITING 또는 배정 직후의 ASSIGNED). 없으면 비어 있다. */
    Optional<MatchTicket> findWaiting(long memberId);

    /** WAITING 표, 대기 시작이 이른 순. */
    List<MatchTicket> waitingOldestFirst();

    /** 두 표가 모두 WAITING일 때만 둘 다 ASSIGNED로 바꾼다. 원자적(FR-082, A-22). */
    boolean tryAssignPair(long a, long b);
}
