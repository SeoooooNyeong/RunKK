package com.a207.runkk.infra;

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
 * 부록 F F.5.3 V20 마이그레이션 시험: V19까지 올린 PostgreSQL에 기존 회원·크루 소속 행을 두고 V20을 적용하면 태그 칼럼이 사라지고(D-91)
 * last_access_at이 updated_at으로 채워지며 기존 회원은 LINKED·SYSTEM 테마·근접 알림 켜짐이다.
 * ck_member_account·ck_member_withdrawn· 미이용 게스트 색인을 확인한다. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V20 회원 게스트·테마 마이그레이션(PostgreSQL)")
class MemberGuestV20MigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 1, 9, 0));
    private static final Timestamp T1 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 20, 7, 30));

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

    /** V19 형식의 회원 행(tags 칼럼 있음). */
    private long legacyMember(String nickname, String tags) {
        return jdbc.queryForObject(
                "INSERT INTO member (provider, provider_account_id, nickname, tags, tier, level,"
                        + " joined_at, unit, record_visibility, course_map_shared,"
                        + " profile_visibility, muted_notification_types, watch_linked, run_target,"
                        + " run_voice, run_vibration, run_follow_coach_plan,"
                        + " battle_default_distance, battle_voice, battle_vibration,"
                        + " battle_gap_alert_interval, valid_run_count, recent_scores,"
                        + " total_valid_meters, status, created_at, updated_at, version)"
                        + " VALUES ('KAKAO', ?, ?, ?, 'UNRANKED', 1, ?, 'KM', 'PUBLIC', false,"
                        + " 'PRIVATE', '', false, 'FREE', true, true, false, 'KM5', true, true,"
                        + " 'KM1', 0, '', 0, 'ACTIVE', ?, ?, 0) RETURNING id",
                Long.class,
                "k-" + nickname,
                nickname,
                tags,
                T0,
                T0,
                T1);
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ?",
                String.class,
                table);
    }

    @Test
    @DisplayName("기존 회원: 태그 칼럼 삭제, last_access_at = updated_at, LINKED·SYSTEM·근접 알림 켜짐")
    void existingRowsAreMigrated() {
        long memberId = legacyMember("달리는곰", "SOLO_RUN,DAWN");
        long crewId =
                jdbc.queryForObject(
                        "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                                + " invite_code_expires_at, created_at, version)"
                                + " VALUES ('곰크루', '', 'PUBLIC', 'FREE', 'ABC123', ?, ?, 0)"
                                + " RETURNING id",
                        Long.class,
                        T0,
                        T0);
        jdbc.update(
                "INSERT INTO crew_membership (crew_id, member_id, role, joined_at, crew_tags)"
                        + " VALUES (?, ?, 'LEADER', ?, 'SOLO_RUN')",
                crewId,
                memberId,
                T0);

        migrateTo("20");

        assertThat(columns("member"))
                .doesNotContain("tags")
                .contains(
                        "account_type",
                        "last_access_at",
                        "guest_credential_hash",
                        "screen_theme",
                        "battle_proximity_alert");
        assertThat(columns("crew_membership")).doesNotContain("crew_tags");
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT account_type, last_access_at, guest_credential_hash, screen_theme,"
                                + " battle_proximity_alert FROM member WHERE id = ?",
                        memberId);
        assertThat(row.get("account_type")).isEqualTo("LINKED");
        assertThat(row.get("last_access_at")).isEqualTo(T1);
        assertThat(row.get("guest_credential_hash")).isNull();
        assertThat(row.get("screen_theme")).isEqualTo("SYSTEM");
        assertThat(row.get("battle_proximity_alert")).isEqualTo(true);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM crew_membership WHERE member_id = ?",
                                Integer.class,
                                memberId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT indexdef FROM pg_indexes WHERE indexname ="
                                        + " 'idx_member_idle_guest'",
                                String.class))
                .contains("last_access_at")
                .contains("GUEST")
                .contains("ACTIVE");
    }

    @Test
    @DisplayName("ck_member_account·ck_member_withdrawn·값 CHECK")
    void constraints() {
        long linked = legacyMember("연동곰", "");
        migrateTo("20");

        // 게스트 행: 소셜 계정 없음 + 해시
        jdbc.update(
                "UPDATE member SET account_type = 'GUEST', provider = NULL,"
                        + " provider_account_id = NULL, guest_credential_hash = ? WHERE id = ?",
                "a".repeat(64),
                linked);
        // 게스트인데 소셜 계정이 있으면 거부
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET provider = 'KAKAO',"
                                                + " provider_account_id = 'k' WHERE id = ?",
                                        linked))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 연동 회원(ACTIVE)인데 소셜 계정이 없으면 거부
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET account_type = 'LINKED' WHERE id = ?",
                                        linked))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 탈퇴 행에 해시가 남으면 거부
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ?,"
                                                + " nickname = NULL WHERE id = ?",
                                        T1,
                                        linked))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ?, nickname = NULL,"
                        + " guest_credential_hash = NULL WHERE id = ?",
                T1,
                linked);
        // 탈퇴한 연동 회원은 소셜 계정 없이 남을 수 있다
        jdbc.update("UPDATE member SET account_type = 'LINKED' WHERE id = ?", linked);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET screen_theme = 'BLUE' WHERE id = ?",
                                        linked))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET account_type = 'ANON' WHERE id = ?",
                                        linked))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
