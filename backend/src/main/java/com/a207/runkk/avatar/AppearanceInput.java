package com.a207.runkk.avatar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 체형 계산 입력(설계 6.1.8 AppearanceCalculator, 6.2 A-16).
 *
 * @param runsIn7Days 최근 7일 유효 러닝 횟수
 * @param lastValidRunAt 마지막 유효 러닝 시작 일시, 한 번도 없으면 null
 * @param previous 이전 체형
 * @param today 오늘
 */
public record AppearanceInput(
        int runsIn7Days, LocalDateTime lastValidRunAt, BodyShape previous, LocalDate today) {

    public AppearanceInput {
        if (runsIn7Days < 0 || previous == null || today == null) {
            throw new IllegalArgumentException("체형 계산 입력 오류");
        }
    }

    Optional<LocalDateTime> lastRun() {
        return Optional.ofNullable(lastValidRunAt);
    }
}
