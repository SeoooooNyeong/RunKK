package com.a207.runkk.app.domain.runbody

/** 분석 작업 상태(서버 RunBodyStatus, D-441). PENDING은 제출 전, ANALYZING은 결과 대기다. */
enum class RunBodyStatus {
    PENDING,
    ANALYZING,
    DONE,
    FAILED;

    /** 아직 결과를 기다리는 상태. */
    val isWaiting: Boolean get() = this == PENDING || this == ANALYZING
}
