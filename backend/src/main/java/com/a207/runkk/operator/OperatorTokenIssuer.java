package com.a207.runkk.operator;

/**
 * 관리툴 토큰 발급·검증 포트(설계 6.1.12, D-42). 구현 infra.integration.auth.JwtOperatorTokenIssuer. 회원
 * 토큰(member.TokenIssuer)과 서명 키·발급자를 나눠 한쪽 토큰으로 다른 쪽 검사를 통과하지 못한다.
 */
public interface OperatorTokenIssuer {

    /** 관리툴 토큰 발급(만료 8시간). */
    String issue(OperatorId operator);

    /**
     * 서명·만료 확인 뒤 OperatorId.
     *
     * @throws com.a207.runkk.common.PermissionDeniedException 위조·만료·형식 오류
     */
    OperatorId verify(String adminToken);
}
