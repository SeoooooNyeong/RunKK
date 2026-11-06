package com.a207.runkk.battle;

/**
 * 배치고사 대결 한 판의 셈 상태(05-03 5.7.3.1, D-487). COUNTED = 판으로 셈, NOT_COUNTED = 세지 않음(포기, 무효 기록, 기록 없이
 * 만료, 취소, 배치고사가 이미 끝난 뒤 판정), PENDING = 아직 모름(판정 전, 기록 도착 전, 셈 반영 전).
 */
public enum PlacementRoundState {
    COUNTED,
    NOT_COUNTED,
    PENDING;

    /**
     * 위에서 처음 맞는 것(D-487, v0.31 R89): 이미 셈 → COUNTED, 세지 않는 판(check.isVoid) → NOT_COUNTED, 끝났는데
     * 배치고사가 이미 끝남 → NOT_COUNTED, 그 밖(판정 전, 기록 도착 전, 셈 반영 전) → PENDING. 배치고사 대결이 아니면 부르지 않는다.
     */
    public static PlacementRoundState of(
            PlacementRoundCheck check, boolean counted, boolean progressCompleted) {
        if (counted) {
            return COUNTED;
        }
        if (check.isVoid()) {
            return NOT_COUNTED;
        }
        if (check.finished() && progressCompleted) {
            return NOT_COUNTED;
        }
        return PENDING;
    }
}
