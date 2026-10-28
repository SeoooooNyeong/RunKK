package com.a207.runkk.audit;

/** 감사 기록 행위자 역할(설계 1.2.11). */
public enum ActorRole {
    RUNNER,
    CREW_LEADER,
    /** 운영진이 남의 콘텐츠를 지운 감사의 행위자 역할(v0.11, FR-213, D-98). */
    CREW_MANAGER,
    OPERATOR,
    SYSTEM
}
