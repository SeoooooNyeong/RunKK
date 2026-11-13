package com.a207.runkk.aicoach;

import java.time.LocalDateTime;

/**
 * 지난 대화의 문답 한 건(S15P31A207-300). 본문은 ai 서버가 보관하고 api는 대화 주인 확인 뒤 그대로 전달한다.
 *
 * @param role 사용자 질문(QUESTION)인지 코치 답(ANSWER)인지
 * @param text 질문 또는 답 원문
 * @param at 남긴 시각(서버 시간대)
 */
public record CoachMessage(Role role, String text, LocalDateTime at) {
    public enum Role {
        QUESTION,
        ANSWER
    }

    @Override
    public String toString() {
        return "CoachMessage[role=" + role + ", at=" + at + ", text=redacted]";
    }
}
