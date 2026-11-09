package com.a207.runkk.web.runbody;

import com.a207.runkk.common.ServerZone;
import com.a207.runkk.runbody.RunBodySessionItem;
import com.a207.runkk.runbody.RunBodySessionPage;
import com.a207.runkk.runbody.RunBodySessionView;
import com.a207.runkk.runbody.RunBodyTrend;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.OffsetDateTime;
import java.util.List;

/** 런바디 응답(앱 계약 runbody contract.md v1.2 1절·7절·8절). */
public final class RunBodyDtos {

    private RunBodyDtos() {}

    /**
     * GET /api/v1/runbody/sessions/{recordId} 200 본문.
     *
     * @param recordId 경로의 기록 ID
     * @param status PENDING·ANALYZING·DONE·FAILED(서버 RunBodyStatus 이름 그대로)
     * @param result DONE일 때만 ai GET /sessions/{id} 200 본문 그대로, 아니면 null
     */
    public record RunBodySessionResponse(
            String recordId, String status, @JsonRawValue String result, String failureReason) {

        static RunBodySessionResponse of(RunBodySessionView view) {
            return new RunBodySessionResponse(
                    view.recordId(), view.status().name(), view.result(), view.failureReason());
        }
    }

    /**
     * GET /api/v1/runbody/sessions 200 본문(러닝 선택 G-30, 계약 7절).
     *
     * @param items 최신순(러닝 저장 순서의 역순)
     * @param nextCursor 다음 쪽 커서(이 쪽 마지막 recordId). 더 없으면 null
     */
    public record RunBodySessionListResponse(List<RunBodySessionRow> items, String nextCursor) {

        static RunBodySessionListResponse of(RunBodySessionPage page) {
            return new RunBodySessionListResponse(
                    page.items().stream().map(RunBodySessionRow::of).toList(), page.nextCursor());
        }
    }

    /**
     * 러닝 선택 목록 한 줄.
     *
     * @param recordId 기록 ID
     * @param status PENDING·ANALYZING·DONE·FAILED
     * @param score 러닝 점수(0~100, ai 결과 axes의 점수 있는 축 평균 반올림). DONE이고 점수를 남겼을 때만
     * @param scoreChange 직전 점수 있는 러닝 대비 변화(양수면 올랐다). 셈할 수 없으면 null
     */
    public record RunBodySessionRow(
            String recordId, String status, Integer score, Integer scoreChange) {

        static RunBodySessionRow of(RunBodySessionItem item) {
            return new RunBodySessionRow(
                    item.recordId(), item.status().name(), item.score(), item.scoreChange());
        }
    }

    /**
     * GET /api/v1/runbody/trend 200 본문(요약 "최근 10회 변화" G-14, 계약 8절).
     *
     * @param validRunCount 유효 러닝 수
     * @param round 최근 10회의 회차(0이면 아직 10회 전)
     * @param needs 1회차까지 남은 러닝 수
     * @param recent 최근 10회(round 0이면 null)
     * @param previous 직전 10회(round 2 미만이면 null)
     * @param sameHeartRatePaceChange 같은 심박 페이스 변화(초/km, 양수면 빨라짐). 셈할 수 없으면 null
     * @param runimalType 나의 러닝 유형(서버 RunimalType 이름, 확정 우선·없으면 임시). 없으면 null
     * @param firstRunAt 첫 유효 러닝 시작 시각(+09:00). 앱이 기기 시간대로 바꿔 월간·주간 이동 하한으로 쓴다. 없으면 null
     */
    public record RunBodyTrendResponse(
            int validRunCount,
            int round,
            int needs,
            RunBodyWindowResponse recent,
            RunBodyWindowResponse previous,
            Integer sameHeartRatePaceChange,
            String runimalType,
            OffsetDateTime firstRunAt) {

        static RunBodyTrendResponse of(RunBodyTrend trend, String runimalType) {
            return new RunBodyTrendResponse(
                    trend.validRunCount(),
                    trend.round(),
                    trend.needs(),
                    RunBodyWindowResponse.of(trend.recent()),
                    RunBodyWindowResponse.of(trend.previous()),
                    trend.sameHeartRatePaceChange(),
                    runimalType,
                    trend.firstRunStartedAt() == null
                            ? null
                            : trend.firstRunStartedAt().atZone(ServerZone.ID).toOffsetDateTime());
        }
    }

    /**
     * 한 회차(10회)의 값.
     *
     * @param fromOrdinal 첫 유효 러닝 순번
     * @param toOrdinal 마지막 유효 러닝 순번
     * @param paceSecondsPerKm 평균 페이스(초/km, 거리 가중)
     * @param runsPerWeek 주간 러닝 빈도(회/주, 소수 첫째 자리)
     * @param runsPerMonth 월간 러닝 빈도(회/30일, 소수 첫째 자리)
     * @param averageHeartRate 평균 심박(심박 있는 러닝 3회 미만이면 null)
     * @param averageDistanceMeters 평균 거리(m)
     */
    public record RunBodyWindowResponse(
            int fromOrdinal,
            int toOrdinal,
            int paceSecondsPerKm,
            double runsPerWeek,
            double runsPerMonth,
            Integer averageHeartRate,
            long averageDistanceMeters) {

        static RunBodyWindowResponse of(RunBodyTrend.Window w) {
            return w == null
                    ? null
                    : new RunBodyWindowResponse(
                            w.fromOrdinal(),
                            w.toOrdinal(),
                            w.paceSecondsPerKm(),
                            w.runsPerWeek(),
                            w.runsPerMonth(),
                            w.averageHeartRate(),
                            w.averageDistanceMeters());
        }
    }
}
