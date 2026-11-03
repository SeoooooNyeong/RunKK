package com.a207.runkk.infra.integration.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * watch_session_generation 테이블 정의(V34, W25). 읽고 쓰기는 JdbcWatchSessionGenerations가 SQL로 하고, 이 엔티티는
 * local·test 프로필의 스키마 생성과 develop·prod의 ddl validate만을 위해 있다.
 */
@Entity
@Table(name = "watch_session_generation")
class WatchSessionGenerationRow {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "generation", nullable = false)
    private long generation;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;

    protected WatchSessionGenerationRow() {}
}
