package com.a207.runkk.member;

import com.a207.runkk.common.Tier;

/** 배치고사 판 한 번을 센 결과(05-21 2.1, A-37). */
public sealed interface PlacementCount
        permits PlacementCount.Provisional, PlacementCount.Confirmed, PlacementCount.Ignored {

    /**
     * 1·2판을 세어 임시 티어가 정해졌다.
     *
     * @param round 센 판 번호
     * @param tier 임시 티어
     */
    record Provisional(int round, Tier tier) implements PlacementCount {}

    /**
     * 3판을 세어 티어가 확정됐다.
     *
     * @param tier 확정 티어
     */
    record Confirmed(Tier tier) implements PlacementCount {}

    /**
     * 세지 않았다(완료, 이미 셈, 진행 없음, 탈퇴, 이미 랭크 티어).
     *
     * @param reason 운영 로그용 사유
     */
    record Ignored(String reason) implements PlacementCount {}
}
