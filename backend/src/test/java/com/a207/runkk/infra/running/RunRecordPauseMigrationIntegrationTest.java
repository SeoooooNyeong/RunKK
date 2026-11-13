package com.a207.runkk.infra.running;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 부록 F F.5.3 V21: V21 직전까지 올린 PostgreSQL에 기존 기록을 두고 V21을 적용하면 기존 행은 정지 없음('', 0)으로 채워지고,
 * ck_run_record_pause가 음수·자유런 밖 정지를 막으며, 부분 색인 idx_run_record_member_valid_saved가 생긴다. Docker가 없는
 * PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V21 정지 구간 칼럼 마이그레이션(PostgreSQL)")
class RunRecordPauseMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 7, 0));
    private static final Timestamp T1 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 7, 30));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaBeforeV21() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        // V20(S1)은 run_record를 건드리지 않으므로 S0의 V19까지만 올리고 V21에서 함께 적용해도 된다
        migrateTo("19");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                // V29(analytics)가 ai 계정 비밀번호를 플레이스홀더로 받는다(application-integration.yml과 같은 값)
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .target(version)
                .load()
                .migrate();
    }

    private void record(String id, String mode, String cardKind, Long battleId) {
        jdbc.update(
                "INSERT INTO run_record (record_id, member_id, started_at, ended_at,"
                        + " distance_meters, elapsed_millis, average_pace_seconds, mode, card_kind,"
                        + " battle_id, valid, judged, score, point_count, route_purged,"
                        + " unstable_segments, heart_rate_purged, new_records, saved_at)"
                        + " VALUES (?, 1, ?, ?, 5000, 1800000, 360, ?, ?, ?, true, true, 38.3, 0,"
                        + " false, '', false, '', ?)",
                id,
                T0,
                T1,
                mode,
                cardKind,
                battleId,
                T1);
    }

    @Test
    @DisplayName("기존 행은 pauses '' · paused_millis 0으로 채워지고 새 행은 정지 구간을 저장한다")
    void existingRowsGetNoPauses() {
        record("00000000-0000-4000-8000-000000000001", "FREE", "FREE_RUN", null);
        record("00000000-0000-4000-8000-000000000002", "BATTLE", "BATTLE_RUN", 7L);

        migrateTo("21");

        assertThat(
                        jdbc.queryForList(
                                "SELECT pauses, paused_millis FROM run_record ORDER BY record_id"))
                .extracting(row -> row.get("pauses"), row -> row.get("paused_millis"))
                .containsOnly(org.assertj.core.groups.Tuple.tuple("", 0L));

        jdbc.update(
                "UPDATE run_record SET pauses = ?, paused_millis = 600000 WHERE mode = 'FREE'",
                "2026-10-22T07:10;2026-10-22T07:20");
        Map<String, Object> free =
                jdbc.queryForMap(
                        "SELECT pauses, paused_millis FROM run_record WHERE mode = 'FREE'");
        assertThat(free.get("paused_millis")).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("ck_run_record_pause: 자유런 밖 정지·음수 정지는 CHECK 위반")
    void pauseCheck() {
        record("00000000-0000-4000-8000-000000000001", "FREE", "FREE_RUN", null);
        record("00000000-0000-4000-8000-000000000002", "BATTLE", "BATTLE_RUN", 7L);
        migrateTo("21");

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET paused_millis = 1000 WHERE mode ="
                                                + " 'BATTLE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET paused_millis = -1 WHERE mode ="
                                                + " 'FREE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V43(MR !91 BR-1): 크루런 정지는 받고 대결 정지·음수 정지는 여전히 CHECK 위반")
    void crewRunPauseAllowedFromV43() {
        record("00000000-0000-4000-8000-000000000001", "FREE", "FREE_RUN", null);
        record("00000000-0000-4000-8000-000000000002", "BATTLE", "BATTLE_RUN", 7L);
        record("00000000-0000-4000-8000-000000000003", "CREW_RUN", "CREW_RUN", null);
        migrateTo("42");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET paused_millis = 1000 WHERE mode ="
                                                + " 'CREW_RUN'"))
                .as("V42까지는 자유런만")
                .isInstanceOf(DataIntegrityViolationException.class);

        migrateTo("43");

        assertThat(
                        jdbc.update(
                                "UPDATE run_record SET pauses = ?, paused_millis = 600000 WHERE"
                                        + " mode = 'CREW_RUN'",
                                "2026-10-22T07:10;2026-10-22T07:20"))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET paused_millis = 1000 WHERE mode ="
                                                + " 'BATTLE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET paused_millis = -1 WHERE mode ="
                                                + " 'CREW_RUN'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName(
            "부분 색인 idx_run_record_member_valid_saved (member_id, saved_at, record_id) WHERE valid")
    void partialIndex() {
        migrateTo("21");
        String definition =
                jdbc.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE indexname ="
                                + " 'idx_run_record_member_valid_saved'",
                        String.class);
        assertThat(definition)
                .contains("(member_id, saved_at, record_id)")
                .containsIgnoringCase("WHERE valid");
    }

    @Test
    @DisplayName("총괄 결정 7-3: tier_applied는 기존 행 true, 새 행 기본 false, 보정 대기 색인이 생긴다")
    void tierAppliedBackfill() {
        record("00000000-0000-4000-8000-000000000001", "FREE", "FREE_RUN", null);
        migrateTo("21");
        record("00000000-0000-4000-8000-000000000002", "FREE", "FREE_RUN", null);

        assertThat(
                        jdbc.queryForList(
                                "SELECT tier_applied FROM run_record ORDER BY record_id",
                                Boolean.class))
                .containsExactly(true, false);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT indexdef FROM pg_indexes WHERE indexname ="
                                        + " 'idx_run_record_tier_pending'",
                                String.class))
                .contains("(saved_at)")
                .containsIgnoringCase("NOT tier_applied");
    }
}
