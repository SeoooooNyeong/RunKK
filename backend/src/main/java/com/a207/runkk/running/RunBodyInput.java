package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 런바디 분석 요청을 만들 러닝 기록 재료(설계 05-19 2.2 running 추가, D-444). 엔티티 대신 runbody.RunBodyRequestFactory가
 * 읽는다. 케이던스·속도는 저장하지 않아 없다.
 *
 * @param recordId 기록 ID
 * @param memberId 기록 주인
 * @param startedAt 시작 일시
 * @param endedAt 종료 일시
 * @param distance 기록 거리
 * @param elapsed 운동 시간(정지 제외)
 * @param startDevice 시작 기기
 * @param valid 유효 러닝(거짓이면 집계 제외 기록, FR-225)
 * @param heartRates 심박 표본(시간순, 종료 뒤 회복 심박 포함 가능)
 * @param points 위치 점(시간순, 파기했으면 빈 목록)
 * @param pauses 정지 구간(시간순)
 */
public record RunBodyInput(
        String recordId,
        long memberId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Distance distance,
        Duration elapsed,
        StartDevice startDevice,
        boolean valid,
        List<HeartRateSample> heartRates,
        List<LocationPoint> points,
        List<PauseInterval> pauses) {

    public RunBodyInput {
        heartRates = List.copyOf(heartRates);
        points = List.copyOf(points);
        pauses = List.copyOf(pauses);
    }
}
