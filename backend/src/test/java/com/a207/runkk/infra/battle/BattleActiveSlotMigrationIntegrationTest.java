package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 코드 리뷰 3차 B-L1: V15 backfill이 회원마다 가장 최근 활성 대결에만 칸을 채워, 중복 활성 대결이 있던 DB에서는 남은 활성 대결이 활성 → 활성으로 넘어갈
 * 때(Battle.moveTo가 칸을 채움) 고유 제약 충돌이 났다. V14까지 올린 PostgreSQL에 중복 활성 대결을 넣고 V16에서 충돌을 재현한 뒤, V17이 남은
 * 활성 대결을 CANCELLED로 보내고 칸 규칙(활성 ⇔ 칸 = 회원 ID)을 엔티티와 맞추는지 확인한다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V17 활성 대결 칸 정리 마이그레이션(PostgreSQL)")
class BattleActiveSlotMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        DriverManagerDataSource dataSource =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("14");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long battle(String status, int minutesAfterT0) {
        return jdbc.queryForObject(
                "INSERT INTO battle (type, distance_meters, status, created_at, corrected,"
                    + " winner_opponent, version) VALUES ('RANDOM', 3000, ?, ?, false, false, 0)"
                    + " RETURNING id",
                Long.class,
                status,
                Timestamp.valueOf(T0.plusMinutes(minutesAfterT0)));
    }

    private void participant(long battleId, long memberId) {
        jdbc.update(
                "INSERT INTO battle_participant (battle_id, member_id, overtakes, forfeited,"
                        + " new_record) VALUES (?, ?, 0, false, false)",
                battleId,
                memberId);
    }

    /** Battle.moveTo(활성 상태)가 하는 칸 맞추기와 같은 갱신. */
    private void moveToActive(long battleId, String next) {
        jdbc.update("UPDATE battle SET status = ? WHERE id = ?", next, battleId);
        jdbc.update(
                "UPDATE battle_participant SET active_member_id = member_id WHERE battle_id = ?",
                battleId);
    }

    private String statusOf(long battleId) {
        return jdbc.queryForObject(
                "SELECT status FROM battle WHERE id = ?", String.class, battleId);
    }

    private List<Map<String, Object>> slotMismatches() {
        return jdbc.queryForList(
                "SELECT p.id FROM battle_participant p JOIN battle b ON b.id = p.battle_id WHERE"
                        + " (b.status IN ('WAITING_START', 'IN_PROGRESS', 'COUNTDOWN')) <>"
                        + " (p.active_member_id IS NOT NULL AND p.active_member_id = p.member_id)");
    }

    @Test
    @DisplayName("V16까지는 남은 활성 대결의 활성 → 활성 전이가 고유 제약에 걸리고, V17 뒤에는 정리돼 충돌하지 않는다")
    void leftoverActiveBattle_isCancelledAndSlotsMatchEntityRule() {
        // 회원 1이 활성 대결 둘(older: 1·2, newer: 1·3). 회원 2의 최신 활성 대결은 older다.
        long older = battle("IN_PROGRESS", 0);
        participant(older, 1);
        participant(older, 2);
        long newer = battle("WAITING_START", 5);
        participant(newer, 1);
        participant(newer, 3);
        long finished = battle("FINISHED", -60);
        participant(finished, 1);

        migrateTo("16");
        assertThat(slotMismatches()).hasSize(1); // older의 회원 1 칸만 비었다
        assertThatThrownBy(() -> moveToActive(older, "COUNTDOWN"))
                .isInstanceOf(DuplicateKeyException.class);

        migrateTo("17");
        assertThat(statusOf(older)).isEqualTo("CANCELLED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT closed_at IS NOT NULL FROM battle WHERE id = ?",
                                Boolean.class,
                                older))
                .isTrue();
        assertThat(statusOf(newer)).isEqualTo("WAITING_START");
        assertThat(statusOf(finished)).isEqualTo("FINISHED");
        assertThat(slotMismatches()).isEmpty();
        assertThat(
                        jdbc.queryForList(
                                "SELECT active_member_id FROM battle_participant WHERE"
                                        + " active_member_id IS NOT NULL ORDER BY 1",
                                Long.class))
                .containsExactly(1L, 3L);

        moveToActive(newer, "IN_PROGRESS"); // 남은 최신 대결의 활성 → 활성 전이는 그대로 된다
        assertThat(statusOf(newer)).isEqualTo("IN_PROGRESS");
        long next = battle("WAITING_START", 10); // 정리로 풀린 회원 2는 새 대결을 가질 수 있다
        participant(next, 2);
        moveToActive(next, "WAITING_START");
        assertThat(slotMismatches()).isEmpty();
    }

    @Test
    @DisplayName("중복 활성 대결이 없던 DB는 V17이 아무것도 바꾸지 않는다")
    void noDuplicates_isUntouched() {
        long a = battle("IN_PROGRESS", 0);
        participant(a, 1);
        participant(a, 2);
        long b = battle("RESULT_PENDING", 1);
        participant(b, 3);

        migrateTo("17");

        assertThat(statusOf(a)).isEqualTo("IN_PROGRESS");
        assertThat(statusOf(b)).isEqualTo("RESULT_PENDING");
        assertThat(slotMismatches()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT version FROM battle WHERE id = ?", Long.class, a))
                .isZero();
    }
}
