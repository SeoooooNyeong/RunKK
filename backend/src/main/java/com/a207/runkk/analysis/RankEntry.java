package com.a207.runkk.analysis;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;

/**
 * 랭킹표 한 줄(설계 6.1.4 RankEntry).
 *
 * @param rank 순위(같은 거리는 같은 순위)
 * @param subjectId 회원 또는 크루 ID
 * @param displayName 표시명(크루 랭킹은 크루명)
 * @param tier 티어(크루 랭킹은 null)
 * @param distance 집계 거리(크루는 1인 평균)
 */
public record RankEntry(
        int rank, long subjectId, DisplayName displayName, Tier tier, Distance distance) {}
