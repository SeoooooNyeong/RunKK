package com.a207.runkk.operator;

/**
 * 운영자 로그인에서 소셜 인증 자체가 실패했다(제공자 토큰 만료·위조, 설계 6.1.12, D-76 (1), R36). OperatorAuthorizer가
 * member.AuthFailureException을 이 예외로 바꿔 올리고 web.admin의 AdminAuthController가 401로 바꾼다. member 예외를
 * 그대로 올리면 web.admin이나 web.error가 member를 import해야 하므로 operator 경계에서 바꾼다. 화이트리스트 밖(권한 부족)은 이 예외가 아니라
 * PermissionDeniedException → 403이다. 메시지에 토큰 값을 싣지 않는다.
 */
public class OperatorAuthFailedException extends RuntimeException {

    public OperatorAuthFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
