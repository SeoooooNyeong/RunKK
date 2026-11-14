package com.a207.runkk.avatar;

import java.util.Collection;
import java.util.Map;

/**
 * 스냅숏 하나에 묶어 둔 러너 모습 저장소(S15P31A207-300, PM 2026-11-14). 랭킹 줄의 look을 0시 스냅숏 단위로 고정해, 같은 스냅숏을 보는 동안
 * 모든 사용자가 같은 모습을 읽고 요청마다 DB를 다시 읽지 않는다. infra.avatar가 Redis로 구현한다. 장애는 구현이 삼키고(경고 로그) 비어 있다고 답하므로
 * 부르는 쪽은 DB에서 읽어 응답한다(RankingCache와 같은 규칙, 5.5 장애: Redis).
 */
public interface RunnerLookSnapshotStore {

    /** 그 스냅숏에 저장된 회원별 모습. 저장되지 않은 회원은 맵에 없고, 읽을 수 없으면 빈 맵이다. */
    Map<Long, RunnerLook> find(String snapshot, Collection<Long> memberIds);

    /** 그 스냅숏에 회원별 모습을 더한다. 실패해도 예외를 던지지 않는다. */
    void save(String snapshot, Map<Long, RunnerLook> looks);
}
