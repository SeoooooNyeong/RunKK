package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
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
 * @param publicTiers 개인 랭킹 줄 회원의 지금 티어(공개 티어, UNRANKED = 배치 중). 스냅숏의 tier와 따로 조회 때 붙인다(05-21
 *     D-486). 크루 랭킹과 탈퇴 회원은 없다. 임시 티어는 넣지 않는다(D-485)
 */
public record RankingView(
        RankingKind kind,
        AggregationPeriod period,
        LocalDateTime aggregatedAt,
        List<RankEntry> entries,
        RankEntry mine,
        Distance gapToNext,
        String notice,
        Map<Long, String> shownGenders,
        Map<Long, Tier> publicTiers) {

    public RankingView {
        shownGenders = shownGenders == null ? Map.of() : Map.copyOf(shownGenders);
        publicTiers = publicTiers == null ? Map.of() : Map.copyOf(publicTiers);
    }

    /** 공개 티어 없이 만든 보기(크루 랭킹, 스냅숏 없음). */
    public RankingView(
            RankingKind kind,
            AggregationPeriod period,
            LocalDateTime aggregatedAt,
            List<RankEntry> entries,
            RankEntry mine,
            Distance gapToNext,
            String notice,
            Map<Long, String> shownGenders) {
        this(kind, period, aggregatedAt, entries, mine, gapToNext, notice, shownGenders, Map.of());
    }

    /** 그 줄 회원의 공개 티어, 크루 줄이거나 모르는(탈퇴) 회원이면 null(D-486). */
    public Tier publicTierOf(RankEntry entry) {
        return publicTiers.get(entry.subjectId());
    }

    /** 그 줄 회원의 표시 성별 이름, 크루 줄이거나 모르면 null. */
    public String shownGenderOf(RankEntry entry) {
        return shownGenders.get(entry.subjectId());
    }
}
