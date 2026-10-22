package com.a207.runkk.policy;

/**
 * 코스 동일성 기준(설계 6.1.9).
 *
 * @param coverage 겹침 비율
 * @param radiusMeters 같은 점으로 보는 반경
 * @param compareCount 비교할 최근 기록 수
 */
public record CourseSimilarityRule(double coverage, int radiusMeters, int compareCount) {}
