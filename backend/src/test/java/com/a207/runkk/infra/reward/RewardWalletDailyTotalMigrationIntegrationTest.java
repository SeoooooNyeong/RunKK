package com.a207.runkk.infra.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
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
 * 부록 F F.5.3 V26(D-109): V25까지 올린 PostgreSQL에 기존 지갑·적립 내역을 두고 V26을 적용하면 집계 칼럼이 회원별 가장 최근 적립일과 그날
 * 합계로 채워지고, 적립이 없는 지갑은 (null, 0)이며, 음수 합계는 CHECK 위반이다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V26 리워드 집계 칼럼 마이그레이션(PostgreSQL)")
class RewardWalletDailyTotalMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaBeforeV26() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("25");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void wallet(long memberId, int balance) {
        jdbc.update(
                "INSERT INTO reward_wallet (member_id, balance, version) VALUES (?, ?, 0)",
                memberId,
                balance);
    }

    private void accrual(long memberId, String source, int amount, LocalDateTime at) {
        jdbc.update(
                "INSERT INTO reward_accrual (member_id, reason, source_id, amount, accrued_at)"
                        + " VALUES (?, 'VALID_RUN', ?, ?, ?)",
                memberId,
                source,
                amount,
                Timestamp.valueOf(at));
    }

    private Map<String, Object> columnsOf(long memberId) {
        return jdbc.queryForMap(
                "SELECT last_accrual_date, last_accrual_date_amount FROM reward_wallet"
                        + " WHERE member_id = ?",
                memberId);
    }

    @Test
    @DisplayName("기존 지갑은 가장 최근 적립일과 그날 합계로, 적립이 없는 지갑은 (null, 0)으로 채운다")
    void backfillsLatestDayTotal() {
        wallet(1, 260);
        accrual(1, "r1", 100, LocalDateTime.of(2026, 10, 20, 9, 0));
        accrual(1, "r2", 120, LocalDateTime.of(2026, 10, 21, 7, 0));
        accrual(1, "r3", 40, LocalDateTime.of(2026, 10, 21, 23, 59, 59));
        wallet(2, 0);

        migrateTo("26");

        Map<String, Object> one = columnsOf(1);
        assertThat(((Date) one.get("last_accrual_date")).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(one.get("last_accrual_date_amount")).isEqualTo(160);
        Map<String, Object> two = columnsOf(2);
        assertThat(two.get("last_accrual_date")).isNull();
        assertThat(two.get("last_accrual_date_amount")).isEqualTo(0);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE indexname ="
                                        + " 'idx_reward_accrual_member_accrued'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("새 지갑은 기본값 0이고 음수 합계는 CHECK 위반이다")
    void defaultsAndCheck() {
        migrateTo("26");
        wallet(3, 0);
        assertThat(columnsOf(3).get("last_accrual_date_amount")).isEqualTo(0);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE reward_wallet SET last_accrual_date_amount = -1"
                                                + " WHERE member_id = 3"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
