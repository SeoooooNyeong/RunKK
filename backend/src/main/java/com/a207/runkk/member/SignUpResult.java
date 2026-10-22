package com.a207.runkk.member;

/**
 * 가입 완료 결과(FR-003). 거부는 BusinessRuleException(사유)으로 알린다.
 *
 * @param memberId 새 회원 ID
 * @param nickname 확정된 닉네임(겹치면 숫자 4자리가 붙는다)
 * @param tokens 발급한 토큰 쌍
 */
public record SignUpResult(long memberId, String nickname, TokenPair tokens) {}
