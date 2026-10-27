package com.a207.runkk.infra;

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
 * TC-6612(부록 F F.5.3 V19): V18까지 올린 PostgreSQL에 기존 행을 두고 V19를 적용하면 v0.11 enum 값(알림 종류, 감사 행위·행위자
 * 역할, 크루 역할, 게시 종류, 참석 응답)이 저장되고 정의 밖 값은 CHECK 위반이며 기존 행은 그대로다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V19 enum CHECK 확장 마이그레이션(PostgreSQL)")
class EnumChecksV011MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV18() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("18");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void notification(String type) {
        jdbc.update(
                "INSERT INTO notification (recipient_id, type, content, occurred_at, is_read)"
                        + " VALUES (1, ?, '내용', ?, false)",
                type,
                T0);
    }

    private void audit(String action, String role) {
        jdbc.update(
                "INSERT INTO audit_record (recorded_at, actor_id, actor_role, action, target_id)"
                        + " VALUES (?, 'actor', ?, ?, 'target')",
                T0,
                role,
                action);
    }

    private long crew(String name) {
        return jdbc.queryForObject(
                "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                        + " invite_code_expires_at, created_at, version)"
                        + " VALUES (?, '', 'PUBLIC', 'FREE', ?, ?, ?, 0) RETURNING id",
                Long.class,
                name,
                name.substring(0, 6),
                T0,
                T0);
    }

    private void membership(long crewId, long memberId, String role) {
        jdbc.update(
                "INSERT INTO crew_membership (crew_id, member_id, role, joined_at, crew_tags)"
                        + " VALUES (?, ?, ?, ?, '')",
                crewId,
                memberId,
                role,
                T0);
    }

    private long post(long crewId, String kind) {
        return jdbc.queryForObject(
                "INSERT INTO crew_post (post_kind, crew_id, author_id, body, created_at, deleted,"
                        + " version) VALUES (?, ?, 1, '본문', ?, false, 0) RETURNING id",
                Long.class,
                kind,
                crewId,
                T0);
    }

    private void participation(long postId, long memberId, String answer) {
        jdbc.update(
                "INSERT INTO crew_run_participation (post_id, member_id, voted_at, answer,"
                        + " readiness, runner, quit) VALUES (?, ?, ?, ?, 'NOT_ENTERED', true,"
                        + " false)",
                postId,
                memberId,
                T0,
                answer);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    @DisplayName("새 값은 저장되고 정의 밖 값은 CHECK 위반, 기존 행은 그대로")
    void v19_widensEnumChecksOnly() {
        notification("KICKED");
        audit("KICK", "CREW_LEADER");
        long crewId = crew("CREW01-기존");
        membership(crewId, 1, "LEADER");
        long runPost = post(crewId, "CREW_RUN");
        participation(runPost, 1, "GOING");
        assertThatThrownBy(() -> notification("CREW_CHAT"))
                .isInstanceOf(DataIntegrityViolationException.class);

        migrateTo("19");

        assertThat(count("notification")).isEqualTo(1);
        assertThat(count("audit_record")).isEqualTo(1);
        assertThat(count("crew_membership")).isEqualTo(1);
        assertThat(count("crew_post")).isEqualTo(1);
        assertThat(count("crew_run_participation")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT answer FROM crew_run_participation", String.class))
                .isEqualTo("GOING");

        for (String type :
                new String[] {
                    "CREW_NOTICE",
                    "CREW_POLL",
                    "POLL_CLOSING_SOON",
                    "CREW_SCHEDULE",
                    "CREW_CHAT",
                    "CREW_GOAL_ACHIEVED",
                    "MANAGER_CHANGED",
                    "BACKGROUND_UNLOCKED",
                    "REPORT_RESOLVED"
                }) {
            notification(type);
        }
        for (String action :
                new String[] {
                    "MANAGER_APPOINT",
                    "MANAGER_DISMISS",
                    "CHAT_FORCE_DELETE",
                    "REPORT_HIDE",
                    "REPORT_DISMISS"
                }) {
            audit(action, "CREW_MANAGER");
        }
        membership(crewId, 2, "MANAGER");
        for (String kind : new String[] {"MEETUP", "NOTICE", "POLL", "BOARD"}) {
            post(crewId, kind);
        }
        participation(runPost, 3, "UNDECIDED");

        assertThat(count("notification")).isEqualTo(10);
        assertThat(count("audit_record")).isEqualTo(6);
        assertThat(count("crew_post")).isEqualTo(5);

        assertThatThrownBy(() -> notification("FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> audit("FOO", "RUNNER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> audit("KICK", "FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> membership(crewId, 4, "FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> post(crewId, "FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> participation(runPost, 5, "FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
