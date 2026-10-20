package com.a207.runkk.member;

/**
 * 회원 토큰 발급·검증 포트(설계 6.1.2, D-19). 접근 토큰 1시간, 갱신 토큰 14일, 가입 티켓 15분. 구현은
 * infra.integration.auth.JwtTokenIssuer. web.auth.AuthFilter와 realtime.LiveSocketHandler가 verify를
 * 부른다. 관리툴 토큰은 operator.OperatorTokenIssuer가 맡는다(D-42).
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

    /** 갱신 토큰을 폐기한다. 이미 폐기했거나 읽을 수 없는 토큰이면 아무것도 하지 않는다. */
    void revoke(String refreshToken);

    /**
     * 갱신 토큰을 검증하고 폐기한 뒤(회전) 회원 ID를 돌려준다. AuthService.refresh가 새 토큰 쌍을 발급할 회원을 알 수 있게 더했다(B2 결정: 설계
     * 6.1.2 TokenIssuer에 갱신 토큰에서 회원을 읽는 연산이 없었다).
     *
     * @throws AuthFailureException 위조·만료·폐기된 토큰
     */
    long redeemRefreshToken(String refreshToken);
}
