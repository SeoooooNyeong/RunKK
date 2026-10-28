package com.a207.runkk.policy;

/**
 * 누적 거리의 랜드마크 표기(FR-051, FR-077, 6.2 A-18).
 *
 * @param stage 기준 랜드마크의 단계
 * @param name 기준 랜드마크 이름
 * @param label 화면 문구
 */
public record LandmarkProgress(LandmarkStage stage, String name, String label) {}
