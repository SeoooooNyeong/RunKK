package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * V35_3 운영자 공지 알림 마이그레이션(S15P31A207-275): NOTICE 종류, 제목 열, 300자 대상 ID를 PostgreSQL로 확인한다. Docker가
 * 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V35_3 공지 알림 마이그레이션(PostgreSQL)")
class NoticeMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    @Test
    @DisplayName("NOTICE와 제목, 긴 링크 대상 ID를 받고 모르는 종류는 거부, 기존 종류는 제목 없이 그대로")
    void noticeTypeTitleAndLongTarget() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .target("35.3")
                .load()
                .migrate();
        JdbcTemplate jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));

        String link = "https://docs.google.com/forms/d/e/" + "x".repeat(250);
        jdbc.update(
                "INSERT INTO notification (recipient_id, type, content, occurred_at, is_read,"
                        + " target_screen, target_id, title) VALUES (1, 'NOTICE', '본문', now(),"
                        + " false, 'EXTERNAL_LINK', ?, '코치님 이벤트 당첨')",
                link);
        jdbc.update(
                "INSERT INTO notification (recipient_id, type, content, occurred_at, is_read)"
                        + " VALUES (1, 'PLAN_MISSED', '오늘 계획', now(), false)");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT target_id FROM notification WHERE type = 'NOTICE'",
                                String.class))
                .isEqualTo(link);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM notification WHERE title IS NULL",
                                Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO notification (recipient_id, type, content,"
                                                + " occurred_at, is_read) VALUES (1, 'ANNOUNCE',"
                                                + " 'x', now(), false)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
