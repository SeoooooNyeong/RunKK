package com.a207.runkk.running;

import java.util.List;

/**
 * 기록 목록 한 줄(설계 5.6.2 running.RunListRow, D-233·D-234·D-292). 위치·심박 열을 읽지 않는 투영으로 만든다(6.5).
 *
 * @param summary 요약
 * @param newRecords 이 기록이 세운 신기록(없으면 빈 목록)
 * @param routeShape 경로 모양(경로가 없거나 아직 채우지 않았으면 null)
 */
public record RunListRow(
        RunSummary summary, List<NewRecordMark> newRecords, RouteShape routeShape) {

    public RunListRow {
        newRecords = List.copyOf(newRecords);
    }

    /** 저장소 투영(요약, 신기록 저장 형식, 모양 저장 형식)으로 만든다. */
    public static RunListRow of(RunSummary summary, String newRecordsData, String routeShapeData) {
        RouteShape shape = routeShapeData == null ? null : RouteShape.decode(routeShapeData);
        return new RunListRow(
                summary,
                newRecordsData == null ? List.of() : RunRecord.decodeMarks(newRecordsData),
                shape == null || shape.isEmpty() ? null : shape);
    }

    /** 신기록 표시 여부(옛 앱의 newRecord 불리언, D-292). */
    public boolean newRecord() {
        return !newRecords.isEmpty();
    }
}
