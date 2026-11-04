package com.a207.runkk.battle;

/**
 * 배치고사 대결 한 판의 셈 상태(05-03 5.7.3.1, D-487). COUNTED = 판으로 셈, NOT_COUNTED = 세지 않음(포기, 무효 기록, 기록 없이
 * 만료, 취소, 배치고사가 이미 끝난 뒤 판정), PENDING = 아직 모름(판정 전, 기록 도착 전, 셈 반영 전).
 */
public enum PlacementRoundState {
    COUNTED,
    NOT_COUNTED,
    PENDING
}
