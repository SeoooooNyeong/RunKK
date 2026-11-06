package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V35_1 배치고사 진행 마이그레이션(05-21 5절, D-476·D-514). 새 DB 순서(V35 → 35.1)와 develop 순서(V37까지 있는 DB에 순서 밖
 * 35.1)를 PostgreSQL로 확인한다. develop 순서는 실제 마이그레이션 폴더에서 V35_1만 뺀 사본으로 35.1 이전 develop DB를 만든다 (핫픽스
 * 브랜치에서 쓰던 흉내 파일 db/sim-develop는 실제 V36·V37과 번호가 겹쳐 지웠다). Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V35_1 배치고사 진행 마이그레이션(PostgreSQL)")
class PlacementMigrationIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 4, 9, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

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
    }

    private Flyway flyway(String target, boolean outOfOrder, String... locations) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(locations)
                .placeholders(Map.of("runkk_ai_password", "integration-test"))
                .outOfOrder(outOfOrder)
                .ignoreMigrationPatterns("*:missing", "*:future")
                .target(target)
                .load();
    }

    private long member(String nickname) {
        return jdbc.queryForObject(
                "INSERT INTO member (provider, provider_account_id, nickname, tier, level,"
                    + " joined_at, unit, record_visibility, course_map_shared, profile_visibility,"
                    + " muted_notification_types, watch_linked, run_target, run_voice,"
                    + " run_vibration, run_follow_coach_plan, battle_default_distance,"
                    + " battle_voice, battle_vibration, battle_gap_alert_interval, valid_run_count,"
                    + " recent_scores, total_valid_meters, status, created_at, updated_at, version,"
                    + " last_access_at) VALUES ('KAKAO', ?, ?, 'UNRANKED', 1, ?, 'KM', 'PUBLIC',"
                    + " false, 'PRIVATE', '', false, 'FREE', true, true, false, 'KM5', true, true,"
                    + " 'KM1', 0, '', 0, 'ACTIVE', ?, ?, 0, ?) RETURNING id",
                Long.class,
                "k-" + nickname,
                nickname,
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0));
    }

    private void round(long memberId, int roundNo, long battleId, String verdict) {
        jdbc.update(
                "INSERT INTO placement_round (member_id, round_no, battle_id, score,"
                    + " battle_distance, run_distance_meters, run_elapsed_millis,"
                    + " average_pace_seconds, verdict, valid_run_ordinal, counted_at) VALUES (?, ?,"
                    + " ?, 30.5, 'KM5', 5000, 1800000, 360, ?, ?, ?)",
                memberId,
                roundNo,
                battleId,
                verdict,
                roundNo,
                Timestamp.valueOf(T0));
    }

    /**
     * 실제 마이그레이션 폴더를 [prefix]로 시작하는 파일만 빼고 [target]에 복사한다. 35.1이 생기기 전 develop DB(V36·V37 적용)를 같은
     * 체크섬의 실제 파일로 만든다.
     */
    private static void copyMigrationsExcept(String prefix, Path target) throws Exception {
        Path source =
                Path.of(
                        PlacementMigrationIntegrationTest.class
                                .getResource("/db/migration")
                                .toURI());
        try (Stream<Path> files = Files.list(source)) {
            for (Path file :
                    files.filter(f -> !f.getFileName().toString().startsWith(prefix)).toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
    }

    private List<String> appliedVersions() {
        return jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);
    }

    @Test
    @DisplayName("TC-5932 새 DB는 V35 → 35.1 순서로 올라가고 새 표·칼럼이 생긴다(ddl validate 대상 칼럼 형식 SMALLINT 포함)")
    void v35_1_appliesInOrderAndValidates() {
        flyway("35.1", false, "classpath:db/migration").migrate();

        assertThat(appliedVersions()).containsSubsequence("35", "35.1");
        assertThat(appliedVersions().get(appliedVersions().size() - 1)).isEqualTo("35.1");
        Map<String, String> types =
                Map.of(
                        "placement_progress.counted_rounds", "smallint",
                        "placement_round.round_no", "smallint",
                        "battle.placement_round", "smallint",
                        "battle_participant.valid_run_ordinal", "integer",
                        "placement_round.score", "double precision");
        types.forEach(
                (column, type) -> {
                    String[] parts = column.split("\\.");
                    assertThat(
                                    jdbc.queryForObject(
                                            "SELECT data_type FROM information_schema.columns"
                                                    + " WHERE table_name = ? AND column_name = ?",
                                            String.class,
                                            parts[0],
                                            parts[1]))
                            .as(column)
                            .isEqualTo(type);
                });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE indexname ="
                                        + " 'ix_battle_placement'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("TC-5933 CHECK: 센 판 0~3, 임시 티어는 랭크 티어만, 판 번호 1~3, 승패 WIN·LOSE·DRAW")
    void checkConstraints() {
        flyway("35.1", false, "classpath:db/migration").migrate();
        long m = member("배치곰");
        jdbc.update("INSERT INTO placement_progress (member_id) VALUES (?)", m);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE placement_progress SET counted_rounds = 4 WHERE"
                                                + " member_id = ?",
                                        m))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE placement_progress SET provisional_tier ="
                                                + " 'UNRANKED' WHERE member_id = ?",
                                        m))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "UPDATE placement_progress SET provisional_tier = 'GOLD' WHERE member_id = ?", m);
        assertThatThrownBy(() -> round(m, 4, 1, "WIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> round(m, 1, 1, "LOST"))
                .isInstanceOf(DataIntegrityViolationException.class);
        round(m, 1, 1, "WIN");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT counted_rounds FROM placement_progress WHERE member_id = ?",
                                Integer.class,
                                m))
                .isZero();
    }

    @Test
    @DisplayName(
            "TC-5934 같은 대결은 한 판으로만(uq_placement_round_battle), battle.placement_round는 GHOST만 1~3")
    void uniqueBattleAndGhostOnlyRound() {
        flyway("35.1", false, "classpath:db/migration").migrate();
        long a = member("곰A");
        long b = member("곰B");
        jdbc.update("INSERT INTO placement_progress (member_id) VALUES (?), (?)", a, b);
        round(a, 1, 77, "WIN");
        assertThatThrownBy(() -> round(b, 1, 77, "LOSE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> round(a, 1, 78, "LOSE"))
                .as("같은 회원 같은 판 번호")
                .isInstanceOf(DataIntegrityViolationException.class);

        String insertBattle =
                "INSERT INTO battle (type, distance_meters, status, created_at, corrected,"
                        + " winner_opponent, version, opponent_kind, placement_round) VALUES (?,"
                        + " 5000, 'WAITING_START', now(), false, false, 0, ?, ?)";
        jdbc.update(insertBattle, "GHOST", "VIRTUAL", 2);
        assertThatThrownBy(() -> jdbc.update(insertBattle, "SELF", "RUN_RECORD", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertBattle, "GHOST", "VIRTUAL", 4))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(insertBattle, "RANDOM", null, null);
    }

    @Test
    @DisplayName("TC-5935 회원 행을 지우면 진행·판 행이 함께 지워진다(ON DELETE CASCADE, D-489)")
    void memberDelete_cascades() {
        flyway("35.1", false, "classpath:db/migration").migrate();
        long m = member("떠날곰");
        jdbc.update("INSERT INTO placement_progress (member_id) VALUES (?)", m);
        round(m, 1, 5, "DRAW");

        jdbc.update("DELETE FROM member WHERE id = ?", m);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM placement_progress", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM placement_round", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName(
            "TC-5936 develop 순서: V37까지 있는 DB에 35.1은 순서 밖 적용을 켜야 한 번 들어가고, 다시 돌리면 아무것도 하지"
                    + " 않는다(D-476·R85)")
    void developOrder_outOfOrderAppliesOnce(@TempDir Path withoutPlacement) throws Exception {
        copyMigrationsExcept("V35_1__", withoutPlacement);
        flyway("37", false, "filesystem:" + withoutPlacement.toAbsolutePath()).migrate();
        assertThat(appliedVersions()).endsWith("35", "36", "37").doesNotContain("35.1");

        assertThatThrownBy(() -> flyway("37", false, "classpath:db/migration").migrate())
                .as("순서 밖이 꺼진 설정은 적용 안 된 낮은 번호 35.1에서 멈춘다(그래서 develop만 켠다)")
                .isInstanceOf(FlywayException.class);
        assertThat(appliedVersions()).doesNotContain("35.1");

        MigrateResult develop = flyway("37", true, "classpath:db/migration").migrate();
        assertThat(develop.migrationsExecuted).isEqualTo(1);
        assertThat(appliedVersions()).endsWith("36", "37", "35.1");
        assertThat(flyway("37", true, "classpath:db/migration").migrate().migrationsExecuted)
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_name IN"
                                        + " ('placement_progress', 'placement_round')",
                                Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("TC-5937 out-of-order는 develop 프로필에만 있고 prod·기본 설정에는 없다(D-514)")
    void outOfOrderOnlyInDevelopProfile() throws Exception {
        String yml =
                Files.readString(
                        Path.of("src", "main", "resources", "application.yml"),
                        StandardCharsets.UTF_8);
        String[] profiles = yml.split("\r?\n---\r?\n");
        for (String part : profiles) {
            boolean develop = part.contains("on-profile: develop");
            assertThat(part.contains("out-of-order: true"))
                    .as(develop ? "develop 프로필" : "다른 문서")
                    .isEqualTo(develop);
        }
    }
}
