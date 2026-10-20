package com.a207.runkk.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;

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

    /** 두 값 모두 now. */
    public static Timestamps createdAt(LocalDateTime now) {
        return new Timestamps(now, now);
    }

    /** updatedAt만 바꾼 새 값. */
    public Timestamps touched(LocalDateTime now) {
        return new Timestamps(createdAt, now);
    }
}
