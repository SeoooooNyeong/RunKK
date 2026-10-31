package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.RecordVisibility;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 기록 상세(설계 6.1.4 RecordQueryService.detail, srs.md 6.3 3.2, FR-059, FR-078, FR-079, FR-185,
 * FR-186). 본인 전용 항목(심박 구간, 크루 반영 거리, 적립 리워드)은 다른 러너에게 null·빈 값이다.
 *
 * @param summary 기록 줄
 * @param owner 열람자가 기록 주인인가
 * @param route 코스 지도 좌표(본인이거나 코스지도공유 켜짐일 때만, 파기 뒤 빈 목록)
 * @param calories 소모 열량 추정(kcal)
 * @param splitPaces 1km 구간 페이스
 * @param fastestKm 가장 빠른 1km, 없으면 null
 * @param heartRate 평균·최고 심박, 없으면 null
 * @param heartRateZones 심박 구간(Z1~Z5) → 시간. 본인이고 출생연도가 있을 때만
 * @param unstable 신호 불안정 구간(시작 기준 경과 시간)
 * @param weather 날씨, 없으면 null
 * @param newRecords 신기록 문구
 * @param crewContribution 크루 주간 거리 반영 거리(본인), 없으면 null
 * @param rewardPoints 이 기록으로 적립한 리워드(본인), 없으면 null
 * @param pauses 정지 구간(시간순, 정지가 없으면 빈 목록, FR-036)
 * @param pausedSeconds 정지 시간 합(초). summary의 경과 시간은 정지를 뺀 운동 시간이다
 * @param deletable 삭제 가능(본인이고 집계 제외 기록)
 * @param recordVisibility 기록 주인의 기록 공개 범위(본인 열람일 때만, 남의 기록이면 null)
 */
public record RecordDetailView(
        RecordRow summary,
        boolean owner,
        List<GeoPoint> route,
        int calories,
        List<Pace> splitPaces,
        Pace fastestKm,
        HeartRate heartRate,
        Map<String, Duration> heartRateZones,
        List<UnstableRange> unstable,
        WeatherInfo weather,
        List<String> newRecords,
        Distance crewContribution,
        Integer rewardPoints,
        List<PauseRange> pauses,
        long pausedSeconds,
        boolean deletable,
        RecordVisibility recordVisibility) {

    /**
     * 정지 구간 하나(FR-036, FR-194). web.record가 running을 import하지 않게 이 묶음으로 옮긴다(12.2 D48).
     *
     * @param from 정지 시작 일시
     * @param to 재개 일시
     */
    public record PauseRange(LocalDateTime from, LocalDateTime to) {}

    /**
     * 평균·최고 심박.
     *
     * @param average 평균 bpm
     * @param max 최고 bpm
     */
    public record HeartRate(int average, int max) {}

    /**
     * 불안정 구간 하나(FR-033, FR-059 표시).
     *
     * @param from 시작 경과 시간
     * @param to 끝 경과 시간
     * @param distance 구간 거리
     */
    public record UnstableRange(Duration from, Duration to, Distance distance) {}

    /**
     * 날씨(FR-184).
     *
     * @param temperatureC 기온
     * @param sky 하늘 상태 이름
     */
    public record WeatherInfo(int temperatureC, String sky) {}
}
