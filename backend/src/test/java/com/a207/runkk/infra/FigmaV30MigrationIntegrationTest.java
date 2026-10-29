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
 * V30 피그마 v0.14 마이그레이션(설계 5.6.4, test.md TC-9857): V28까지 올린 PostgreSQL에 기존 회원·Runimal 확정 유형을 두고
 * V30을 적용하면 새 표·열이 생기고 기존 값이 옮겨지며, 새 CHECK·부분 고유 인덱스가 규칙을 지킨다. V29는 다른 MR이 쓰므로 비어 있다. Docker가 없으면
 * 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V30 피그마 v0.14 마이그레이션(PostgreSQL)")
class FigmaV30MigrationIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaBeforeV30() {
        jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        migrateTo("28");
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private long member(String nickname, String profileVisibility, String photoUrl) {
        return jdbc.queryForObject(
                "INSERT INTO member (provider, provider_account_id, nickname, photo_url, tier,"
                    + " level, joined_at, unit, record_visibility, course_map_shared,"
                    + " profile_visibility, muted_notification_types, watch_linked, run_target,"
                    + " run_voice, run_vibration, run_follow_coach_plan, battle_default_distance,"
                    + " battle_voice, battle_vibration, battle_gap_alert_interval, valid_run_count,"
                    + " recent_scores, total_valid_meters, status, created_at, updated_at, version,"
                    + " last_access_at) VALUES ('KAKAO', ?, ?, ?, 'UNRANKED', 1, ?, 'KM', 'PUBLIC',"
                    + " false, ?, '', false, 'FREE', true, true, false, 'KM5', true, true, 'KM1',"
                    + " 0, '', 0, 'ACTIVE', ?, ?, 0, ?) RETURNING id",
                Long.class,
                "k-" + nickname,
                nickname,
                photoUrl,
                Timestamp.valueOf(T0),
                profileVisibility,
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0));
    }

    private void runimal(long memberId, String confirmed) {
        jdbc.update(
                "INSERT INTO runimal_profile (member_id, valid_run_count, confirmed_type, version)"
                        + " VALUES (?, 10, ?, 0)",
                memberId,
                confirmed);
    }

    @Test
    @DisplayName(
            "TC-9857 기존 값 이동: 공개 두 항목 = profile_visibility, 사진 비움, 가이드 거짓, 확정 이력 = 확정 유형, V29 비어"
                    + " 있음")
    void migratesExistingRows() {
        long open = member("공개곰", "PUBLIC", "https://cdn/p.png");
        long closed = member("비공개곰", "PRIVATE", null);
        runimal(open, "LONE_WOLF");
        runimal(closed, null);

        migrateTo("30");

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT age_band_visibility, gender_visibility, photo_url, guide_seen,"
                                + " profile_visibility FROM member WHERE id = ?",
                        open);
        assertThat(row.get("age_band_visibility")).isEqualTo("PUBLIC");
        assertThat(row.get("gender_visibility")).isEqualTo("PUBLIC");
        assertThat(row.get("photo_url")).isNull();
        assertThat(row.get("guide_seen")).isEqualTo(false);
        assertThat(row.get("profile_visibility")).as("옛 열은 남긴다").isEqualTo("PUBLIC");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT age_band_visibility FROM member WHERE id = ?",
                                String.class,
                                closed))
                .isEqualTo("PRIVATE");
        assertThat(jdbc.queryForList("SELECT member_id, type FROM runimal_confirmed_history"))
                .containsExactly(Map.of("member_id", open, "type", "LONE_WOLF"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM runimal_type_share", Integer.class))
                .isZero();
        List<String> versions =
                jdbc.queryForList(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY"
                                + " installed_rank",
                        String.class);
        assertThat(versions).contains("28", "30").doesNotContain("29");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.columns WHERE"
                                        + " table_name = 'run_record' AND column_name IN"
                                        + " ('route_shape', 'evaluation_kind', 'evaluation_tier',"
                                        + " 'evaluation_valid_runs', 'evaluation_runimal_type')",
                                Integer.class))
                .isEqualTo(5);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_name IN"
                                        + " ('friend_invite', 'run_record_tombstone',"
                                        + " 'runimal_confirmed_history', 'runimal_type_share')",
                                Integer.class))
                .isEqualTo(4);
    }

    private void invite(long inviter, String token, String status) {
        jdbc.update(
                "INSERT INTO friend_invite (inviter_member_id, distance_meters, token, status,"
                        + " created_at, expires_at, version) VALUES (?, 5000, ?, ?, ?, ?, 0)",
                inviter,
                token,
                status,
                Timestamp.valueOf(T0),
                Timestamp.valueOf(T0.plusMinutes(5)));
    }

    @Test
    @DisplayName(
            "friend_invite: 회원당 PENDING 하나(부분 고유 인덱스), 끝난 초대는 여럿, 토큰 고유, ACCEPTED는 참가자·대결 필요, 거리"
                    + " 1·3·5·10km")
    void friendInviteConstraints() {
        migrateTo("30");
        invite(7, "tokenA", "PENDING");
        assertThatThrownBy(() -> invite(7, "tokenB", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        invite(7, "tokenC", "EXPIRED");
        invite(7, "tokenD", "CANCELLED");
        assertThatThrownBy(() -> invite(8, "tokenA", "CANCELLED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> invite(9, "tokenE", "ACCEPTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> invite(9, "tokenF", "LOST"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO friend_invite (inviter_member_id,"
                                                + " distance_meters, token, status, created_at,"
                                                + " expires_at, version) VALUES (10, 21097, 'h',"
                                                + " 'PENDING', now(), now(), 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("battle: FRIEND는 랜덤처럼 기록 상대가 없다, personal_best FULL, 셀럽 실명 동의는 둘 다, 유형 비율 수 검사")
    void checkConstraints() {
        migrateTo("30");
        String battle =
                "INSERT INTO battle (type, distance_meters, status, created_at, corrected,"
                        + " winner_opponent, version, opponent_kind) VALUES (?, 5000,"
                        + " 'WAITING_START', now(), false, false, 0, ?)";
        jdbc.update(battle, "FRIEND", null);
        assertThatThrownBy(() -> jdbc.update(battle, "FRIEND", "VIRTUAL"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(battle, "DUEL", null))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO personal_record_book (member_id) VALUES (1)");
        jdbc.update(
                "INSERT INTO personal_best (member_id, distance_key, record_id, elapsed_millis)"
                        + " VALUES (1, 'FULL', 'r', 10800000)");

        String celeb =
                "INSERT INTO celebrity_record (anonymous_name, distance_meters, finish_millis,"
                        + " source_name, registered_at, real_name_consented_at,"
                        + " real_name_consent_basis) VALUES ('코치 K', 5000, 1500000, '대회', now(),"
                        + " ?, ?)";
        jdbc.update(celeb, Timestamp.valueOf(T0), "서면 동의");
        jdbc.update(celeb, new Object[] {null, null});
        assertThatThrownBy(() -> jdbc.update(celeb, Timestamp.valueOf(T0), null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO runimal_type_share (type, confirmed_count,"
                                                + " total_confirmed, computed_at) VALUES ('X', 3,"
                                                + " 2, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
