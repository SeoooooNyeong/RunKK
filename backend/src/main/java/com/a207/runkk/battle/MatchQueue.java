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

    /**
     * 취소 전용 원자 연산(FR-085, D-83). 표가 WAITING이면 지우고 REMOVED, ASSIGNED면 지우지 않고 ASSIGNED, 표가 없으면
     * ABSENT. 상태 확인과 삭제를 한 번에 해 고스트 시작(tryAssign)과 겹쳐도 한쪽만 이긴다.
     */
    QueueRemoval removeIfWaiting(long memberId);

    /** 회원의 대기열 표(WAITING 또는 배정 직후의 ASSIGNED). 없으면 비어 있다. */
    Optional<MatchTicket> findWaiting(long memberId);

    /** WAITING 표, 대기 시작이 이른 순. */
    List<MatchTicket> waitingOldestFirst();

    /** 두 표가 모두 WAITING일 때만 둘 다 ASSIGNED로 바꾼다. 원자적(FR-082, A-22). */
    boolean tryAssignPair(long a, long b);

    /** 표가 WAITING일 때만 ASSIGNED로 바꾼다. 고스트 매칭 동안 취소·짝 배정을 막는다. 원자적(D-64). */
    boolean tryAssign(long memberId);

    /** ASSIGNED 표를 WAITING으로 되돌린다(waitingSince 유지). 대결 저장·고스트 매칭이 실패했을 때만 부른다(D-64). */
    void release(long memberId);
}
