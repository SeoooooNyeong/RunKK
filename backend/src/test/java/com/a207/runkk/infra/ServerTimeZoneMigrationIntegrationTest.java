package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V48 서버 시간대 수정(DF-073): V47까지 올린 PostgreSQL에 옛 설정(JVM UTC, jdbc.time_zone Asia/Seoul)이 남긴 모양의 행을
 * 두고 V48을 적용하면, 서버가 쓴 시각만 9시간 빠지고 SQL로 넣은 시각(시드, 운영자 SQL)과 대기열, 시점 열은 그대로다. 이벤트 시드의 만든 시각(Flyway 세션
 * UTC의 now())은 9시간 더해진다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V48 서버 시간대 수정 마이그레이션(PostgreSQL)")
class ServerTimeZoneMigrationIntegrationTest {

    /** 실제 한국 시각. 옛 설정의 서버는 이 시각을 9시간 앞선 SERVER_STORED로 저장했다. */
    private static final LocalDateTime KST = LocalDateTime.of(2026, 11, 9, 14, 29, 0);

    private static final LocalDateTime SERVER_STORED = KST.plusHours(9);

    /** 되돌리지 않는 timestamp without time zone 열(설계 05-28 4절). 나머지는 모두 V48이 9시간 뺀다. */
    private static final Set<String> EXCLUDED =
            Set.of(
                    "flyway_schema_history.installed_on",
                    "match_ticket.waiting_since",
                    "match_ticket.assigned_at");

