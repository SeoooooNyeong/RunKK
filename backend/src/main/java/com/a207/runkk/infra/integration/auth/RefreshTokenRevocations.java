package com.a207.runkk.infra.integration.auth;

import java.time.Instant;

/**
 * 폐기한(로그아웃했거나 이미 한 번 쓴) 갱신 토큰 ID 목록 포트(코드 리뷰 2차 M6, M6 후속 결정). 구현은 PostgreSQL
 * JdbcRefreshTokenRevocations 하나다: 폐기 목록은 실시간 상태가 아니라 보안 기록이라 Redis 장애·재시작과 무관하게 남아야 하고(5.5 "Redis
 * 장애는 매칭·실시간만"), "처음 쓰기"를 고유 키 한 번으로 원자적으로 정한다. 토큰 원문은 저장하지 않는다. 저장소를 쓸 수 없으면
 * org.springframework.dao.DataAccessException을 던지고 JwtTokenIssuer가 갱신을 거부한다(fail-closed).
 */
interface RefreshTokenRevocations {

    /** 처음 폐기하면 true, 이미 폐기된 ID면 false. 회전(한 번만 쓰기)에 쓴다. */
    boolean claim(String jti, Instant expiresAt);

    /** 폐기한다(로그아웃). 이미 폐기됐어도 그대로다. */
    void revoke(String jti, Instant expiresAt);

    /** 토큰 만료가 지난 행을 지우고 지운 수를 돌려준다(만료된 토큰은 서명 검사에서 이미 거부된다). */
    int purgeExpired(Instant now);
}
