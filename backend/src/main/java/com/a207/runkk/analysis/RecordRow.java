package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.NewRecordMark;
import com.a207.runkk.running.RouteShape;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 기록 목록·상세의 기록 한 줄(FR-058). 무효 러닝은 valid = false("집계 제외"). 러닝 모드·카드 종류는 이름 문자열로 내준다(web.record가
 * running을 import하지 않게, 12.2 D48).
 *
 * @param recordId 기록 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param elapsed 경과 시간
 * @param averagePace 평균 페이스
 * @param valid 유효 러닝
 * @param mode 러닝 모드(FREE·BATTLE·CREW_RUN)
 * @param cardKind 카드 종류
 * @param battleId 대결 ID(대결 기록이 아니면 null, v0.14 D-233: 표현 계층이 대결 결과를 붙인다)
 * @param newRecords 이 기록이 세운 신기록(목록만, 상세는 빈 목록, v0.14 D-292)
 * @param routeShape 목록 썸네일 경로 모양(없거나 감추면 null, v0.14 D-234)
 */
public record RecordRow(
        String recordId,
        LocalDateTime startedAt,
        Distance distance,
        Duration elapsed,
        Pace averagePace,
        boolean valid,
        String mode,
        String cardKind,
        Long battleId,
        List<NewRecordMark> newRecords,
        RouteShape routeShape) {

    public RecordRow {
        newRecords = newRecords == null ? List.of() : List.copyOf(newRecords);
    }

    static RecordRow of(RunSummary s) {
        return of(s, List.of(), null);
    }

    static RecordRow of(RunSummary s, List<NewRecordMark> newRecords, RouteShape routeShape) {
        return new RecordRow(
                s.recordId(),
                s.startedAt(),
                s.distance(),
                s.elapsed(),
                s.averagePace(),
                s.valid(),
                s.mode().name(),
                s.cardKind().name(),
                s.battleId(),
                newRecords,
                routeShape);
    }

    /** 신기록 표시 여부(옛 앱 newRecord, D-292). */
    public boolean newRecord() {
        return !newRecords.isEmpty();
    }
}
