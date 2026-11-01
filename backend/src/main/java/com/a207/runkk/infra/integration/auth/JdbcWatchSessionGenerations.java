package com.a207.runkk.infra.integration.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 워치 세션 세대의 PostgreSQL 구현(W25). watch_session_generation(회원 ID 기본 키, 세대, 마지막 폐기 시각) 테이블만 쓴다. 올리기는
 * UPDATE를 먼저 하고, 행이 없으면 INSERT … ON CONFLICT DO NOTHING, 동시에 처음 올린 다른 요청에 졌으면 다시 UPDATE한다.
 * local·test의 H2(PostgreSQL 모드)도 같은 SQL을 받는다(ON CONFLICT DO UPDATE는 H2가 받지 않는다). 호출한 쪽 트랜잭션이 없으면
 * 문장마다 바로 커밋된다.
 */
@Component
class JdbcWatchSessionGenerations implements WatchSessionGenerations {

    private static final String SELECT =
            "select generation from watch_session_generation where member_id = ?";
    private static final String UPDATE =
            "update watch_session_generation set generation = generation + 1, revoked_at = ?"
                    + " where member_id = ?";
    private static final String INSERT =
            "insert into watch_session_generation (member_id, generation, revoked_at)"
                    + " values (?, 1, ?) on conflict do nothing";

    private final JdbcTemplate jdbc;

    JdbcWatchSessionGenerations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long current(long memberId) {
        List<Long> rows = jdbc.queryForList(SELECT, Long.class, memberId);
        return rows.isEmpty() ? 0L : rows.get(0);
    }

    @Override
    public void advance(long memberId, Instant at) {
        OffsetDateTime when = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        if (jdbc.update(UPDATE, when, memberId) == 1) {
            return;
        }
        if (jdbc.update(INSERT, memberId, when) == 1) {
            return;
        }
        jdbc.update(UPDATE, when, memberId); // 동시에 처음 올린 요청이 행을 먼저 만들었다
    }
}
