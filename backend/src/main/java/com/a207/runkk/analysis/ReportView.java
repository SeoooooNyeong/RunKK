package com.a207.runkk.analysis;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * 리포트 조회 결과(srs.md 6.3 3.4.3, FR-067). 리포트가 없으면 status = NO_VALID_RUN이고 요약 항목이 비어 있다.
 *
 * @param status 상태
 * @param message 화면 문구("유효 러닝 기록 없음", "코멘트 준비 중", 코멘트)
 * @param fromRun 요약 첫 순번
 * @param toRun 요약 마지막 순번
 * @param averageScore 평균 러닝 점수
 * @param distanceChangeMeters 거리 변화(미터)
 * @param paceChangeSeconds 페이스 변화(km당 초)
 * @param frequentDay 자주 달린 요일
 * @param frequentHourBand 자주 달린 시간대(3시간 단위 0~7)
 * @param comment 코멘트, 없으면 null
 * @param commentSource 코멘트 출처, 없으면 null
 * @param createdAt 요약을 만든 일시
 */
public record ReportView(
        Status status,
        String message,
        Integer fromRun,
        Integer toRun,
        Double averageScore,
        Long distanceChangeMeters,
        Integer paceChangeSeconds,
        DayOfWeek frequentDay,
        Integer frequentHourBand,
        String comment,
        CommentSource commentSource,
        LocalDateTime createdAt) {

    static final String NO_VALID_RUN_TEXT = "유효 러닝 기록 없음";
    static final String PREPARING_TEXT = "코멘트 준비 중";

    /** 리포트 상태. */
    public enum Status {
        NO_VALID_RUN,
        COMMENT_PREPARING,
        READY
    }

    static ReportView none() {
        return new ReportView(
                Status.NO_VALID_RUN,
                NO_VALID_RUN_TEXT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    static ReportView of(FitnessSummary s, Comment comment, LocalDateTime createdAt) {
        return new ReportView(
                comment == null ? Status.COMMENT_PREPARING : Status.READY,
                comment == null ? PREPARING_TEXT : comment.text(),
                s.fromRun(),
                s.toRun(),
                s.averageScore().value(),
                s.distanceChangeMeters(),
                s.paceChangeSeconds(),
                s.frequentDay(),
                s.frequentHourBand(),
                comment == null ? null : comment.text(),
                comment == null ? null : comment.source(),
                createdAt);
    }
}
