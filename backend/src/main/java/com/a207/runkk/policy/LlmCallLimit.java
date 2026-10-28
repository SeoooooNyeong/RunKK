package com.a207.runkk.policy;

/**
 * LLM 코멘트 하루 호출 한도(설계 6.1.9). 전체 한도는 ai 서버 절대 상한 240 ÷ 1.2 = 200 이하다(D-62).
 *
 * @param perMember 회원 한도
 * @param global 전체 한도
 */
public record LlmCallLimit(int perMember, int global) {}
