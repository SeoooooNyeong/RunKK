package com.a207.runkk.analysis;

import java.time.LocalDate;

/**
 * 코멘트 생성 조건(설계 6.1.4 CommentContext, srs.md 6.3 3.4.2).
 *
 * @param llmAgreed LLM 전송 동의
 * @param aiEnabled AI 코멘트 사용(축소 운영 스위치)
 * @param memberId 한도 계산용. LLM에는 보내지 않는다(NFR-024)
 * @param today 한도 날짜
 */
public record CommentContext(
        boolean llmAgreed, boolean aiEnabled, long memberId, LocalDate today) {}
