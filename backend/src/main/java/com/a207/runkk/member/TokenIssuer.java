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
     * 갱신 토큰을 검증하고 폐기한 뒤(회전) 회원과 토큰 종류를 돌려준다. AuthService.refresh가 새 토큰 쌍을 발급할 회원을 알 수 있게 더했다(B2 결정:
     * 설계 6.1.2 TokenIssuer에 갱신 토큰에서 회원을 읽는 연산이 없었다). 워치용 갱신 토큰은 그 회원의 워치 세션이 폐기되었으면 거부한다(W25).
     *
     * @throws AuthFailureException 위조·만료·폐기된 토큰, 폐기된 워치 세션의 토큰
     * @throws ProviderUnavailableException 폐기 목록·워치 세션 세대를 확인할 수 없음(fail-closed, M6 후속 결정)
     */
    RefreshGrant redeemRefreshToken(String refreshToken);

    /**
     * 워치용 토큰 쌍(POST /auth/watch-session, D-372, W25). 접근 토큰은 issue와 같고, 갱신 토큰에는 워치용 표시와 지금의 워치 세션
     * 세대를 담는다.
     *
     * @throws ProviderUnavailableException 워치 세션 세대를 읽을 수 없음
     */
    TokenPair issueWatch(long memberId);

    /** 갱신으로 쓴 토큰과 같은 종류의 새 토큰 쌍. 워치용이면 쓴 토큰의 워치 세션 세대를 그대로 잇는다(W25). */
    TokenPair reissue(RefreshGrant grant);

    /**
     * 그 회원의 워치용 갱신 토큰을 모두 폐기한다(워치연동 끄기·로그아웃·탈퇴, W25). 폰 갱신 토큰은 그대로다. 이미 발급한 접근 토큰은 만료(최대 1시간)까지
     * 남는다.
     *
     * @throws ProviderUnavailableException 워치 세션 세대를 기록할 수 없음
     */
    void revokeWatchSessions(long memberId);

    /**
     * 토큰 만료가 지난 폐기 목록 행을 지우고 지운 수를 돌려준다(M6 후속 결정). batch.member의 RevocationPurgeJob이
     * AuthService.purgeExpiredRevocations로 부른다.
     */
    int purgeExpiredRevocations(LocalDateTime now);
}
