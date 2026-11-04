package com.a207.runkk.member;

import com.a207.runkk.common.Tier;
import java.util.List;

/**
 * 본인 배치고사 진행(05-21 2.1, D-488, 05-03 5.7.3.3 P1). 임시 티어는 이 값에만 담는다(D-485).
 *
 * @param status 진행 상태
 * @param countedRounds 센 판 수(EXEMPT는 0)
 * @param nextRound IN_PROGRESS면 다음 판 번호, 아니면 null
 * @param provisionalTier IN_PROGRESS이고 한 판 이상 셌으면 임시 티어, 아니면 null
 * @param tier 회원 티어(공개 티어)
 * @param rounds 센 판(round 오름차순)
 */
public record PlacementView(
        PlacementStatus status,
        int countedRounds,
        Integer nextRound,
        Tier provisionalTier,
        Tier tier,
        List<PlacementRound> rounds) {

    public PlacementView {
        rounds = List.copyOf(rounds);
    }
}
