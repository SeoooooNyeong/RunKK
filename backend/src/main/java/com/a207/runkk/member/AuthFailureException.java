package com.a207.runkk.member;

/**
 * 소셜 로그인 인증 실패 또는 회원 토큰 검증 실패(설계 6.1.2 SocialLoginGateway·TokenIssuer). AuthService가
 * LoginResult.failed로, web.auth가 401로 바꾼다. 메시지에 토큰 값을 싣지 않는다.
 */
public class AuthFailureException extends RuntimeException {

    public AuthFailureException(String message) {
        super(message);
    }

    public AuthFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
