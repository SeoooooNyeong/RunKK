package com.a207.runkk.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
 * V59 운영진 해제 마이그레이션(피그마 맞춤 W7, 결정 C5: 운영진 지정·해제 화면 삭제): V49까지 올린 PostgreSQL에 크루장·운영진·크루원 소속을 두고
 * V59를 적용하면 운영진(MANAGER)만 크루원(MEMBER)이 되고 크루장·크루원·퇴장 행의 다른 값은 그대로다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V59 운영진 → 크루원 마이그레이션(PostgreSQL)")
class CrewManagerV59MigrationIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshSchemaBeforeV59() {
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

    private long membership(long crewId, long memberId, String role, LocalDateTime leftAt) {
        return jdbc.queryForObject(
                "INSERT INTO crew_membership (crew_id, member_id, role, joined_at, left_at,"
                        + " leave_reason, crew_intro) VALUES (?, ?, ?, ?, ?, ?, '소개') RETURNING id",
                Long.class,
                crewId,
                memberId,
                role,
                Timestamp.valueOf(T0),
                leftAt == null ? null : Timestamp.valueOf(leftAt),
                leftAt == null ? null : "WITHDREW");
    }

    private String role(long membershipId) {
        return jdbc.queryForObject(
                "SELECT role FROM crew_membership WHERE id = ?", String.class, membershipId);
    }

    @Test
    @DisplayName("운영진은 크루원이 되고 크루장·크루원은 그대로, 가입 일시·소개는 남는다")
    void managersBecomeMembers() {
        long crewId =
                jdbc.queryForObject(
                        "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                                + " invite_code_expires_at, created_at, version)"
                                + " VALUES ('오십구크루', '', 'PUBLIC', 'FREE', 'V59V59', ?, ?, 0)"
                                + " RETURNING id",
                        Long.class,
                        Timestamp.valueOf(T0),
                        Timestamp.valueOf(T0));
        long leader = membership(crewId, 1, "LEADER", null);
        long manager = membership(crewId, 2, "MANAGER", null);
        long member = membership(crewId, 3, "MEMBER", null);
        long leftManager = membership(crewId, 4, "MANAGER", T0.plusDays(1));

        migrateTo("59");

        assertThat(role(leader)).isEqualTo("LEADER");
        assertThat(role(manager)).isEqualTo("MEMBER");
        assertThat(role(member)).isEqualTo("MEMBER");
        assertThat(role(leftManager)).isEqualTo("MEMBER");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM crew_membership WHERE role = 'MANAGER'",
                                Integer.class))
                .isZero();
        Map<String, Object> kept =
                jdbc.queryForMap(
                        "SELECT joined_at, crew_intro FROM crew_membership WHERE id = ?", manager);
        assertThat(kept.get("crew_intro")).isEqualTo("소개");
        assertThat(((Timestamp) kept.get("joined_at")).toLocalDateTime()).isEqualTo(T0);
    }

    @Test
    @DisplayName("되돌림 근거(코드 리뷰 M5): 바꾸기 전 운영진 소속 행이 crew_manager_backup_v59에 남는다, 운영진이 없으면 빈 표")
    void managersAreBackedUp() {
        long crewId =
                jdbc.queryForObject(
                        "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                                + " invite_code_expires_at, created_at, version)"
                                + " VALUES ('백업크루', '', 'PUBLIC', 'FREE', 'BAK059', ?, ?, 0)"
                                + " RETURNING id",
                        Long.class,
                        Timestamp.valueOf(T0),
                        Timestamp.valueOf(T0));
        membership(crewId, 1, "LEADER", null);
        long manager = membership(crewId, 2, "MANAGER", null);
        long leftManager = membership(crewId, 4, "MANAGER", T0.plusDays(1));

        migrateTo("59");

        assertThat(
                        jdbc.queryForList(
                                "SELECT membership_id, crew_id, member_id FROM"
                                        + " crew_manager_backup_v59 ORDER BY membership_id"))
                .containsExactly(
                        Map.of("membership_id", manager, "crew_id", crewId, "member_id", 2L),
                        Map.of("membership_id", leftManager, "crew_id", crewId, "member_id", 4L));
    }

    @Test
    @DisplayName("M5: 운영진이 이미 없는 DB에서도 V59가 돌고 백업 표는 비어 있다")
    void noManagers_emptyBackup() {
        migrateTo("59");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM crew_manager_backup_v59", Integer.class))
                .isZero();
    }
}
