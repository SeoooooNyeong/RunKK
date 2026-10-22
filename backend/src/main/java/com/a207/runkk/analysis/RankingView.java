package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 랭킹 조회 결과(설계 6.1.4 RankingService, FR-070, FR-071). 스냅숏이 아직 없으면 aggregatedAt이 null이고 entries가 비어
 * 있다.
 *
 * @param kind 개인·크루
 * @param period 주간·월간
 * @param aggregatedAt 집계 기준 시각(N-20)
 * @param entries 상위 줄(순위순, 최대 RankingService.TOP)
 * @param mine 본인(또는 본인 크루) 줄, 없으면 null
 * @param gapToNext 바로 위 순위까지 남은 거리, 없으면 null
 * @param notice 안내 문구(나만 보기, 크루 없음), 없으면 null
 */
public record RankingView(
        RankingKind kind,
        AggregationPeriod period,
        LocalDateTime aggregatedAt,
        List<RankEntry> entries,
        RankEntry mine,
        Distance gapToNext,
        String notice) {}
