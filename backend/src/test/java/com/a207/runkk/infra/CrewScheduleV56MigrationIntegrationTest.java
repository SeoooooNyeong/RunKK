package com.a207.runkk.infra;

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
 * W6 크루 일정(S15P31A207-297) V56·V57: V49까지 올린 PostgreSQL에 기존 크루런 행을 두고 올리면 본문 첫 줄이 제목이 되고 나머지가 내용으로
 * 남는다. 하프·제한 없음 거리와 반복 회차 고유 색인도 본다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V56·V57 크루 일정 마이그레이션(PostgreSQL)")
class CrewScheduleV56MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV49() {
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

    private long run(String body, String distance) {
        return jdbc.queryForObject(
                "INSERT INTO crew_post (post_kind, crew_id, author_id, body, created_at, deleted,"
                        + " version, scheduled_at, distance, vote_deadline, status, reminded)"
                        + " VALUES ('CREW_RUN', 1, 1, ?, ?, false, 0, ?, ?, ?, 'VOTING', false)"
                        + " RETURNING id",
                Long.class,
                body,
                T0,
                T0,
                distance,
                T0);
    }

    private String column(String column, long postId) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM crew_post WHERE id = ?", String.class, postId);
    }

    @Test
    @DisplayName("본문 첫 줄 → 제목, 나머지 → 내용, 빈 본문은 '크루런', 다른 종류는 제목 없음")
    void titleBackfill() {
        long split = run("토요일 한강\n8번 출구\n6분대", "KM5");
        long oneLine = run("  한 줄 제목  ", "KM1");
        long blank = run("", "KM3");
        long cheer =
                jdbc.queryForObject(
                        "INSERT INTO crew_post (post_kind, crew_id, author_id, body, created_at,"
                                + " deleted, version) VALUES ('CHEER', 1, 1, '응원', ?, false, 0)"
                                + " RETURNING id",
                        Long.class,
                        T0);

        migrateTo("57");

        assertThat(column("title", split)).isEqualTo("토요일 한강");
        assertThat(column("body", split)).isEqualTo("8번 출구\n6분대");
        assertThat(column("title", oneLine)).isEqualTo("한 줄 제목");
        assertThat(column("body", oneLine)).isEmpty();
        assertThat(column("distance", oneLine)).as("기존 1km는 그대로").isEqualTo("KM1");
        assertThat(column("title", blank)).isEqualTo("크루런");
        assertThat(column("title", cheer)).isNull();
        assertThat(column("body", cheer)).isEqualTo("응원");
    }

    @Test
    @DisplayName("하프·제한 없음(NULL) 거리는 저장되고 기존 1km도 남는다, 정의 밖 거리는 CHECK 위반")
    void distances() {
        migrateTo("57");
        assertThat(run("하프", "HALF")).isPositive();
        assertThat(run("제한 없음", null)).isPositive();
        assertThat(run("기존", "KM1")).isPositive();
        assertThatThrownBy(() -> run("풀", "FULL"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("반복: 같은 반복의 같은 일시 회차는 두 번 넣을 수 없다")
    void seriesUniqueness() {
        migrateTo("57");
        long first = run("반복", "KM5");
        long series =
                jdbc.queryForObject(
                        "INSERT INTO crew_run_series (crew_id, author_id, last_post_id,"
                                + " last_scheduled_at, active, created_at, version)"
                                + " VALUES (1, 1, ?, ?, true, ?, 0) RETURNING id",
                        Long.class,
                        first,
                        T0,
                        T0);
        jdbc.update("UPDATE crew_post SET series_id = ? WHERE id = ?", series, first);
        long second = run("반복", "KM5");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE crew_post SET series_id = ? WHERE id = ?",
                                        series,
                                        second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
