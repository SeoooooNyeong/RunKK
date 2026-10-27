package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.policy.Landmark;
import com.a207.runkk.policy.LandmarkTable;
import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * 부록 F F.5.3 V25(D-107): LANDMARKS 운영 기준값 행의 원소마다 key를 붙이고(기본 표 이름이면 D-107의 키, 아니면 'LM_' || 순번, 이름
 * 없는 원소 포함), avatar.selected_background와 avatar_background 표를 만든다. 해금 행은 만들지 않는다. Docker가 없는 PC에서는
 * 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V25 랜드마크 배경 마이그레이션(PostgreSQL)")
class AvatarBackgroundMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV19() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("19");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void landmarks(String json) {
        jdbc.update(
                "INSERT INTO operation_setting (setting_key, setting_value, version, updated_at)"
                        + " VALUES ('LANDMARKS', ?, 0, ?)",
                json,
                T0);
    }

    private static String element(String name, long meters) {
        String named = name == null ? "" : "\"name\":\"" + name + "\",";
        return "{\"stage\":\"DOMESTIC\","
                + named
                + "\"distance\":{\"meters\":"
                + meters
                + "},\"roundTrip\":true}";
    }

    private String stored() {
        return jdbc.queryForObject(
                "SELECT setting_value FROM operation_setting WHERE setting_key = 'LANDMARKS'",
                String.class);
    }

    private List<String> keysOf(String json) throws Exception {
        List<String> keys = new ArrayList<>();
        for (JsonNode node : JSON.readTree(json).get("landmarks")) {
            keys.add(node.get("key").asText());
        }
        return keys;
    }

    @Test
    @DisplayName("기본 표 이름은 D-107 키, 바뀐 이름·같은 이름 두 번째는 LM_순번이고 결과는 LANDMARKS 검사를 통과한다")
    void assignsKeys_andValidates() throws Exception {
        landmarks(
                "{\"landmarks\":["
                        + element("인천", 60_000)
                        + ","
                        + element("인천공항", 70_000)
                        + ","
                        + element("부산", 800_000)
                        + ","
                        + element("부산", 900_000)
                        + "]}");

        migrateTo("25");

        String json = stored();
        assertThat(keysOf(json)).containsExactly("INCHEON", "LM_2", "BUSAN", "LM_4");
        assertThat(SettingKey.LANDMARKS.validate(json).valid()).isTrue();
        LandmarkTable table = (LandmarkTable) SettingKey.LANDMARKS.parse(json);
        assertThat(table.landmarks()).extracting(Landmark::name).first().isEqualTo("인천");
    }

    @Test
    @DisplayName("이름 없는 원소도 LM_순번을 받고, 객체가 아닌 원소·다른 기준값 행이 있어도 멈추지 않는다")
    void namelessElement_getsSequenceKey() throws Exception {
        landmarks("{\"landmarks\":[" + element(null, 60_000) + ",3]}");
        jdbc.update(
                "INSERT INTO operation_setting (setting_key, setting_value, version, updated_at)"
                        + " VALUES ('SELFIE_ENABLED', 'false', 0, ?)",
                T0);

        migrateTo("25");

        assertThat(keysOf(stored())).containsExactly("LM_1");
    }

    @Test
    @DisplayName("LANDMARKS 행이 없으면 아무 행도 만들지 않고, 배경 표는 비어 있으며 회원 삭제로 함께 지워진다")
    void noSettingRow_andBackgroundTable() {
        jdbc.update(
                "INSERT INTO avatar (member_id, creation_count, created_on_last_day, body_shape,"
                        + " skin_tone, version) VALUES (3, 0, 0, 'STAGE1', 'DAYTIME', 0)");

        migrateTo("25");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM operation_setting", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM avatar_background", Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT selected_background FROM avatar WHERE member_id = 3",
                                String.class))
                .isNull();
        jdbc.update(
                "INSERT INTO avatar_background (member_id, landmark_key, landmark_name, stage,"
                        + " distance_meters, unlocked_at) VALUES (3, 'INCHEON', '인천',"
                        + " 'DOMESTIC', 60000, ?)",
                T0);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO avatar_background (member_id, landmark_key,"
                                                + " landmark_name, stage, distance_meters,"
                                                + " unlocked_at) VALUES (3, 'INCHEON', '인천',"
                                                + " 'DOMESTIC', 60000, ?)",
                                        T0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO avatar_background (member_id, landmark_key,"
                                                + " landmark_name, stage, distance_meters,"
                                                + " unlocked_at) VALUES (3, 'MOON', '달까지',"
                                                + " 'CREW_TOTAL', 384400000, ?)",
                                        T0))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("DELETE FROM avatar WHERE member_id = 3");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM avatar_background", Integer.class))
                .isZero();
    }
}
