package com.a207.runkk.crew;

import com.a207.runkk.common.StandardDistance;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 지난 크루런 연·월 조회(피그마 F-11b·F-11c, W7 지난 크루런 달력, W6). 지난 크루런은 보이는 크루런 중 FINISHED·CANCELLED이고 월은 일시의
 * 한국 날짜로 나눈다.
 *
 * @param month 조회한 월
 * @param dates 그 달에 지난 크루런이 있는 날짜(오름차순, 중복 없음)
 * @param runs 그 달(날짜를 주면 그날)의 지난 크루런, 일시 최신순
 * @param earliestMonth 이 크루의 가장 이른 지난 크루런 월, 없으면 null
 */
public record PastCrewRunsView(
        YearMonth month, List<LocalDate> dates, List<Run> runs, YearMonth earliestMonth) {

    public PastCrewRunsView {
        dates = List.copyOf(dates);
        runs = List.copyOf(runs);
    }

    /**
     * 지난 크루런 한 줄.
     *
     * @param postId 게시글 ID
     * @param title 제목
     * @param scheduledAt 일시
     * @param distance 거리, 제한 없음이면 null
     * @param status FINISHED | CANCELLED
     * @param goingCount 참석 인원
     * @param runnerCount 함께 달린 인원(출발 때 준비 완료), 취소면 0
     */
    public record Run(
            long postId,
            String title,
            LocalDateTime scheduledAt,
            StandardDistance distance,
            CrewRunStatus status,
            int goingCount,
            int runnerCount) {}
}
