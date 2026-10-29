package com.a207.runkk.runimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.OptionalInt;

/**
 * 한 유형의 확정자 비율 스냅숏(설계 5.6.2 runimal.RunimalTypeShare, 표 runimal_type_share, D-246). 매일 0시
 * RunimalTypeService.recomputeShares가 16행을 다시 쓴다. 분자·분모는 지금 확정 유형이고 임시 유형만 있는 러너는 빠진다.
 */
@Entity
@Table(name = "runimal_type_share")
public class RunimalTypeShare {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private RunimalType type;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Column(name = "total_confirmed", nullable = false)
    private int totalConfirmed;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    protected RunimalTypeShare() {}

    private RunimalTypeShare(RunimalType type, int count, int total, LocalDateTime at) {
        this.type = type;
        this.confirmedCount = count;
        this.totalConfirmed = total;
        this.computedAt = at;
    }

    /** 확정자 수 count, 전체 확정자 total(0 이상, count ≤ total). 아니면 IllegalArgumentException. */
    public static RunimalTypeShare of(RunimalType t, int count, int total, LocalDateTime at) {
        if (t == null || at == null || count < 0 || total < count) {
            throw new IllegalArgumentException("유형 비율 값이 맞지 않다");
        }
        return new RunimalTypeShare(t, count, total, at);
    }

    /** 반올림한 정수 %(0.5는 올림). 확정자가 0이면 비어 있다(비율 줄을 보이지 않는다, FR-226). */
    public OptionalInt percent() {
        if (confirmedCount == 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(
                (int) ((confirmedCount * 200L + totalConfirmed) / (2L * totalConfirmed)));
    }

    public RunimalType type() {
        return type;
    }

    public int confirmedCount() {
        return confirmedCount;
    }

    public int totalConfirmed() {
        return totalConfirmed;
    }

    public LocalDateTime computedAt() {
        return computedAt;
    }
}
