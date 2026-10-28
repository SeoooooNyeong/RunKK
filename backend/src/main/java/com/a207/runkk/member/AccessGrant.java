package com.a207.runkk.member;

import java.time.Instant;

/**
 * 검증된 접근 토큰 한 장의 회원과 만료 시각(설계 6.1.2, D-110, L29). TokenIssuer.verifyGrant가 돌려주고 realtime이 이 만료 시각에
 * 웹소켓을 4401 TOKEN_EXPIRED로 닫는다(F.3.3).
 *
 * @param memberId 회원 ID
 * @param expiresAt 접근 토큰 만료 시각(이 시각부터 무효, 정각 만료)
 */
public record AccessGrant(long memberId, Instant expiresAt) {

    public AccessGrant {
        if (expiresAt == null) {
            throw new IllegalArgumentException("만료 시각이 비었다");
        }
    }
}
