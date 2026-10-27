package com.a207.runkk.infra.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
 * 부록 F F.5.3 V24(D-104, D-149): report_case·content_report 표, 대상 고유 제약, 같은 신고자 고유 색인(익명화 행 제외),
 * 상태·종류·사유 CHECK, (status, opened_at) 색인, 사례 삭제 CASCADE. TC-6617. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V24 콘텐츠 신고 마이그레이션(PostgreSQL)")
class ReportMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 23, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV23() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("23");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long reportCase(String kind, long targetId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO report_case (target_kind, target_id, crew_id, status, opened_at,"
                        + " version) VALUES (?, ?, 7, ?, ?, 0) RETURNING id",
                Long.class,
                kind,
                targetId,
                status,
                T0);
    }

    private void report(long caseId, Long reporterId, String reason) {
        jdbc.update(
                "INSERT INTO content_report (case_id, reporter_id, reason, reported_at)"
                        + " VALUES (?, ?, ?, ?)",
                caseId,
                reporterId,
                reason,
                T0);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("TC-6617: 같은 대상 사례 두 행·같은 사례 같은 신고자 두 행은 위반, 신고자가 빈(익명화) 행끼리는 저장")
    void uniqueConstraints() {
        assertThat(
                        count(
                                "SELECT count(*) FROM information_schema.tables WHERE table_name ="
                                        + " 'report_case'"))
                .isZero();
        migrateTo("24");
        long c = reportCase("CHEER", 100, "RECEIVED");
        assertThatThrownBy(() -> reportCase("CHEER", 100, "RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_report_case_target");
        reportCase("BOARD", 100, "RECEIVED");
        report(c, 4L, "ABUSE");
        assertThatThrownBy(() -> report(c, 4L, "SPAM"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_content_report_reporter");
        report(c, null, "ABUSE");
        report(c, null, "SPAM");
        assertThat(count("SELECT count(*) FROM content_report WHERE case_id = ?", c)).isEqualTo(3);
        assertThat(count("SELECT open_reporter_count FROM report_case WHERE id = ?", c))
                .as("열린 신고자 수 기본값 0")
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM pg_indexes WHERE indexname IN"
                                        + " ('idx_report_case_status_opened_at',"
                                        + " 'uq_content_report_reporter',"
                                        + " 'idx_content_report_reporter')"))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("CHECK: 모르는 대상 종류·상태·사유는 위반, 신고내용 200자(이모지 포함)는 저장·201자는 위반, 사례를 지우면 신고도 지워진다")
    void checksAndCascade() {
        migrateTo("24");
        assertThatThrownBy(() -> reportCase("PROFILE", 1, "RECEIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> reportCase("CHEER", 1, "CLOSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        long c = reportCase("CHAT_MESSAGE", 501, "AUTO_HIDDEN");
        assertThatThrownBy(() -> report(c, 1L, "FAKE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "INSERT INTO content_report (case_id, reporter_id, reason, detail, reported_at)"
                        + " VALUES (?, 2, 'OTHER', ?, ?)",
                c,
                "🏃".repeat(200),
                T0);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO content_report (case_id, reporter_id, reason,"
                                            + " detail, reported_at) VALUES (?, 3, 'OTHER', ?, ?)",
                                        c,
                                        "가".repeat(201),
                                        T0))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM report_case WHERE id = ?", c);
        assertThat(count("SELECT count(*) FROM content_report WHERE case_id = ?", c)).isZero();
    }

    @Test
    @DisplayName("V24 뒤 V25·V26까지 이어서 적용된다(번호 순서)")
    void laterMigrationsStillApply() {
        migrateTo("26");
        assertThat(
                        count(
                                "SELECT count(*) FROM flyway_schema_history WHERE version = '24'"
                                        + " AND success"))
                .isEqualTo(1);
    }
}
