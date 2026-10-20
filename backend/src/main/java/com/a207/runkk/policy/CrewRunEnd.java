package com.a207.runkk.policy;

/**
 * CREW_RUN_END_RULE 값의 형식(1단계 리뷰 L4, 설계 갱신 요청 U2). policy 안에서만 쓴다.
 *
 * @param minutesPerKm 종료 기준 km당 분
 */
record CrewRunEnd(int minutesPerKm) {}
