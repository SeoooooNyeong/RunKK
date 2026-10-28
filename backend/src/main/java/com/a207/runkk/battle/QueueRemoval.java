package com.a207.runkk.battle;

/**
 * 매칭 취소 시점의 대기 표 상태(설계 6.1.6, D-83). MatchQueue.removeIfWaiting이 상태 확인과 삭제를 원자 연산 한 번으로 하고 그 결과를 알려
 * 준다.
 */
public enum QueueRemoval {
    /** WAITING이라 지웠다. */
    REMOVED,
    /** 이미 배정되어(ASSIGNED) 지우지 않았다. */
    ASSIGNED,
    /** 표가 없다. */
    ABSENT
}
