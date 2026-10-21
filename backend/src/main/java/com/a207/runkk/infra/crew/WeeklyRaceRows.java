package com.a207.runkk.infra.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * weekly_race·race_contribution 테이블 매핑(JpaWeeklyRaceRepository 안에서만 쓴다). 합산은 엔티티로 읽고-더하고-쓰지 않고 원자적
 * SQL로 한다(6.2 A-12). 매핑은 스키마(시험 프로필의 자동 생성, 운영의 ddl validate)를 위해 둔다.
 */
final class WeeklyRaceRows {

    private WeeklyRaceRows() {}

    /** 크루 한 곳의 한 주 합계. */
    @Entity(name = "WeeklyRaceRow")
    @Table(
            name = "weekly_race",
            uniqueConstraints =
                    @UniqueConstraint(
                            name = "uq_weekly_race",
                            columnNames = {"crew_id", "week_start"}))
    static class Race {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "crew_id", nullable = false)
        private long crewId;

        @Column(name = "week_start", nullable = false)
        private LocalDate weekStart;

        @Column(name = "total_meters", nullable = false)
        private long totalMeters;

        protected Race() {}
    }

    /** 기록 하나의 기여. (크루, 주, 기록)은 한 번만 들어간다(이중 합산 방지). */
    @Entity(name = "RaceContributionRow")
    @Table(
            name = "race_contribution",
            uniqueConstraints =
                    @UniqueConstraint(
                            name = "uq_race_contribution",
                            columnNames = {"crew_id", "week_start", "record_id"}),
            indexes = @Index(name = "idx_race_contribution_record", columnList = "record_id"))
    static class Contribution {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "crew_id", nullable = false)
        private long crewId;

        @Column(name = "week_start", nullable = false)
        private LocalDate weekStart;

        @Column(name = "member_id")
        private Long memberId;

        @Column(name = "record_id", nullable = false, length = 36)
        private String recordId;

        @Column(name = "meters", nullable = false)
        private long meters;

        protected Contribution() {}

        Long memberId() {
            return memberId;
        }

        String recordId() {
            return recordId;
        }

        long meters() {
            return meters;
        }
    }
}
