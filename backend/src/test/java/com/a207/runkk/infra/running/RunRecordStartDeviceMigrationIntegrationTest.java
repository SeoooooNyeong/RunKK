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
 * V33(사용자 결정 W23, D-378): run_record에 시작 기기 칼럼(start_device)을 더한다. V32까지 올린 PostgreSQL에 기존 기록을 두고
 * V33을 적용하면 기존 행은 PHONE이고, 칼럼을 적지 않는 INSERT도 PHONE이며, ck_run_record_start_device가 PHONE·WATCH 밖 값을
 * 막는다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V33 시작 기기 칼럼 마이그레이션(PostgreSQL)")
class RunRecordStartDeviceMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 7, 0));
    private static final Timestamp T1 = Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 7, 30));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV32() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("32");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .load()
                .migrate();
    }

    /** start_device를 적지 않는 INSERT(옛 서버 코드와 같은 칼럼). */
    private void record(String id) {
        jdbc.update(
                "INSERT INTO run_record (record_id, member_id, started_at, ended_at,"
                        + " distance_meters, elapsed_millis, average_pace_seconds, mode, card_kind,"
                        + " valid, judged, score, point_count, route_purged,"
                        + " unstable_segments, heart_rate_purged, new_records, saved_at)"
                        + " VALUES (?, 1, ?, ?, 5000, 1800000, 360, 'FREE', 'FREE_RUN', true, true,"
                        + " 38.3, 0, false, '', false, '', ?)",
                id,
                T0,
                T1,
                T1);
    }

    private String deviceOf(String id) {
        return jdbc.queryForObject(
                "SELECT start_device FROM run_record WHERE record_id = ?", String.class, id);
    }

    @Test
    @DisplayName("기존 행과 칼럼을 적지 않은 새 행은 PHONE이다")
    void existingAndOmittedRowsArePhone() {
        record("00000000-0000-4000-8000-000000000001");

        migrateTo("33");
        record("00000000-0000-4000-8000-000000000002");

        assertThat(deviceOf("00000000-0000-4000-8000-000000000001")).isEqualTo("PHONE");
        assertThat(deviceOf("00000000-0000-4000-8000-000000000002")).isEqualTo("PHONE");
    }

    @Test
    @DisplayName("ck_run_record_start_device: WATCH는 받고, NULL·모르는 값은 막는다")
    void startDeviceCheck() {
        record("00000000-0000-4000-8000-000000000001");
        migrateTo("33");

        jdbc.update("UPDATE run_record SET start_device = 'WATCH'");
        assertThat(deviceOf("00000000-0000-4000-8000-000000000001")).isEqualTo("WATCH");
        assertThatThrownBy(() -> jdbc.update("UPDATE run_record SET start_device = 'TABLET'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE run_record SET start_device = NULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
