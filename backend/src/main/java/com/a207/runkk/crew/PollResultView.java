package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 투표 결과(FR-207). 응답자는 익명이 아니다.
 *
 * @param postId 게시글 ID
 * @param question 질문
 * @param method 투표 방식
 * @param closesAt 마감 일시
 * @param closed 마감됨(now ≥ closesAt)
 * @param options 선택지별 결과, 번호 순
 * @param myOptionNo 열람자 선택, 없으면 null
 */
public record PollResultView(
        long postId,
        String question,
        PollMethod method,
        LocalDateTime closesAt,
        boolean closed,
        List<Option> options,
        Integer myOptionNo) {

    public PollResultView {
        options = List.copyOf(options);
    }

    /**
     * 선택지 하나의 결과.
     *
     * @param optionNo 번호
     * @param label 이름
     * @param count 응답 수
     * @param respondents 응답자
     */
    public record Option(int optionNo, String label, int count, List<Respondent> respondents) {

        public Option {
            respondents = List.copyOf(respondents);
        }
    }

    /**
     * 응답자 한 명.
     *
     * @param memberId 회원 ID
     * @param displayName 표시명
     */
    public record Respondent(long memberId, DisplayName displayName) {}
}
