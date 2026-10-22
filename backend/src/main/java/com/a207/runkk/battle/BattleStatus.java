package com.a207.runkk.battle;

import java.util.Set;

/**
 * 대결 상태(설계 2.4.2). 활성 = WAITING_START, IN_PROGRESS, COUNTDOWN. CANCELLED는 출발하지 못한 대결(시작 보고 없이 버려진
 * 기록 대결, 출발 시각이 지나도 시작하지 못한 대결)의 끝 상태다(활성 대결 1개 불변식 결정). 판정·이력·전적에 없다. 상태도 2.4.2 갱신 요청.
 */
public enum BattleStatus {
    WAITING_START,
    IN_PROGRESS,
    COUNTDOWN,
    RESULT_PENDING,
    FINISHED,
    CANCELLED;

    private static final Set<BattleStatus> ACTIVE = Set.of(WAITING_START, IN_PROGRESS, COUNTDOWN);
    private static final Set<BattleStatus> RUNNING = Set.of(IN_PROGRESS, COUNTDOWN);

    public static Set<BattleStatus> active() {
        return ACTIVE;
    }

    /** 러너가 달리는 중일 수 있는 상태. */
    public boolean isRunning() {
        return RUNNING.contains(this);
    }

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
