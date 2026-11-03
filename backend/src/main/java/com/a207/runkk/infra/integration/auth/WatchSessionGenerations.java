package com.a207.runkk.infra.integration.auth;

import java.time.Instant;

/**
 * 회원별 워치 세션 세대 포트(2026-11-01 사용자 결정 W25, 코드 리뷰 12차 U12-6). 갱신 토큰은 서버에 저장하지 않는 JWT이고 폐기 목록은 jti
 * 단위라(RefreshTokenRevocations) 아직 쓰지 않은 워치용 갱신 토큰을 한꺼번에 끊을 수 없다. 그래서 워치용 갱신 토큰에 발급 때의 세대를 담고, 일괄
 * 폐기는 그 회원의 세대를 하나 올린다. 갱신 때 토큰의 세대가 지금 세대와 다르면 거부한다. 구현은 PostgreSQL JdbcWatchSessionGenerations
 * 하나다(폐기 목록과 같은 이유: 보안 기록이라 Redis 장애·재시작과 무관하게 남아야 한다). 저장소를 쓸 수 없으면
 * org.springframework.dao.DataAccessException을 던지고 JwtTokenIssuer가 워치 발급·갱신·폐기를 거부한다(fail-closed).
 */
interface WatchSessionGenerations {

    /** 회원의 지금 워치 세션 세대. 한 번도 폐기하지 않았으면 0. */
    long current(long memberId);

    /** 회원의 워치 세션 세대를 하나 올린다(그 전 세대의 워치용 갱신 토큰이 모두 무효가 된다). */
    void advance(long memberId, Instant at);

    /** 행이 이미 있을 때만 세대를 하나 올린다. 없으면 만들지 않는다(탈퇴 회원의 행을 새로 만들지 않기 위해, BE13-2). */
    void advanceExisting(long memberId, Instant at);

    /** 회원의 행을 지운다(회원 자료 파기 때, BE13-2). 없으면 아무것도 하지 않는다. */
    void forget(long memberId);
}
