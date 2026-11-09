package com.a207.runkk.infra.aicoach;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class AiCoachMigrationTest {
    @Test
    void existingAgreementsAreStillVersionOne() {
        var source =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "sa",
                        "");
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE member (id BIGINT PRIMARY KEY)");
        jdbc.execute(
                "CREATE TABLE member_consent (member_id BIGINT, item VARCHAR(20), agreed BOOLEAN)");
        jdbc.update("INSERT INTO member VALUES (42)");
        jdbc.update("INSERT INTO member_consent VALUES (42, 'LLM_TRANSFER', TRUE)");

        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V46__ai_coach.sql"))
                .execute(source);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT notice_version FROM member_consent WHERE member_id = 42",
                                Integer.class))
                .isEqualTo(1);
        jdbc.update(
                "INSERT INTO coach_daily_usage (member_id, usage_day, answered_count) "
                        + "VALUES (42, DATE '2026-11-09', 0)");
        jdbc.update("DELETE FROM member WHERE id = 42");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM coach_daily_usage", Integer.class))
                .isZero();
    }
}
