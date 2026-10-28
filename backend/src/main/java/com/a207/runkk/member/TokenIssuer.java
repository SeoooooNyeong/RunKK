package com.a207.runkk.member;

import java.time.LocalDateTime;

/**
 * 회원 토큰 발급·검증 포트(설계 6.1.2, D-19). 접근 토큰 1시간, 갱신 토큰 14일, 가입 티켓 15분. 구현은
 * infra.integration.auth.JwtTokenIssuer. web.auth.AuthFilter가 verify를,
 * realtime(TokenHandshakeInterceptor, 웹소켓 연결)이 verifyGrant를 부른다(12.2 D54). 관리툴 토큰은
 * operator.OperatorTokenIssuer가 맡는다(D-42).
 */
public interface TokenIssuer {

    TokenPair issue(long memberId);

    String issueSignUpTicket(SocialAccount account);

    /**
     * 가입 티켓을 계정으로 바꾼다.
     *
     * @throws AuthFailureException 위조·만료
     */
    SocialAccount redeemSignUpTicket(String ticket);

    /**
     * 접근 토큰을 검증해 회원 ID를 돌려준다.
     *
     * @throws AuthFailureException 위조·만료·다른 종류의 토큰
     */
    long verify(String accessToken);

    /**
     * 접근 토큰을 검증해 회원 ID와 만료 시각을 돌려준다(실시간 연결 만료 예약용, D-110).
     *
     * @throws AuthFailureException 위조·만료·다른 종류의 토큰
     */
    AccessGrant verifyGrant(String accessToken);

    /**
     * 갱신 토큰을 폐기한다. 이미 폐기했거나 읽을 수 없는 토큰이면 아무것도 하지 않는다.
     *
     * @throws ProviderUnavailableException 폐기 목록에 기록할 수 없음(M6 후속 결정)
     */
    void revoke(String refreshToken);

    /**
     * 갱신 토큰을 검증하고 폐기한 뒤(회전) 회원 ID를 돌려준다. AuthService.refresh가 새 토큰 쌍을 발급할 회원을 알 수 있게 더했다(B2 결정: 설계
     * 6.1.2 TokenIssuer에 갱신 토큰에서 회원을 읽는 연산이 없었다).
     *
     * @throws AuthFailureException 위조·만료·폐기된 토큰
     * @throws ProviderUnavailableException 폐기 목록을 확인할 수 없음(fail-closed, M6 후속 결정)
     */
    long redeemRefreshToken(String refreshToken);

    /**
     * 토큰 만료가 지난 폐기 목록 행을 지우고 지운 수를 돌려준다(M6 후속 결정). batch.member의 RevocationPurgeJob이
     * AuthService.purgeExpiredRevocations로 부른다.
     */
    int purgeExpiredRevocations(LocalDateTime now);
}
