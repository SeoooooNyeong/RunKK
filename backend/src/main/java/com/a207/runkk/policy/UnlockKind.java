package com.a207.runkk.policy;

/** 상점 아이템 해금 조건 종류(W8, S15P31A207-299, 사용자 결정 A1). 판정은 reward.ShopService가 조회 때마다 한다. */
public enum UnlockKind {
    /** 누적 유효 거리 ≥ threshold(m). */
    TOTAL_DISTANCE,
    /** 최장 유효 기록 거리 ≥ threshold(m). 하프 완주는 21097. */
    LONGEST_RUN,
    /** 유효 러닝 수 ≥ threshold. */
    RUN_COUNT
}
