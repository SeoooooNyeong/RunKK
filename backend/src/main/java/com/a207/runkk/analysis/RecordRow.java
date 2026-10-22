package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.running.RunSummary;
import java.time.Duration;
import java.time.LocalDateTime;

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
 */
public record RecordRow(
        String recordId,
        LocalDateTime startedAt,
        Distance distance,
        Duration elapsed,
        Pace averagePace,
        boolean valid,
        String mode,
        String cardKind) {

    static RecordRow of(RunSummary s) {
        return new RecordRow(
                s.recordId(),
                s.startedAt(),
                s.distance(),
                s.elapsed(),
                s.averagePace(),
                s.valid(),
                s.mode().name(),
                s.cardKind().name());
    }
}
