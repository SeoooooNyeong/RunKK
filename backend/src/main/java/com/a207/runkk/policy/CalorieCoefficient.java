package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/**
 * 거리로 소모 열량을 추정하는 계수(설계 6.1.9, FR-079).
 *
 * @param kcalPerKm km당 kcal
 */
public record CalorieCoefficient(int kcalPerKm) {

    /** round(kcalPerKm × km). */
    public int estimate(Distance d) {
        return (int) Math.round(kcalPerKm * d.exactKm());
    }
}
