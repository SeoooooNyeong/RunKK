package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
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
 * V35_2 이벤트 마이그레이션(S15P31A207-275): 시드 이벤트 둘, 적립 사유 EVENT, 기간 제약을 PostgreSQL로 확인한다. Docker가 없으면
 * 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V35_2 이벤트 마이그레이션(PostgreSQL)")
class EventMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    @DisplayName("시드 이벤트 둘(계약 1절 값), EVENT 적립 사유 허용, 모르는 사유와 거꾸로 된 기간 거부")
    void seedsAndConstraints() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .target("35.2")
                .load()
                .migrate();
        JdbcTemplate jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));

        List<Map<String, Object>> seeds =
                jdbc.queryForList(
                        "SELECT slug, kind, config, starts_at::text AS s, ends_at::text AS e,"
                            + " detail_url, sort_order, published FROM event ORDER BY sort_order");
        assertThat(seeds).hasSize(2);
        assertThat(seeds.get(0))
                .containsEntry("slug", "coach-challenge")
                .containsEntry("kind", "COACH_CELEB")
                .containsEntry("config", "{\"celebrityIds\":[11,12],\"pointPerWin\":500}")
                .containsEntry("s", "2026-11-08 00:00:00")
                .containsEntry("e", "2026-11-16 23:59:59")
                .containsEntry("detail_url", "/events/coach-challenge/")
                .containsEntry("published", true);
        assertThat(seeds.get(1))
                .containsEntry("slug", "beta-1km")
                .containsEntry("kind", "RUN_DISTANCE")
                .containsEntry("config", "{\"minDistanceMeters\":1000}");

        jdbc.update("INSERT INTO reward_wallet (member_id, balance, version) VALUES (1, 0, 0)");
        jdbc.update(
                "INSERT INTO reward_accrual (member_id, reason, source_id, amount, accrued_at)"
                        + " VALUES (1, 'EVENT', '1:celebrity-11', 500, now())");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO reward_accrual (member_id, reason, source_id,"
                                                + " amount, accrued_at) VALUES (1, 'BONUS', 'x',"
                                                + " 1, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE event SET ends_at = starts_at WHERE slug ="
                                                + " 'beta-1km'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
