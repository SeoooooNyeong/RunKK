package com.a207.runkk.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 생성·수정 시각(설계 6.1.1, D-48). 불변 record이고 엔티티는 상태를 바꾸는 연산 끝에 {@code timestamps =
 * timestamps.touched(now)}로 바꿔 끼운다. BaseEntity 상속 대신 @Embeddable로 포함한다(1.3.7).
 *
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각, createdAt 이후
 */
@Embeddable
public record Timestamps(
        @Column(name = "created_at", nullable = false) LocalDateTime createdAt,
        @Column(name = "updated_at", nullable = false) LocalDateTime updatedAt) {

    public Timestamps {
        if (createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "수정 시각은 생성 시각 이후여야 한다: " + createdAt + ", " + updatedAt);
        }
    }

    /** 두 값 모두 now(마이크로초까지). */
    public static Timestamps createdAt(LocalDateTime now) {
        LocalDateTime at = toColumnPrecision(now);
        return new Timestamps(at, at);
    }

    /** updatedAt만 바꾼 새 값(마이크로초까지). */
    public Timestamps touched(LocalDateTime now) {
        return new Timestamps(createdAt, toColumnPrecision(now));
    }

    /**
     * DB 열(timestamp(6))과 같은 마이크로초까지 자른다. 시계가 100ns 자리까지 주면 DB가 저장하며 반올림해, 다시 읽은 생성 시각이 같은 시계
     * 눈금(Windows 약 1~15.6ms) 안의 다음 now보다 늦어져 touched가 거부됐다(S15P31A207-300 흔들리는 시험).
     */
    private static LocalDateTime toColumnPrecision(LocalDateTime now) {
        return now == null ? null : now.truncatedTo(ChronoUnit.MICROS);
    }
}
