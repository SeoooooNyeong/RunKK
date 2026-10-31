package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
 * @param shownGenders 개인 랭킹 줄 회원의 표시 성별 이름(MALE·FEMALE, D-327·D-336). 스냅숏에 저장하지 않고 조회 때 붙인다(D-334).
 *     크루 랭킹은 비어 있다
 */
public record RankingView(
        RankingKind kind,
        AggregationPeriod period,
        LocalDateTime aggregatedAt,
        List<RankEntry> entries,
        RankEntry mine,
        Distance gapToNext,
        String notice,
        Map<Long, String> shownGenders) {

    public RankingView {
        shownGenders = shownGenders == null ? Map.of() : Map.copyOf(shownGenders);
    }

    /** 그 줄 회원의 표시 성별 이름, 크루 줄이거나 모르면 null. */
    public String shownGenderOf(RankEntry entry) {
        return shownGenders.get(entry.subjectId());
    }
}
