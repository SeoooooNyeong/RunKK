package com.a207.runkk.battle;

import java.util.Set;

/** 대결 상태(설계 2.4.2). 활성 = WAITING_START, IN_PROGRESS, COUNTDOWN. */
public enum BattleStatus {
    WAITING_START,
    IN_PROGRESS,
    COUNTDOWN,
    RESULT_PENDING,
    FINISHED;

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
