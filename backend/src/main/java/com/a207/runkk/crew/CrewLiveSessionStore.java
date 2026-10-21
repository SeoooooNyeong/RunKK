package com.a207.runkk.crew;

import java.util.List;
import java.util.Optional;

/**
 * 진행 중 크루런 세션 저장소 포트(설계 6.1.7, D-26). 구현은 infra.crew.RedisCrewLiveSessionStore. 1초 진행값은 DB에 쓰지
 * 않는다(6.5).
 */
public interface CrewLiveSessionStore {

    Optional<CrewRunLiveSession> load(long postId);

    void save(CrewRunLiveSession session);

    void delete(long postId);

    List<Long> activePostIds();
}
