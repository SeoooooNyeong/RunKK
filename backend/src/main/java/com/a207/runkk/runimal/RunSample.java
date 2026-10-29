package com.a207.runkk.runimal;

import com.a207.runkk.common.Distance;
import com.a207.runkk.running.RunSampleRow;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 축 평가 표본(설계 6.1.8). 최근 유효 10회와 최근 한 달 유효 기록. RunSampleRow(기록 ID, 시작 일시, 거리)는
 * RunQueryService.runSamples가 내준 값이고 세 항목을 모두 쓴다(D-32, D-41).
 *
 * @param recent 최근 10회
 * @param lastMonth 최근 한 달
 */
record RunSample(List<SampleRun> recent, List<SampleRun> lastMonth) {

    RunSample {
        recent = List.copyOf(recent);
        lastMonth = List.copyOf(lastMonth);
    }

    static RunSample of(
            List<RunSampleRow> recent10,
            List<RunSampleRow> lastMonth,
            Map<String, Boolean> courseVerdicts,
            Set<String> linkedIds) {
        return new RunSample(
                runsOf(recent10, courseVerdicts, linkedIds),
                runsOf(lastMonth, courseVerdicts, linkedIds));
    }

    private static List<SampleRun> runsOf(
            Collection<RunSampleRow> rows, Map<String, Boolean> verdicts, Set<String> linked) {
        return rows.stream()
                .map(
                        r ->
                                new SampleRun(
                                        r.recordId(),
                                        r.startedAt(),
                                        r.distance(),
                                        verdicts.get(r.recordId()),
                                        linked.contains(r.recordId()),
                                        r.battleRun()))
                .toList();
    }

    /**
     * 표본 기록 하나.
     *
     * @param recordId 기록 ID
     * @param startedAt 시작 일시
     * @param distance 거리
     * @param sameCourse 같은 코스 판정(판정이 없으면 null: 경로 파기·비교 대상 없음)
     * @param crewRunLinked 크루런 참가 기록인가(FR-146)
     */
    record SampleRun(
            String recordId,
            LocalDateTime startedAt,
            Distance distance,
            Boolean sameCourse,
            boolean crewRunLinked,
            boolean battleRun) {

        /** 크루런 연결이나 배틀런으로 "함께 달린 러닝"인가(v0.14 D-247). 둘 다여도 한 번만 센다. */
        boolean ranTogether() {
            return crewRunLinked || battleRun;
        }
    }
}
