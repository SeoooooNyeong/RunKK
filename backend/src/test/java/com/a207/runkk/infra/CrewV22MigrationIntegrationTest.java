package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * 부록 F F.5.3 V22 마이그레이션 시험(S3a): V21까지 올린 PostgreSQL에 기존 응원글·크루런·주간 거리 행을 두고 V22를 적용하면 기존 행은 hidden
 * 거짓·목표 없음이 되고 본문 1,000자·새 종류의 CHECK·새 표(대면 모임 참석·투표 선택지·응답·사진)와 FK CASCADE·부분 색인이 생긴다. Docker가 없는
 * PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V22 크루 게시 계층·사진·공동 목표 마이그레이션(PostgreSQL)")
class CrewV22MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 1, 9, 0));
    private static final Timestamp LATER = Timestamp.valueOf(LocalDateTime.of(2026, 10, 30, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV21() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("21");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long crew() {
        return jdbc.queryForObject(
                "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                        + " invite_code_expires_at, created_at, version)"
                        + " VALUES ('이십이크루', '', 'PUBLIC', 'FREE', 'V22V22', ?, ?, 0) RETURNING id",
                Long.class,
                T0,
                T0);
    }

    private long post(long crewId, String kind, String extraColumns, Object... extraValues) {
        String columns = "post_kind, crew_id, author_id, body, created_at, deleted, version";
        String values = "?, ?, 1, '본문', ?, false, 0";
        if (!extraColumns.isEmpty()) {
            columns += ", " + extraColumns;
            values += ", ?".repeat(extraValues.length);
        }
        Object[] args = new Object[3 + extraValues.length];
        args[0] = kind;
        args[1] = crewId;
        args[2] = T0;
        System.arraycopy(extraValues, 0, args, 3, extraValues.length);
        return jdbc.queryForObject(
                "INSERT INTO crew_post (" + columns + ") VALUES (" + values + ") RETURNING id",
                Long.class,
                args);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class,
                table);
    }

    @Test
    @DisplayName("기존 행: hidden 거짓·목표 없음, 새 칼럼·표·부분 색인이 생긴다")
    void existingRowsAndSchema() {
        long crewId = crew();
        long cheer = post(crewId, "CHEER", "");
        jdbc.update(
                "INSERT INTO weekly_race (crew_id, week_start, total_meters) VALUES (?, ?, 12000)",
                crewId,
                LocalDate.of(2026, 9, 28));

        migrateTo("22");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT hidden FROM crew_post WHERE id = ?", Boolean.class, cheer))
                .isFalse();
        assertThat(columns("crew_post"))
                .contains(
                        "hidden",
                        "pinned",
                        "place_name",
                        "capacity",
                        "poll_method",
                        "poll_closes_at");
        assertThat(columns("weekly_race")).contains("goal_meters", "goal_set_by", "achieved_at");
        Map<String, Object> race =
                jdbc.queryForMap("SELECT goal_meters, achieved_at FROM weekly_race");
        assertThat(race.get("goal_meters")).isNull();
        assertThat(race.get("achieved_at")).isNull();
        for (String table :
                List.of(
                        "crew_meetup_attendance",
                        "crew_poll_option",
                        "crew_poll_response",
                        "crew_photo")) {
            assertThat(columns(table)).as(table).isNotEmpty();
        }
        assertThat(
                        jdbc.queryForList(
                                "SELECT indexname FROM pg_indexes WHERE tablename IN ('crew_post',"
                                        + " 'crew_photo')",
                                String.class))
                .contains(
                        "idx_crew_post_pinned_notice",
                        "idx_crew_post_poll_closing",
                        "idx_crew_photo_crew");
        jdbc.update("UPDATE crew_post SET body = ? WHERE id = ?", "가".repeat(1000), cheer);
    }

    @Test
    @DisplayName("종류별 CHECK: 대면 모임 장소·정원, 투표 방식·마감, 공지 고정 칸")
    void kindChecks() {
        migrateTo("22");
        long crewId = crew();
        post(crewId, "MEETUP", "scheduled_at, place_name, capacity", LATER, "공원", 10);
        assertThatThrownBy(
                        () ->
                                post(
                                        crewId,
                                        "MEETUP",
                                        "scheduled_at, place_name, capacity",
                                        LATER,
                                        "공원",
                                        11))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> post(crewId, "MEETUP", "scheduled_at, capacity", LATER, 5))
                .isInstanceOf(DataIntegrityViolationException.class);
        post(crewId, "POLL", "poll_method, poll_closes_at, reminded", "YES_NO", LATER, false);
        assertThatThrownBy(() -> post(crewId, "POLL", "poll_method", "MAYBE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        post(crewId, "NOTICE", "pinned", true);
        assertThatThrownBy(() -> post(crewId, "NOTICE", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        post(crewId, "BOARD", "");
    }

    @Test
    @DisplayName("딸린 표: 글을 지우면 참석·선택지·응답·사진이 함께 지워지고, 사진 jpeg는 빈 바이트 기본값")
    void cascades() {
        migrateTo("22");
        long crewId = crew();
        long poll =
                post(
                        crewId,
                        "POLL",
                        "poll_method, poll_closes_at, reminded",
                        "CHOICES",
                        LATER,
                        false);
        jdbc.update(
                "INSERT INTO crew_poll_option (post_id, option_no, label) VALUES (?, 1, '가'), (?,"
                        + " 2, '나')",
                poll,
                poll);
        jdbc.update(
                "INSERT INTO crew_poll_response (post_id, member_id, option_no, responded_at)"
                        + " VALUES (?, 3, 2, ?)",
                poll,
                T0);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO crew_poll_response (post_id, member_id,"
                                                + " option_no, responded_at) VALUES (?, 4, 9, ?)",
                                        poll,
                                        T0))
                .as("없는 선택지는 FK가 막는다")
                .isInstanceOf(DataIntegrityViolationException.class);
        long meet = post(crewId, "MEETUP", "scheduled_at, place_name, capacity", LATER, "공원", 3);
        jdbc.update(
                "INSERT INTO crew_meetup_attendance (post_id, member_id, answer, answered_at)"
                        + " VALUES (?, 3, 'UNDECIDED', ?)",
                meet,
                T0);
        long board = post(crewId, "BOARD", "");
        jdbc.update(
                "INSERT INTO crew_photo (post_id, crew_id, seq, size_bytes, created_at) VALUES (?,"
                        + " ?, 1, 10, ?)",
                board,
                crewId,
                T0);
        assertThat(jdbc.queryForObject("SELECT length(jpeg) FROM crew_photo", Integer.class))
                .isZero();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO crew_photo (post_id, crew_id, seq, size_bytes,"
                                                + " created_at) VALUES (?, ?, 6, 10, ?)",
                                        board,
                                        crewId,
                                        T0))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM crew_post WHERE crew_id = ?", crewId);

        for (String table :
                List.of(
                        "crew_poll_option",
                        "crew_poll_response",
                        "crew_meetup_attendance",
                        "crew_photo")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class))
                    .as(table)
                    .isZero();
        }
    }

    @Test
    @DisplayName("공동 목표 CHECK: 1km 미만 거부, 달성 일시는 목표가 있을 때만")
    void goalChecks() {
        migrateTo("22");
        long crewId = crew();
        LocalDate monday = LocalDate.of(2026, 10, 19);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO weekly_race (crew_id, week_start,"
                                            + " total_meters, goal_meters) VALUES (?, ?, 0, 999)",
                                        crewId,
                                        monday))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO weekly_race (crew_id, week_start,"
                                                + " total_meters, achieved_at) VALUES (?, ?, 0, ?)",
                                        crewId,
                                        monday,
                                        T0))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "INSERT INTO weekly_race (crew_id, week_start, total_meters, goal_meters,"
                        + " achieved_at) VALUES (?, ?, 1000, 1000, ?)",
                crewId,
                monday,
                T0);
    }
}
