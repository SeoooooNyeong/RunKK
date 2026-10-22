package com.a207.runkk.analysis;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;

/**
 * 순위를 매기기 전 점수 하나(설계 6.1.4 RankingSnapshot.build의 입력).
 *
 * @param subjectId 회원 또는 크루 ID
 * @param name 표시명
 * @param tier 티어(크루는 null)
 * @param distance 집계 거리
 */
public record RankScore(long subjectId, DisplayName name, Tier tier, Distance distance) {}
