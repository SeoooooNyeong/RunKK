package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import java.util.Optional;

/**
 * 랭킹 스냅숏 저장 포트(설계 6.1.4, D-25). 구현 infra.analysis.JpaRankingSnapshotRepository(DB 저장 + Redis 캐시).
 * analysis 밖에서 쓰지 않는다(13.1 규칙 4).
 */
public interface RankingSnapshotRepository {

    /** 같은 종류·기간의 이전 스냅숏을 대신한다. */
    void save(RankingSnapshot snapshot);

    Optional<RankingSnapshot> latest(RankingKind kind, AggregationPeriod period);
}