    /** V48이 행 조건으로 나눠 다루는 열(1~3절). 4절의 「모든 행 9시간 빼기」 검사에서 뺀다. */
    private static final Set<String> CONDITIONAL =
            Set.of(
                    "audit_record.recorded_at",
                    "celebrity_record.registered_at",
                    "celebrity_record.real_name_consented_at",
                    "event.starts_at",
                    "event.ends_at",
                    "event.created_at",
                    "event.updated_at");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV47() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("47");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                // V29(analytics)가 ai 계정 비밀번호를 플레이스홀더로 받는다(application-integration.yml과 같은 값)
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .target(version)
                .load()
                .migrate();
    }

    private LocalDateTime at(String sql, Object... args) {
        return jdbc.queryForObject(sql, Timestamp.class, args).toLocalDateTime();
    }

    private static Timestamp ts(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    @Test
    @DisplayName("서버가 쓴 시각은 9시간 빠지고 NULL은 NULL로 남는다(대결, 알림)")
    void serverWrittenValues_moveBackNineHours() {
        long battleId =
                jdbc.queryForObject(
                        "INSERT INTO battle (type, distance_meters, status, created_at, start_at,"
                                + " corrected, winner_opponent, version) VALUES ('RANDOM', 5000,"
                                + " 'FINISHED', ?, ?, false, false, 0) RETURNING id",
                        Long.class,
                        ts(SERVER_STORED),
                        ts(SERVER_STORED.plusSeconds(3)));
        long notificationId =
                jdbc.queryForObject(
                        "INSERT INTO notification (recipient_id, type, content, occurred_at,"
                                + " is_read) VALUES (11, 'NOTICE', '공지', ?, false) RETURNING id",
                        Long.class,
                        ts(SERVER_STORED));

        migrateTo("48");

        assertThat(at("SELECT created_at FROM battle WHERE id = ?", battleId)).isEqualTo(KST);
        assertThat(at("SELECT start_at FROM battle WHERE id = ?", battleId))
                .isEqualTo(KST.plusSeconds(3));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT judged_at IS NULL AND closed_at IS NULL FROM battle WHERE"
                                        + " id = ?",
                                Boolean.class,
                                battleId))
                .isTrue();
        assertThat(at("SELECT occurred_at FROM notification WHERE id = ?", notificationId))
                .isEqualTo(KST);
    }

    @Test
    @DisplayName("감사 기록은 9시간 빠지고, 마이그레이션 뒤에도 고치기는 다시 거부된다")
    void auditRecord_movesBack_andStaysAppendOnly() {
        long auditId =
                jdbc.queryForObject(
                        "INSERT INTO audit_record (recorded_at, actor_id, actor_role, action,"
                                + " target_id) VALUES (?, '7', 'RUNNER', 'CONSENT_CHANGE', '7')"
                                + " RETURNING id",
                        Long.class,
                        ts(SERVER_STORED));

        migrateTo("48");

        assertThat(at("SELECT recorded_at FROM audit_record WHERE id = ?", auditId)).isEqualTo(KST);
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_record SET target_id = 'forged'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("유명인 기록은 관리 API로 등록한 행만 되돌리고, V32 시드와 운영자 SQL 행은 그대로다")
    void celebrity_onlyApiRegisteredRowsMoveBack() {
        LocalDateTime operatorSql = LocalDateTime.of(2026, 11, 8, 21, 59, 16);
        long operatorRow =
                jdbc.queryForObject(
                        "INSERT INTO celebrity_record (anonymous_name, distance_meters,"
                                + " finish_millis, source_name, registered_at,"
                                + " real_name_consented_at, real_name_consent_basis, figure_key)"
                                + " VALUES ('주세빈 코치님', 10000, 4159000, '런크크', ?, ?, '본인 동의',"
                                + " 'joo') RETURNING id",
                        Long.class,
                        ts(operatorSql),
                        ts(operatorSql));
        long apiRow =
                jdbc.queryForObject(
                        "INSERT INTO celebrity_record (anonymous_name, distance_meters,"
                                + " finish_millis, source_name, registered_at,"
                                + " real_name_consented_at, real_name_consent_basis) VALUES"
                                + " ('관리 등록', 5000, 1500000, '대회', ?, ?, '서면') RETURNING id",
                        Long.class,
                        ts(SERVER_STORED),
                        ts(SERVER_STORED.minusDays(1)));
        jdbc.update(
                "INSERT INTO audit_record (recorded_at, actor_id, actor_role, action, target_id)"
                        + " VALUES (?, 'op', 'OPERATOR', 'CELEBRITY_REGISTER', ?)",
                ts(SERVER_STORED),
                String.valueOf(apiRow));
        List<Timestamp> seedBefore =
                jdbc.queryForList(
                        "SELECT registered_at FROM celebrity_record WHERE id <> ? AND id <> ?"
                                + " ORDER BY id",
                        Timestamp.class,
                        operatorRow,
                        apiRow);

        migrateTo("48");

        assertThat(seedBefore).hasSize(10).contains(ts(LocalDateTime.of(2026, 10, 31, 0, 0, 3)));
        assertThat(
                        jdbc.queryForList(
                                "SELECT registered_at FROM celebrity_record WHERE id <> ? AND id"
                                        + " <> ? ORDER BY id",
                                Timestamp.class,
                                operatorRow,
                                apiRow))
                .isEqualTo(seedBefore);
        assertThat(at("SELECT registered_at FROM celebrity_record WHERE id = ?", operatorRow))
                .isEqualTo(operatorSql);
        assertThat(
                        at(
                                "SELECT real_name_consented_at FROM celebrity_record WHERE id = ?",
                                operatorRow))
                .isEqualTo(operatorSql);
        assertThat(at("SELECT registered_at FROM celebrity_record WHERE id = ?", apiRow))
                .isEqualTo(KST);
        assertThat(at("SELECT real_name_consented_at FROM celebrity_record WHERE id = ?", apiRow))
                .isEqualTo(KST.minusDays(1));
    }

    @Test
    @DisplayName("이벤트 기간은 임시 보정(+9시간)을 되돌려 시드 리터럴로 돌아가고, 시드의 만든 시각은 UTC 벽시계에서 한국 시각이 된다")
    void event_periodRestored_andSeedCreatedAtBecomesKst() {
        // 2026-11-09 임시 보정: 운영과 develop의 시드 기간에 9시간을 더했다
        jdbc.update(
                "UPDATE event SET starts_at = starts_at + INTERVAL '9 hours',"
                        + " ends_at = ends_at + INTERVAL '9 hours'");
        LocalDateTime seedCreated =
                at("SELECT created_at FROM event WHERE slug = 'coach-challenge'");
        // 관리 API로 만든 이벤트(서버가 쓴 값)
        long apiEvent =
                jdbc.queryForObject(
                        "INSERT INTO event (slug, title, subtitle, kind, config, starts_at,"
                                + " ends_at, banner_image_url, thumbnail_image_url, detail_url,"
                                + " sort_order, published, created_at, updated_at) VALUES"
                                + " ('next', '다음', '다음', 'RUN_DISTANCE',"
                                + " '{\"minDistanceMeters\":1000}', ?, ?, '/b.png', '/t.png',"
                                + " '/events/next/', 3, false, ?, ?) RETURNING id",
                        Long.class,
                        ts(LocalDateTime.of(2026, 11, 20, 9, 0)),
                        ts(LocalDateTime.of(2026, 11, 28, 8, 59, 59)),
                        ts(SERVER_STORED),
                        ts(SERVER_STORED.plusMinutes(5)));
        // 시드 하나는 관리 API로 고쳤다(updated_at이 서버 값)
        jdbc.update(
                "UPDATE event SET updated_at = ? WHERE slug = 'beta-1km'",
                ts(SERVER_STORED.plusMinutes(1)));

        migrateTo("48");

        for (String slug : List.of("coach-challenge", "beta-1km")) {
            assertThat(at("SELECT starts_at FROM event WHERE slug = ?", slug))
                    .isEqualTo(LocalDateTime.of(2026, 11, 8, 0, 0, 0));
            assertThat(at("SELECT ends_at FROM event WHERE slug = ?", slug))
                    .isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
            assertThat(at("SELECT created_at FROM event WHERE slug = ?", slug))
                    .isEqualTo(seedCreated.plusHours(9));
        }
        assertThat(at("SELECT updated_at FROM event WHERE slug = 'coach-challenge'"))
                .isEqualTo(seedCreated.plusHours(9));
        assertThat(at("SELECT updated_at FROM event WHERE slug = 'beta-1km'"))
                .isEqualTo(KST.plusMinutes(1));
        assertThat(at("SELECT starts_at FROM event WHERE id = ?", apiEvent))
                .isEqualTo(LocalDateTime.of(2026, 11, 20, 0, 0));
        assertThat(at("SELECT ends_at FROM event WHERE id = ?", apiEvent))
                .isEqualTo(LocalDateTime.of(2026, 11, 27, 23, 59, 59));
        assertThat(at("SELECT created_at FROM event WHERE id = ?", apiEvent)).isEqualTo(KST);
        assertThat(at("SELECT updated_at FROM event WHERE id = ?", apiEvent))
                .isEqualTo(KST.plusMinutes(5));
    }

    @Test
    @DisplayName("임시 보정이 없는 새 DB(새 설정이 V35_2부터 적용)에서는 이벤트 시드의 기간과 만든 시각, 고친 시각이 그대로다")
    void freshDatabase_eventSeedStays() {
        // 시험 JVM과 새 이미지는 Asia/Seoul이라 V35_2 시드의 now()가 이미 한국 시각이다
        List<Map<String, Object>> before =
                jdbc.queryForList(
                        "SELECT slug, starts_at, ends_at, created_at, updated_at FROM event ORDER"
                                + " BY slug");

        migrateTo("48");

        assertThat(before).hasSize(2);
        assertThat(
                        jdbc.queryForList(
                                "SELECT slug, starts_at, ends_at, created_at, updated_at FROM event"
                                        + " ORDER BY slug"))
                .isEqualTo(before);
        assertThat(at("SELECT starts_at FROM event WHERE slug = 'beta-1km'"))
                .isEqualTo(LocalDateTime.of(2026, 11, 8, 0, 0, 0));
        assertThat(at("SELECT ends_at FROM event WHERE slug = 'beta-1km'"))
                .isEqualTo(LocalDateTime.of(2026, 11, 16, 23, 59, 59));
    }

    @Test
    @DisplayName("대기열 행과 Flyway 기록은 그대로다")
    void excludedColumns_stay() {
        LocalDateTime waiting = LocalDateTime.of(2026, 11, 9, 14, 0);
        jdbc.update(
                "INSERT INTO match_ticket (member_id, tier, distance, waiting_since, status,"
                        + " assigned_at) VALUES (1, 'BRONZE', 'KM5', ?, 'ASSIGNED', ?)",
                ts(waiting),
                ts(waiting.minusHours(9)));
        List<Timestamp> installedBefore =
                jdbc.queryForList(
                        "SELECT installed_on FROM flyway_schema_history ORDER BY installed_rank",
                        Timestamp.class);

        migrateTo("48");

        assertThat(at("SELECT waiting_since FROM match_ticket WHERE member_id = 1"))
                .isEqualTo(waiting);
        assertThat(at("SELECT assigned_at FROM match_ticket WHERE member_id = 1"))
                .isEqualTo(waiting.minusHours(9));
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_on FROM flyway_schema_history ORDER BY"
                                        + " installed_rank",
                                Timestamp.class))
                .startsWith(installedBefore.toArray(Timestamp[]::new));
    }

    /**
     * V47 스키마의 timestamp without time zone 열은 모두 V48이 다루거나 제외 목록에 있다. 4절 열은 「열 = 열 - INTERVAL '9
     * hours'」를 그 표의 UPDATE 문에 가진다(열을 빠뜨리면 실패한다).
     */
    @Test
    @DisplayName("V47의 모든 timestamp 열이 V48 되돌리기 목록이나 제외 목록에 있다")
    void everyTimestampColumn_isCoveredOrExcluded() throws IOException {
        String migration = readMigration();
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> row :
                jdbc.queryForList(
                        "SELECT table_name, column_name FROM information_schema.columns WHERE"
                                + " table_schema = 'public' AND data_type = 'timestamp without"
                                + " time zone' AND table_name IN (SELECT table_name FROM"
                                + " information_schema.tables WHERE table_schema = 'public' AND"
                                + " table_type = 'BASE TABLE')")) {
            String table = (String) row.get("table_name");
            String column = (String) row.get("column_name");
            String key = table + "." + column;
            if (EXCLUDED.contains(key) || CONDITIONAL.contains(key)) {
                continue;
            }
            Pattern statement =
                    Pattern.compile(
                            "UPDATE "
                                    + table
                                    + " SET [^;]*\\b"
                                    + column
                                    + " = "
                                    + column
                                    + " - INTERVAL '9 hours'[^;]*;");
            if (!statement.matcher(migration).find()) {
                missing.add(key);
            }
        }
        assertThat(missing).isEmpty();
    }

    private static String readMigration() throws IOException {
        try (InputStream in =
                ServerTimeZoneMigrationIntegrationTest.class.getResourceAsStream(
                        "/db/migration/V48__server_timezone_kst.sql")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
