package com.a207.runkk.infra.integration.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessResourceFailureException;

/** 시험용 워치 세션 세대(단위 시험). unavailable()이면 저장소 장애처럼 DataAccessException을 던진다. */
final class InMemoryWatchSessionGenerations implements WatchSessionGenerations {

    private final Map<Long, Long> rows = new ConcurrentHashMap<>();
    private volatile boolean down;

    InMemoryWatchSessionGenerations unavailable() {
        down = true;
        return this;
    }

    InMemoryWatchSessionGenerations available() {
        down = false;
        return this;
    }

    @Override
    public long current(long memberId) {
        check();
        return rows.getOrDefault(memberId, 0L);
    }

    @Override
    public void advance(long memberId, Instant at) {
        check();
        rows.merge(memberId, 1L, Long::sum);
    }

    @Override
    public void advanceExisting(long memberId, Instant at) {
        check();
        rows.computeIfPresent(memberId, (id, g) -> g + 1);
    }

    @Override
    public void forget(long memberId) {
        check();
        rows.remove(memberId);
    }

    boolean hasRow(long memberId) {
        return rows.containsKey(memberId);
    }

    private void check() {
        if (down) {
            throw new DataAccessResourceFailureException("db down");
        }
    }
}
