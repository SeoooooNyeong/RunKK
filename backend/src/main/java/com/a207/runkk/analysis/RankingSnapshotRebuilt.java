package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 랭킹 스냅숏 하나를 새로 저장했다(재집계: 0시 작업·시작 집계·운영자 재계산, [임시:DF-성-20]). 커밋 뒤 web.record가 개인 스냅숏 상위 줄 회원의 모습을
 * 그 스냅숏 키로 미리 저장한다(PM 2026-11-14 「0시에 미리 채운다」). analysis → avatar 화살표를 만들지 않도록 사건으로 넘긴다(D-46).
 *
 * @param kind 개인·크루
 * @param period 주간·월간
 * @param computedAt 저장한 스냅숏의 집계 시각(조회 응답 aggregatedAt과 같은 값, 밀리초 단위)
 * @param topSubjectIds 상위 줄(최대 RankingService.TOP) 회원 또는 크루 ID, 순위순
 */
public record RankingSnapshotRebuilt(
        RankingKind kind,
        AggregationPeriod period,
        LocalDateTime computedAt,
        List<Long> topSubjectIds)
        implements DomainEvent {

    public RankingSnapshotRebuilt {
        topSubjectIds = List.copyOf(topSubjectIds);
    }

    @Override
    public LocalDateTime occurredAt() {
        return computedAt;
    }
}
