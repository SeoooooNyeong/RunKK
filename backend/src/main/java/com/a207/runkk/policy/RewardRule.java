package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/**
 * 리워드 적립량 규칙(설계 6.1.9).
 *
 * @param perKm km당 적립량
 * @param battleWin 대결 승리 적립량
 * @param dailyCap 하루 상한
 */
public record RewardRule(int perKm, int battleWin, int dailyCap) {

    /** ⌊km × perKm⌋. */
    public int amountForRun(Distance d) {
        return (int) (d.meters() * perKm / 1000);
    }
}
