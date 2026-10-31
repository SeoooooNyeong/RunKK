package com.a207.runkk.infra.battle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
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
 * V31 상대 캐릭터 성별·V32 셀럽 캐릭터(2026-10-31 결정): V30까지 올린 PostgreSQL에 기존 대결을 두고 V32까지 적용하면 기존 행은 NULL로
 * 남고, 성별은 MALE·FEMALE만 받으며, 셀럽 기록 10건(풀코스 4건과 이O주 하프·10km·5km, 연예인 3건)이 그림 키와 함께 들어간다. Docker가 없으면
 * 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V31·V32 대결 캐릭터 마이그레이션(PostgreSQL)")
class BattleFigureMigrationIntegrationTest {

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 30, 9, 0));

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV30() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("30");
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

    private long randomBattle() {
        long battleId =
                jdbc.queryForObject(
                        "INSERT INTO battle (type, distance_meters, status, created_at, corrected,"
                                + " winner_opponent, version) VALUES ('RANDOM', 5000, 'FINISHED',"
                                + " ?, false, false, 0) RETURNING id",
                        Long.class,
                        T0);
        jdbc.update(
                "INSERT INTO battle_participant (battle_id, seq, member_id, overtakes, forfeited,"
                        + " new_record) VALUES (?, 0, 1, 0, false, false)",
                battleId);
        return battleId;
    }

    @Test
    @DisplayName("기존 대결은 성별·그림 키가 NULL이고, 성별 열은 MALE·FEMALE만 받는다")
    void existingRowsStayNull_andGenderIsChecked() {
        long battleId = randomBattle();
        migrateTo("32");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT opponent_gender IS NULL AND opponent_figure IS NULL FROM"
                                        + " battle WHERE id = ?",
                                Boolean.class,
                                battleId))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT shown_gender FROM battle_participant WHERE battle_id = ?",
                                String.class,
                                battleId))
                .isNull();
        jdbc.update(
                "UPDATE battle_participant SET shown_gender = 'FEMALE' WHERE battle_id = ?",
                battleId);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE battle_participant SET shown_gender ="
                                                + " 'UNDISCLOSED' WHERE battle_id = ?",
                                        battleId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE battle SET opponent_gender = 'UNDISCLOSED' WHERE"
                                                + " id = ?",
                                        battleId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("셀럽 기록 10건이 그림 키·출처와 함께 들어가고, 구간 기록·실명 동의는 없다")
    void celebritySeed() {
        migrateTo("32");

        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        "SELECT anonymous_name, figure_key, distance_meters, finish_millis, splits,"
                                + " source_name, real_name_consented_at FROM celebrity_record"
                                + " ORDER BY registered_at DESC");
        assertThat(rows)
                .extracting(r -> r.get("anonymous_name") + "/" + r.get("figure_key"))
                .containsExactly(
                        "이O주/lee",
                        "황O조/hwang",
                        "황O조/hwang",
                        "손O정/son",
                        "이O주/lee",
                        "이O주/lee",
                        "이O주/lee",
                        "기O84/gian",
                        "임O미/lim",
                        "진O규/jin");
        assertThat(rows)
                .extracting(r -> ((Number) r.get("finish_millis")).longValue())
                .containsExactly(
                        7_640_000L,
                        7_689_000L,
                        8_003_000L,
                        8_959_000L,
                        3_664_000L,
                        1_784_110L,
                        852_270L,
                        17_296_000L,
                        15_643_000L,
                        3_332_000L);
        assertThat(rows)
                .extracting(r -> ((Number) r.get("distance_meters")).longValue())
                .containsExactly(
                        42_195L, 42_195L, 42_195L, 42_195L, 21_097L, 10_000L, 5_000L, 42_195L,
                        42_195L, 10_000L);
        assertThat(rows.get(0).get("source_name")).isEqualTo("2000 도쿄국제마라톤");
        assertThat(rows)
                .allSatisfy(
                        r -> {
                            assertThat(r.get("splits")).isNull();
                            assertThat(r.get("real_name_consented_at")).isNull();
                        });
    }
}
