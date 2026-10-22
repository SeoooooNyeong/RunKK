package com.a207.runkk.battle;

import java.util.List;
import java.util.Optional;

/**
 * 진행 중 랜덤 대결 세션 저장소 포트(설계 6.1.6, NFR-004). 구현은 infra.battle의 RedisLiveSessionStore(대결마다 해시 하나)와
 * JdbcLiveSessionStore(runkk.queue=jdbc 대안, D-07). 1초 진행값은 DB 대결 행에 쓰지 않는다(6.5).
 */
public interface LiveSessionStore {

    Optional<LiveBattleSession> load(long battleId);

    void save(LiveBattleSession session);

    void delete(long battleId);

    List<Long> activeBattleIds();
}
