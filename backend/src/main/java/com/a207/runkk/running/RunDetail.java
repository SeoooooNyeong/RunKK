package com.a207.runkk.running;

import com.a207.runkk.common.Pace;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 기록 상세 화면에 쓸 구간·심박 계산 결과(설계 6.1.3, analysis.RecordQueryService용).
 *
 * @param summary 요약
 * @param splitPaces 1km 구간 페이스
 * @param fastestKm 가장 빠른 1km(없으면 null)
 * @param heartRateZones 심박 구간 분포(최대 심박이 없으면 빈 맵)
 * @param unstableSegments 불안정 구간
 * @param weather 날씨(없으면 null)
 * @param newRecords 신기록
 * @param heartRate 평균·최고 심박(심박 기록이 없으면 null, 결과 화면·카드 앞면 FR-078·FR-068)
 * @param pauses 정지 구간(시간순, 정지가 없으면 빈 목록, FR-036)
 * @param deletable 삭제할 수 있는 기록(RunRecord.isDeletable, 본인 여부는 부르는 쪽이 본다, v0.14 D-235)
 */
public record RunDetail(
        RunSummary summary,
        List<Pace> splitPaces,
        Pace fastestKm,
        Map<HeartRateZone, Duration> heartRateZones,
        List<UnstableSegment> unstableSegments,
        Weather weather,
        List<NewRecordMark> newRecords,
        HeartRateSummary heartRate,
        List<PauseInterval> pauses,
        boolean deletable) {

    /** 삭제 가능 여부를 모르는(거짓) 상세. */
    public RunDetail(
            RunSummary summary,
            List<Pace> splitPaces,
            Pace fastestKm,
            Map<HeartRateZone, Duration> heartRateZones,
            List<UnstableSegment> unstableSegments,
            Weather weather,
            List<NewRecordMark> newRecords,
            HeartRateSummary heartRate,
            List<PauseInterval> pauses) {
        this(
                summary,
                splitPaces,
                fastestKm,
                heartRateZones,
                unstableSegments,
                weather,
                newRecords,
                heartRate,
                pauses,
                false);
    }
}
