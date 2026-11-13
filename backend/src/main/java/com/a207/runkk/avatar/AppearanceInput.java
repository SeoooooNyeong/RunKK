package com.a207.runkk.avatar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 체형 계산 입력(설계 6.1.8 AppearanceCalculator, 6.2 A-16).
 *
 * @param lastValidRunAt 마지막 유효 러닝 시작 일시, 한 번도 없으면 null
 * @param previous 이전 체형
 * @param today 오늘
 */
public record AppearanceInput(LocalDateTime lastValidRunAt, BodyShape previous, LocalDate today) {

    public AppearanceInput {
        if (previous == null || today == null) {
            throw new IllegalArgumentException("체형 계산 입력 오류");
        }
    }

    Optional<LocalDateTime> lastRun() {
        return Optional.ofNullable(lastValidRunAt);
    }
}
