package com.a207.runkk.infra.chat;

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
 * 피그마 맞춤 W5 V53(S15P31A207-296, C7): 반응 CHECK 허용, 메시지 종류(kind)·러닝 거리 열과 제약. 옛 메시지는 TEXT가 된다.
 * Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V53 채팅 반응 CHECK·시스템 메시지 마이그레이션(PostgreSQL)")
class CrewChatW5MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 11, 9, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchema() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("49");
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

    private long crew() {
        return jdbc.queryForObject(
                "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                        + " invite_code_expires_at, created_at, version)"
                        + " VALUES ('더블유크루', '', 'PUBLIC', 'FREE', 'W00000', ?, ?, 0) RETURNING id",
                Long.class,
                T0,
                T0);
    }

    private long text(long crewId, String clientMessageId) {
        return jdbc.queryForObject(
                "INSERT INTO crew_chat_message (crew_id, author_id, client_message_id, content,"
                        + " sent_at) VALUES (?, 3, ?, '안녕', ?) RETURNING id",
                Long.class,
                crewId,
                clientMessageId,
                T0);
    }

    private int system(long crewId, String kind, Long meters, String clientMessageId) {
        return jdbc.update(
                "INSERT INTO crew_chat_message (crew_id, author_id, client_message_id, content,"
                        + " sent_at, kind, run_distance_meters) VALUES (?, 4, ?, '러닝', ?, ?, ?)",
                crewId,
                clientMessageId,
                T0,
                kind,
                meters);
    }

    @Test
    @DisplayName("V53 전 메시지는 TEXT·거리 null, 반응은 CHECK도 받고 모르는 종류는 거부한다")
    void reactionCheckAndExistingRows() {
        long crewId = crew();
        long old = text(crewId, "u1");
        String insert =
                "INSERT INTO crew_chat_reaction (message_id, member_id, kind, reacted_at)"
                        + " VALUES (?, ?, ?, ?)";
        assertThatThrownBy(() -> jdbc.update(insert, old, 4, "CHECK", T0))
                .as("V53 전에는 CHECK가 없다")
                .isInstanceOf(DataIntegrityViolationException.class);

        migrateTo("53");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT kind FROM crew_chat_message WHERE id = ?",
                                String.class,
                                old))
                .isEqualTo("TEXT");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT run_distance_meters FROM crew_chat_message WHERE id = ?",
                                Long.class,
                                old))
                .isNull();
        assertThat(jdbc.update(insert, old, 4, "CHECK", T0)).isEqualTo(1);
        assertThat(jdbc.update(insert, old, 4, "LIKE", T0)).isEqualTo(1);
        assertThat(jdbc.update(insert, old, 4, "HEART", T0)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insert, old, 5, "WOW", T0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName(
            "RUN_FINISHED는 거리 0 이상이 있어야 하고, TEXT는 거리가 없어야 하며, 모르는 종류는 거부한다. 같은 기록 ID 메시지는 한 번만")
    void messageKindConstraints() {
        migrateTo("53");
        long crewId = crew();
        assertThat(system(crewId, "RUN_FINISHED", 5_021L, "r1")).isEqualTo(1);
        assertThatThrownBy(() -> system(crewId, "RUN_FINISHED", null, "r2"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> system(crewId, "RUN_FINISHED", -1L, "r3"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> system(crewId, "TEXT", 100L, "r4"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> system(crewId, "NOTICE", null, "r5"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> system(crewId, "RUN_FINISHED", 5_021L, "r1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_crew_chat_client");
    }
}
