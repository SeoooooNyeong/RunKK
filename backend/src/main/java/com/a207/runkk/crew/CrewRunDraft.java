package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDateTime;

/**
 * 크루런 모집글 입력(자료사전 게시내용: 제목, 내용, 모집희망일시, 크루런거리, (투표마감일시), 반복). 장소·위치 항목이 없다(FR-132).
 *
 * @param title 제목(공백 제외 1~30자)
 * @param body 내용(0~200자), null이면 빈 문자열
 * @param scheduledAt 모집희망일시
 * @param distance 크루런 거리(3·5·10km·하프), null이면 제한 없음
 * @param voteDeadline 투표 마감, 비우면 모집희망일시
 * @param repeat 반복, null이면 NONE
 */
public record CrewRunDraft(
        String title,
        String body,
        LocalDateTime scheduledAt,
        StandardDistance distance,
        LocalDateTime voteDeadline,
        CrewRunRepeat repeat) {

    /** 제목이 비었을 때 옛 입력에 붙이는 제목. V57이 빈 본문 글에 넣는 값과 같다. */
    static final String DEFAULT_TITLE = "크루런";

    public CrewRunDraft {
        repeat = repeat == null ? CrewRunRepeat.NONE : repeat;
    }

    /** 제목 칸이 없던 입력(W6 전 호출부 호환). 본문 첫 줄을 제목(30자까지)으로 삼고 본문은 그대로 두며 반복하지 않는다. 첫 줄이 비면 "크루런". */
    public CrewRunDraft(
            String body,
            LocalDateTime scheduledAt,
            StandardDistance distance,
            LocalDateTime voteDeadline) {
        this(titleOf(body), body, scheduledAt, distance, voteDeadline, CrewRunRepeat.NONE);
    }

    private static String titleOf(String body) {
        String first =
                body == null ? "" : body.strip().lines().findFirst().map(String::strip).orElse("");
        if (first.isEmpty()) {
            return DEFAULT_TITLE;
        }
        return CrewPost.codePoints(first) <= CrewRunPost.MAX_TITLE
                ? first
                : first.substring(0, first.offsetByCodePoints(0, CrewRunPost.MAX_TITLE));
    }
}
