package com.a207.runkk.crew;

/** 열람자와 크루의 관계(srs.md 3.0.3 권한표의 열: 크루장 | 운영진 | 크루원 | 크루 밖, D-98). */
public enum CrewRelation {
    LEADER,
    MANAGER,
    MEMBER,
    OUTSIDER
}
