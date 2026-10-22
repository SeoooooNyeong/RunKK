package com.a207.runkk.infra.integration.auth;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessResourceFailureException;

/** 시험용 폐기 목록(단위 시험). unavailable()이면 저장소 장애처럼 DataAccessException을 던진다. */
final class InMemoryRefreshTokenRevocations implements RefreshTokenRevocations {

    private final Map<String, Instant> rows = new ConcurrentHashMap<>();
    private volatile boolean down;

    InMemoryRefreshTokenRevocations unavailable() {
        down = true;
        return this;
    }

    @Override
    public boolean claim(String jti, Instant expiresAt) {
        check();
        return rows.putIfAbsent(jti, expiresAt) == null;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        check();
        rows.putIfAbsent(jti, expiresAt);
    }

    @Override
    public int purgeExpired(Instant now) {
        check();
        int before = rows.size();
        rows.values().removeIf(expiry -> !expiry.isAfter(now));
        return before - rows.size();
    }

    private void check() {
        if (down) {
            throw new DataAccessResourceFailureException("db down");
        }
    }
}
