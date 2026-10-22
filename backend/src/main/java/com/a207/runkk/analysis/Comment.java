package com.a207.runkk.analysis;

/**
 * 리포트 코멘트(설계 6.1.4 Comment).
 *
 * @param text 문구
 * @param source 출처(AI·규칙)
 * @param forRun 어느 요약 순번(toRun)의 코멘트인지
 */
public record Comment(String text, CommentSource source, int forRun) {

    public Comment {
        if (text == null || text.isBlank() || source == null) {
            throw new IllegalArgumentException("코멘트가 비었다");
        }
    }
}
