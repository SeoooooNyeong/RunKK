package com.a207.runkk.infra.integration.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 갱신 토큰 폐기 목록의 PostgreSQL 구현(M6 후속 결정, D-07 "저장소는 인터페이스 뒤"). refresh_token_revocation(jti 기본 키, 만료
 * 시각) 테이블만 쓴다. claim은 INSERT … ON CONFLICT DO NOTHING 한 번이라 같은 토큰을 동시에 두 번 써도 한쪽만 이긴다. 호출한 쪽 트랜잭션이
 * 없으면 바로 커밋된다(AuthService는 트랜잭션 밖). 만료 행은 batch.member RevocationPurgeJob이 매일 지운다.
 */
@Component
class JdbcRefreshTokenRevocations implements RefreshTokenRevocations {

    private static final String INSERT =
            "insert into refresh_token_revocation (jti, expires_at) values (?, ?)"
                    + " on conflict do nothing";

    private final JdbcTemplate jdbc;

    JdbcRefreshTokenRevocations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String jti, Instant expiresAt) {
        return jdbc.update(INSERT, jti, utc(expiresAt)) == 1;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        jdbc.update(INSERT, jti, utc(expiresAt));
    }

    @Override
    public int purgeExpired(Instant now) {
        return jdbc.update("delete from refresh_token_revocation where expires_at <= ?", utc(now));
    }

    private static OffsetDateTime utc(Instant at) {
        return OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
    }
}
