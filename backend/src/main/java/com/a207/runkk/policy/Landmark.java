package com.a207.runkk.policy;

import com.a207.runkk.common.Distance;

/**
 * 랜드마크 하나(설계 6.1.9, srs.md 3.3.1).
 *
 * @param stage 단계
 * @param name 이름
 * @param distance 넘었다고 보는 누적 거리(왕복 랜드마크는 srs.md 3.3.1의 왕복 거리)
 * @param roundTrip 왕복 기준인가
 */
public record Landmark(LandmarkStage stage, String name, Distance distance, boolean roundTrip) {}
