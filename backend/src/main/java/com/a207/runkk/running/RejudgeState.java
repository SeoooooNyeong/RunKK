package com.a207.runkk.running;

/**
 * v0.10 일회성 재판정 표시(V28, RunRejudgeService). V28이 배포 시점의 무효 기록에 PENDING을 세운다. 재판정이 유효로 바꾸면 VALID(부수
 * 처리 대기), 무효 그대로면 DONE, 경로가 없어(파기·미수집) 다시 판정할 수 없으면 SKIPPED다. VALID는 부수 처리(티어·리워드·배경) 뒤 DONE이 된다.
 * 새로 받은 기록에는 표시가 없다(null).
 */
public enum RejudgeState {
    PENDING,
    VALID,
    DONE,
    SKIPPED
}
