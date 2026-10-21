package com.a207.runkk.member;

/**
 * 소셜 로그인 제공자 토큰을 검증해 계정을 돌려주는 포트(설계 6.1.2, 6.4 다형성). 구현은 infra.integration.auth.KakaoLoginGateway,
 * GoogleLoginGateway. 제공자를 더해도 AuthService는 바뀌지 않는다.
 */
public interface SocialLoginGateway {

    /**
     * 제공자 토큰을 검증한다.
     *
     * @throws AuthFailureException 토큰이 위조·만료되었거나 다른 앱의 토큰
     * @throws ProviderUnavailableException 제공자가 응답하지 않음
     */
    SocialAccount authenticate(String providerToken);

    ProviderType provider();
}
