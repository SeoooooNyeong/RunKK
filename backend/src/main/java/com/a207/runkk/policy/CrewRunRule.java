package com.a207.runkk.policy;

import com.a207.runkk.common.StandardDistance;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 크루런 운영값(설계 6.1.9). CREW_RUN_LOBBY_OPEN(대기실 개방·알림·최소 준비 인원)과 CREW_RUN_END_RULE(km당 분)을 합친 값이다.
 *
 * @param lobbyOpenBefore 출발 전 대기실 개방
 * @param reminderBefore 출발 전 알림
 * @param minReady 최소 준비 인원
 * @param minutesPerKm 종료 기준 km당 분
 */
public record CrewRunRule(
        Duration lobbyOpenBefore, Duration reminderBefore, int minReady, int minutesPerKm) {

    /** 출발 시각 + minutesPerKm × km. */
    public LocalDateTime closesAt(LocalDateTime departedAt, StandardDistance d) {
        long seconds =
                Math.round(Duration.ofMinutes(minutesPerKm).toSeconds() * d.distance().exactKm());
        return departedAt.plusSeconds(seconds);
    }
}
