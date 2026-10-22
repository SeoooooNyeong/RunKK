package com.a207.runkk.analysis;

import com.a207.runkk.common.AggregationPeriod;
import java.util.Optional;

/**
 * 랭킹 스냅숏 저장 포트(설계 6.1.4, D-25). 구현 infra.analysis.JpaRankingSnapshotRepository(DB 저장 + Redis 캐시: 상위
 * N과 회원별 줄을 따로). analysis 밖에서 쓰지 않는다(13.1 규칙 4).
 */
public interface RankingSnapshotRepository {

    /** 같은 종류·기간의 이전 스냅숏을 대신한다. */
    void save(RankingSnapshot snapshot);

    /** 최근 스냅숏 전체(DB). 시작 때 스냅숏이 있는지 볼 때만 쓴다. */
    Optional<RankingSnapshot> latest(RankingKind kind, AggregationPeriod period);

    /** 최근 스냅숏의 상위 top줄과 subject 줄(캐시 우선, 비면 DB). 스냅숏이 없으면 비어 있다. */
    Optional<RankingPage> latestPage(
            RankingKind kind, AggregationPeriod period, int top, Optional<Long> subjectId);
}
