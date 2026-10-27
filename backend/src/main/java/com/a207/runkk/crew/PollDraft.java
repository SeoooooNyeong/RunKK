package com.a207.runkk.crew;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 투표 입력(FR-206). 질문 1~200자, CHOICES면 선택지 2~5개·각 1~40자(글자 수는 코드 포인트, D-143), YES_NO면 선택지를 받지 않는다.
 * 길이·개수 위반은 생성자의 IllegalArgumentException → 400(D-155, D-165). 마감이 지금 이전인지는 PollPost가
 * POLL_INVALID(409)로 본다.
 *
 * @param question 질문
 * @param method 투표 방식
 * @param options 선택지(CHOICES), YES_NO는 빈 목록
 * @param closesAt 마감 일시
 */
public record PollDraft(
        String question, PollMethod method, List<String> options, LocalDateTime closesAt) {

    static final int MAX_QUESTION = 200;
    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 5;
    static final int MAX_LABEL = 40;

    public PollDraft {
        options = options == null ? List.of() : List.copyOf(options);
        if (question == null
                || question.isBlank()
                || CrewPost.codePoints(question) > MAX_QUESTION) {
            throw new IllegalArgumentException("질문은 1~200자");
        }
        if (method == null || closesAt == null) {
            throw new IllegalArgumentException("투표 방식·마감이 없다");
        }
        if (method == PollMethod.YES_NO && !options.isEmpty()) {
            throw new IllegalArgumentException("찬반 투표는 선택지를 받지 않는다");
        }
        if (method == PollMethod.CHOICES) {
            if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("선택지는 2~5개");
            }
            for (String label : options) {
                if (label == null || label.isBlank() || CrewPost.codePoints(label) > MAX_LABEL) {
                    throw new IllegalArgumentException("선택지는 1~40자");
                }
            }
        }
    }
}
