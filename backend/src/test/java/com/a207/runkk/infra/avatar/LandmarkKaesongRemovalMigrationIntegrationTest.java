package com.a207.runkk.infra.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.policy.SettingKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * V65(D-756, D-761): 운영 기준값 LANDMARKS 행에서 개성(KAESONG) 원소를 빼고 춘천(CHUNCHEON, 국내, 왕복 150km) 원소를 거리 순
 * 자리에 넣는다. KAESONG 해금 행과 선택 배경을 지운다. 행이 없으면 아무것도 하지 않는다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V65 개성 삭제·춘천 추가 마이그레이션(PostgreSQL)")
class LandmarkKaesongRemovalMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 11, 9, 9, 0));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaAtV62() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("62");
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

    private void landmarks(String json) {
        jdbc.update(
                "INSERT INTO operation_setting (setting_key, setting_value, version, updated_at)"
                        + " VALUES ('LANDMARKS', ?, 3, ?)",
                json,
                T0);
    }

    private static String element(String key, String name, long meters, boolean roundTrip) {
        return "{\"key\":\""
                + key
                + "\",\"stage\":\"DOMESTIC\",\"name\":\""
                + name
                + "\",\"distance\":{\"meters\":"
                + meters
                + "},\"roundTrip\":"
                + roundTrip
                + "}";
    }

    private static String table(String... elements) {
        return "{\"landmarks\":[" + String.join(",", elements) + "]}";
    }

    private String stored() {
        return jdbc.queryForObject(
                "SELECT setting_value FROM operation_setting WHERE setting_key = 'LANDMARKS'",
                String.class);
    }

    private long storedVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM operation_setting WHERE setting_key = 'LANDMARKS'",
                Long.class);
    }

    private List<String> keysOf(String json) throws Exception {
        List<String> keys = new ArrayList<>();
        for (JsonNode node : JSON.readTree(json).get("landmarks")) {
            keys.add(node.get("key").asText());
        }
        return keys;
    }

    private JsonNode elementOf(String json, String key) throws Exception {
        for (JsonNode node : JSON.readTree(json).get("landmarks")) {
            if (key.equals(node.get("key").asText())) {
                return node;
            }
        }
        throw new AssertionError("no element " + key);
    }

    private void avatar(long memberId, String selected) {
        jdbc.update(
                "INSERT INTO avatar (member_id, creation_count, created_on_last_day, body_shape,"
                        + " skin_tone, version, selected_background) VALUES (?, 0, 0, 'STAGE1',"
                        + " 'DAYTIME', 0, ?)",
                memberId,
                selected);
    }

    private void unlocked(long memberId, String key, String name, long meters) {
        jdbc.update(
                "INSERT INTO avatar_background (member_id, landmark_key, landmark_name, stage,"
                        + " distance_meters, unlocked_at) VALUES (?, ?, ?, 'DOMESTIC', ?, ?)",
                memberId,
                key,
                name,
                meters,
                T0);
    }

    @Test
    @DisplayName("운영 행에서 KAESONG을 빼고 CHUNCHEON(국내·왕복·150km)을 인천 다음에 넣으며 결과는 LANDMARKS 검사를 통과한다")
    void replacesKaesongWithChuncheon() throws Exception {
        landmarks(
                table(
                        element("INCHEON", "인천", 60_000, true),
                        element("KAESONG", "개성", 120_000, true),
                        element("DAEJEON", "대전", 280_000, true),
                        element("JEONJU", "전주", 400_000, true)));

        migrateTo("65");

        String json = stored();
        assertThat(keysOf(json)).containsExactly("INCHEON", "CHUNCHEON", "DAEJEON", "JEONJU");
        JsonNode chuncheon = elementOf(json, "CHUNCHEON");
        assertThat(chuncheon.get("stage").asText()).isEqualTo("DOMESTIC");
        assertThat(chuncheon.get("name").asText()).isEqualTo("춘천");
        assertThat(chuncheon.get("distance").get("meters").asLong()).isEqualTo(150_000);
        assertThat(chuncheon.get("roundTrip").asBoolean()).isTrue();
        assertThat(SettingKey.LANDMARKS.validate(json).valid()).isTrue();
        assertThat(storedVersion()).isEqualTo(4);
    }

    @Test
    @DisplayName("다른 운영 기준값 행이 JSON이 아니어도 LANDMARKS 행만 읽어 실패하지 않는다(리뷰 R2-16)")
    void ignoresNonJsonRowsOfOtherKeys() throws Exception {
        jdbc.update(
                "INSERT INTO operation_setting (setting_key, setting_value, version, updated_at)"
                        + " VALUES ('ZZ_PLAIN_TEXT', 'not json at all', 0, ?)",
                T0);
        landmarks(
                table(
                        element("INCHEON", "인천", 60_000, true),
                        element("KAESONG", "개성", 120_000, true)));

        migrateTo("65");

        assertThat(keysOf(stored())).containsExactly("INCHEON", "CHUNCHEON");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT setting_value FROM operation_setting WHERE setting_key ="
                                        + " 'ZZ_PLAIN_TEXT'",
                                String.class))
                .isEqualTo("not json at all");
    }

    @Test
    @DisplayName("LANDMARKS 값이 JSON이 아니면 건드리지 않는다")
    void leavesNonJsonLandmarksAlone() {
        landmarks("not json");

        migrateTo("65");

        assertThat(stored()).isEqualTo("not json");
        assertThat(storedVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("150km보다 먼 원소가 없으면 끝에 넣고, CHUNCHEON이 이미 있으면 다시 넣지 않고 행도 바꾸지 않는다")
    void appendsAtEnd_andSkipsExistingChuncheon() throws Exception {
        landmarks(table(element("INCHEON", "인천", 60_000, true)));
        migrateTo("65");
        assertThat(keysOf(stored())).containsExactly("INCHEON", "CHUNCHEON");

        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("62");
        String already =
                table(
                        element("INCHEON", "인천", 60_000, true),
                        element("CHUNCHEON", "춘천", 150_000, true));
        landmarks(already);
        migrateTo("65");
        assertThat(keysOf(stored())).containsExactly("INCHEON", "CHUNCHEON");
        assertThat(storedVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("KAESONG 해금 행과 KAESONG 선택만 지우고(선택은 기본 배경), 다른 키와 LANDMARKS 행 없음은 그대로다")
    void removesKaesongUnlocksAndSelection() {
        avatar(3, "KAESONG");
        avatar(4, "INCHEON");
        avatar(5, null);
        unlocked(3, "INCHEON", "인천", 60_000);
        unlocked(3, "KAESONG", "개성", 120_000);
        unlocked(4, "INCHEON", "인천", 60_000);

        migrateTo("65");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM operation_setting WHERE setting_key ="
                                        + " 'LANDMARKS'",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForList(
                                "SELECT member_id || ':' || landmark_key FROM avatar_background"
                                        + " ORDER BY member_id, landmark_key",
                                String.class))
                .containsExactly("3:INCHEON", "4:INCHEON");
        assertThat(
                        jdbc.queryForList(
                                "SELECT coalesce(selected_background, '-') FROM avatar"
                                        + " ORDER BY member_id",
                                String.class))
                .containsExactly("-", "INCHEON", "-");
    }
}
