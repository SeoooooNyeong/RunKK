package com.a207.runkk.runbody;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * ai 런바디 분석 요청 본문(설계 05-19 2.2, D-444, ai SessionIn). 회원 식별 항목(이름·이메일)은 없고 userId는 회원 ID 문자열이다. 시각은
 * 운동 시간 축(정지 구간을 뺀 시작 뒤 밀리초)이다.
 *
 * @param sessionId 기록 ID
 * @param userId 회원 ID 문자열
 * @param source watch 또는 phone(시작 기기)
 * @param startedAt 시작 시각(서버 시간대)
 * @param endedAt 종료 시각(서버 시간대)
 * @param activeMs 운동 시간(정지 제외)
 * @param distanceMeters 기록 거리
 * @param birthYear 출생연도(ai가 최대 심박 220 - 나이에 쓴다)
 * @param samples 심박 표본과 위치로 셈한 이동 표본
 * @param gps 위치 점
 * @param recoveryHeartRates 종료 뒤 회복 심박(종료 시각 기준)
 */
public record RunBodyRequest(
        String sessionId,
        String userId,
        String source,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        long activeMs,
        double distanceMeters,
        int birthYear,
        List<Sample> samples,
        List<GpsPoint> gps,
        List<RecoveryHeartRate> recoveryHeartRates) {

    public RunBodyRequest {
        samples = List.copyOf(samples);
        gps = List.copyOf(gps);
        recoveryHeartRates = List.copyOf(recoveryHeartRates);
    }

    /**
     * 센서 표본 한 점. 심박 표본이면 heartRate만, 이동 표본이면 speed·distance만 있다(ai가 1초 칸으로 모은다). 케이던스는 저장하지 않아 없다.
     *
     * @param tMs 운동 시간 축 밀리초
     * @param heartRate 심박(bpm)
     * @param speed 직전 위치 점에서 셈한 속도(m/s)
     * @param distance 직전 위치 점에서 늘어난 거리(m)
     */
    public record Sample(long tMs, Integer heartRate, Double speed, Double distance) {}

    /**
     * 위치 점 한 개.
     *
     * @param tMs 운동 시간 축 밀리초
     * @param lat 위도
     * @param lon 경도
     * @param acc 정확도 반경(m)
     */
    public record GpsPoint(long tMs, double lat, double lon, Double acc) {}

    /**
     * 종료 뒤 심박 한 개.
     *
     * @param tMs 종료 시각 뒤 밀리초
     * @param heartRate 심박(bpm)
     */
    public record RecoveryHeartRate(long tMs, int heartRate) {}
}
