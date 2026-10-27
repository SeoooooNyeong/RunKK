package com.a207.runkk.member;

/**
 * 계정 유형(자료사전 계정유형, FR-197, D-89). 게스트는 기기 자격 증명으로, 연동회원은 소셜 계정으로 들어온다. 본인 전용 응답(GET /me/settings)에만
 * 싣는다(FR-006).
 */
public enum AccountType {
    GUEST,
    LINKED
}
