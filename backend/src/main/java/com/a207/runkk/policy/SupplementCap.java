package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/**
 * 보충 추천 거리 상한(설계 6.1.9).
 *
 * @param cap 상한 거리
 */
public record SupplementCap(Distance cap) {}
