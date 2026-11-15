package com.a207.runkk.web.record;

import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.common.AggregationPeriod;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 랭킹 스냅숏 식별자 {종류}:{기간}:{집계 시각 밀리초}(D-858). 집계 시각(서버 시간대 LocalDateTime)을 UTC로 보아 epoch 밀리초로 바꾼 값으로,
 * infra.analysis.RankingCache 판 번호와 같은 식이다(U26-2). 조회(RankingController)와 0시 미리 채우기
 * (RankingLookPrefill)가 이 함수 하나를 함께 써 키가 어긋나지 않는다.
 */
final class RankingSnapshotIds {

    private RankingSnapshotIds() {}

    static String of(RankingKind kind, AggregationPeriod period, LocalDateTime computedAt) {
        return kind + ":" + period + ":" + computedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
