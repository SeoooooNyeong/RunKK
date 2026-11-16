package com.a207.runkk.crew;

/** 게시글 목록 순서(설계 05-32 4.5). */
public enum PostOrder {
    /** 최신순(ID 내림차순, 기존 기본값). cursor는 앞 페이지 마지막 글 ID. */
    NEWEST,
    /**
     * 크루런 예정일시 오름차순, 같은 일시는 ID 오름차순(종류가 CREW_RUN 하나일 때만). cursor는 앞 페이지 마지막 글 ID이고 서버가 그 글의 일시와 ID로
     * 이어 읽는다. 그 글을 찾을 수 없으면 400.
     */
    SCHEDULED_ASC
}
