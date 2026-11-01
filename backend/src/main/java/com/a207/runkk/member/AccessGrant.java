package com.a207.runkk.member;

import java.time.Instant;

/**
 * 검증된 접근 토큰 한 장의 회원과 만료 시각(설계 6.1.2, D-110, L29). TokenIssuer.verifyGrant가 돌려주고 realtime이 이 만료 시각에
 * 웹소켓을 4401 TOKEN_EXPIRED로 닫는다(F.3.3). web.auth.AuthFilter는 워치용 접근 토큰인지를 요청 속성으로 넘긴다(W26).
 *
 * @param memberId 회원 ID
 * @param expiresAt 접근 토큰 만료 시각(이 시각부터 무효, 정각 만료)
 * @param watch POST /auth/watch-session으로 발급했거나 그 갱신으로 받은 워치용 접근 토큰인가(2026-11-01 사용자 결정 W26)
 */
public record AccessGrant(long memberId, Instant expiresAt, boolean watch) {

    public AccessGrant {
        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시각이 비었다");
        }
    }

    /**
     * 웹소켓 연결 전용: 핸드셰이크가 속성에 담아 둔 회원과 만료 시각을 되살릴 때만 쓴다(접근 토큰 검증 결과가 아니다). 웹소켓은 워치용인지를 가리지 않으므로
     * watch는 false로 둔다.
     */
    public AccessGrant(long memberId, Instant expiresAt) {
        this(memberId, expiresAt, false);
    }
}
