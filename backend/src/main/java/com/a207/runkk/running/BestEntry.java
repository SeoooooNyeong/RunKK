package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 거리별 최고 기록 하나(설계 6.1.3 BestEntry(recordId, time 또는 distance, achievedOn, hasUnstable)). 기준 거리 기록은
 * 시간을, 최장 기록은 거리를 가진다. 저장 열은 밀리초·미터 값이다.
 *
 * @param recordId 기록 ID
 * @param elapsedMillis 기준 거리에 닿은 경과 밀리초(최장이면 null)
 * @param distanceMeters 최장 거리 미터(기준 거리면 null)
 * @param achievedOn 달성일
 * @param hasUnstable 그 구간에 신호 불안정 구간이 있었는가(FR-063 표시)
 */
@Embeddable
public record BestEntry(
        @Column(name = "record_id", length = 36) String recordId,
        @Column(name = "elapsed_millis") Long elapsedMillis,
        @Column(name = "distance_meters") Long distanceMeters,
        @Column(name = "achieved_on") LocalDate achievedOn,
        @Column(name = "has_unstable") Boolean hasUnstable) {

    static BestEntry ofTime(String recordId, Duration time, LocalDate on, boolean unstable) {
        return new BestEntry(recordId, time.toMillis(), null, on, unstable);
    }

    static BestEntry ofDistance(
            String recordId, Distance distance, LocalDate on, boolean unstable) {
        return new BestEntry(recordId, null, distance.meters(), on, unstable);
    }

    /** 기준 거리 기록의 시간. */
    public Optional<Duration> time() {
        return Optional.ofNullable(elapsedMillis).map(Duration::ofMillis);
    }

    /** 최장 기록의 거리. */
    public Optional<Distance> distance() {
        return Optional.ofNullable(distanceMeters).map(Distance::ofMeters);
    }
}
