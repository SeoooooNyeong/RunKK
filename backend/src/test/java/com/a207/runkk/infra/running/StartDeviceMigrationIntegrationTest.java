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
 * V33 시작 기기 열과 V35 제약 검증(W23, 코드 리뷰 12차 BE12-2): V33은 열을 상수 기본값으로 더하고 CHECK 제약을 NOT VALID로 걸어 기존 행을
 * 훑지 않는다(짧은 ACCESS EXCLUSIVE 잠금만). V35가 따로 커밋되는 마이그레이션에서 VALIDATE CONSTRAINT로 기존 행을 검사한다(SHARE UPDATE
 * EXCLUSIVE라 읽기·쓰기를 막지 않는다). Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V33·V35 run_record.start_device 마이그레이션(PostgreSQL)")
class StartDeviceMigrationIntegrationTest {

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 30, 7, 0));
    private static final Timestamp T1 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 30, 7, 30));

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV32WithOneRecord() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("32");
        jdbc.update(
                "INSERT INTO run_record (record_id, member_id, started_at, ended_at,"
                        + " distance_meters, elapsed_millis, average_pace_seconds, mode, card_kind,"
                        + " valid, judged, score, point_count, route_purged, unstable_segments,"
                        + " heart_rate_purged, new_records, saved_at) VALUES ('old-1', 1, ?, ?,"
                        + " 5000, 1800000, 360, 'FREE', 'FREE_RUN', true, true, 1.0, 10, false,"
                        + " '', false, '', ?)",
                T0,
                T1,
                T1);
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .target(version)
                .load()
                .migrate();
    }

    private Boolean validated() {
        return jdbc.queryForObject(
                "SELECT convalidated FROM pg_constraint WHERE conname ="
                        + " 'ck_run_record_start_device'",
                Boolean.class);
    }

    @Test
    @DisplayName("V33: 기존 기록은 PHONE이고 제약은 NOT VALID로 걸려 기존 행을 훑지 않는다")
    void v33_addsColumn_andNotValidCheck() {
        migrateTo("33");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT start_device FROM run_record WHERE record_id = 'old-1'",
                                String.class))
                .isEqualTo("PHONE");
        assertThat(validated()).isFalse();
        // NOT VALID여도 새로 쓰는 값은 검사한다
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET start_device = 'TABLE' WHERE"
                                                + " record_id = 'old-1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("V35: 제약을 검증해 VALID가 되고 PHONE·WATCH만 받는다")
    void v35_validatesCheck() {
        migrateTo("35");

        assertThat(validated()).isTrue();
        jdbc.update("UPDATE run_record SET start_device = 'WATCH' WHERE record_id = 'old-1'");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE run_record SET start_device = 'watch' WHERE"
                                                + " record_id = 'old-1'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
