package com.a207.runkk.runbody;

/** 런바디 분석 작업 상태(설계 05-19 2.2, 3.3 상태도). 앱 응답 status에 이 이름을 그대로 싣는다. */
public enum RunBodyStatus {
    /** 작업만 만들었고 ai에 아직 제출하지 않았다(또는 실패 뒤 재제출 대기). */
    PENDING,
    /** ai에 제출했고 결과를 기다린다. */
    ANALYZING,
    DONE,
    /** 재제출 3회 실패, 또는 ai가 받지 않는 요청(413·422·409). */
    FAILED
}
