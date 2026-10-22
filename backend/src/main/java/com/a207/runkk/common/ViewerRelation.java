package com.a207.runkk.common;

/** 기록 열람자와 기록 주인의 관계. RecordQueryService가 크루 소속을 보고 정해 넘긴다(설계 6.1.1). */
public enum ViewerRelation {
    OWNER,
    SAME_CREW,
    OTHER
}
