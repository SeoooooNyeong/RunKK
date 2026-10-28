package com.a207.runkk.infra.integration.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * refresh_token_revocation 테이블 정의(V14). 읽고 쓰기는 JdbcRefreshTokenRevocations가 SQL로 하고, 이 엔티티는
 * local·test 프로필의 스키마 생성과 develop·prod의 ddl validate만을 위해 있다.
 */
@Entity
@Table(name = "refresh_token_revocation")
class RefreshTokenRevocationRow {

    @Id
    @Column(name = "jti", length = 36)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RefreshTokenRevocationRow() {}
}
