package com.a207.runkk.infra.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
 * 부록 F F.5.3 V23(D-101, 리뷰 M4): crew_chat_message·crew_chat_reaction 표와 색인, 크루 삭제 FK
 * CASCADE(TC-6706), 묶음 알림 부분 유일 색인과 기존 겹친 행 정리. Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V23 크루 채팅·묶음 알림 마이그레이션(PostgreSQL)")
class CrewChatMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));

    private static final Timestamp T0 = Timestamp.valueOf(LocalDateTime.of(2026, 10, 22, 9, 0));

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

    private long crew(String name) {
        return jdbc.queryForObject(
                "INSERT INTO crew (name, tags, visibility, join_policy, invite_code,"
                        + " invite_code_expires_at, created_at, version)"
                        + " VALUES (?, '', 'PUBLIC', 'FREE', ?, ?, ?, 0) RETURNING id",
                Long.class,
                name,
                name.substring(0, 1) + "00000",
                T0,
                T0);
    }

    private long message(long crewId, Long authorId, String clientMessageId) {
        return jdbc.queryForObject(
                "INSERT INTO crew_chat_message (crew_id, author_id, client_message_id, content,"
                        + " sent_at) VALUES (?, ?, ?, '안녕', ?) RETURNING id",
                Long.class,
                crewId,
                authorId,
                clientMessageId,
                T0);
    }

    private void unreadChat(long recipient, String target, String content, boolean read) {
        jdbc.update(
                "INSERT INTO notification (recipient_id, type, content, occurred_at, is_read,"
                    + " target_screen, target_id) VALUES (?, 'CREW_CHAT', ?, ?, ?, 'CREW_CHAT', ?)",
                recipient,
                content,
                T0,
                read,
                target);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("같은 작성자의 같은 clientMessageId는 한 번만, 작성자가 빈(탈퇴) 행끼리는 겹쳐도 된다. 500글자는 이모지도 글자로 센다")
    void messageConstraints() {
        migrateTo("23");
        long crewId = crew("가크루");
        message(crewId, 3L, "u1");
        assertThatThrownBy(() -> message(crewId, 3L, "u1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_crew_chat_client");
        message(crewId, 4L, "u1");
        message(crewId, null, "u2");
        message(crewId, null, "u2");
        jdbc.update(
                "INSERT INTO crew_chat_message (crew_id, author_id, client_message_id, content,"
                        + " sent_at) VALUES (?, 5, 'u3', ?, ?)",
                crewId,
                "🏃".repeat(500),
                T0);
        assertThat(count("SELECT count(*) FROM crew_chat_message")).isEqualTo(5);
        assertThat(
                        count(
                                "SELECT count(*) FROM pg_indexes WHERE indexname IN"
                                        + " ('idx_crew_chat_message_crew_id',"
                                        + " 'idx_crew_chat_message_author',"
                                        + " 'idx_crew_chat_reaction_member')"))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("리액션은 (메시지, 회원, 종류)가 기본 키이고 LIKE·HEART만, ON CONFLICT DO NOTHING이면 한 행")
    void reactionConstraints() {
        migrateTo("23");
        long id = message(crew("나크루"), 3L, "u1");
        String insert =
                "INSERT INTO crew_chat_reaction (message_id, member_id, kind, reacted_at)"
                        + " VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING";
        assertThat(jdbc.update(insert, id, 4, "LIKE", T0)).isEqualTo(1);
        assertThat(jdbc.update(insert, id, 4, "LIKE", T0)).isZero();
        assertThat(jdbc.update(insert, id, 4, "HEART", T0)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insert, id, 4, "WOW", T0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("TC-6706 크루 행을 지우면 그 크루의 메시지·리액션이 FK CASCADE로 지워지고 다른 크루는 남는다")
    void crewDeletion_cascades() {
        migrateTo("23");
        long seven = crew("다크루");
        long eight = crew("라크루");
        for (int i = 0; i < 50; i++) {
            long id = message(seven, 3L, "s" + i);
            if (i < 20) {
                jdbc.update(
                        "INSERT INTO crew_chat_reaction (message_id, member_id, kind, reacted_at)"
                                + " VALUES (?, 4, 'LIKE', ?)",
                        id,
                        T0);
            }
        }
        message(eight, 3L, "other");

        jdbc.update("DELETE FROM crew WHERE id = ?", seven);

        assertThat(count("SELECT count(*) FROM crew_chat_message WHERE crew_id = ?", seven))
                .isZero();
        assertThat(count("SELECT count(*) FROM crew_chat_reaction")).isZero();
        assertThat(count("SELECT count(*) FROM crew_chat_message WHERE crew_id = ?", eight))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("리뷰 M4: 겹친 읽지 않은 CREW_CHAT은 최근 행만 남기고, 그 뒤로는 같은 대상의 읽지 않은 행을 둘 만들 수 없다")
    void collapsingNotificationIndex() {
        unreadChat(1, "7", "옛 행", false);
        unreadChat(1, "7", "최근 행", false);
        unreadChat(1, "7", "읽은 행", true);
        unreadChat(2, "7", "다른 회원", false);

        migrateTo("23");

        assertThat(count("SELECT count(*) FROM notification WHERE recipient_id = 1")).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content FROM notification WHERE recipient_id = 1 AND NOT"
                                        + " is_read",
                                String.class))
                .isEqualTo("최근 행");
        assertThatThrownBy(() -> unreadChat(1, "7", "겹침", false))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_notification_unread_collapse");
        unreadChat(1, "7", "또 읽은 행", true);
        unreadChat(1, "8", "다른 크루", false);
        jdbc.update(
                "INSERT INTO notification (recipient_id, type, content, occurred_at, is_read,"
                    + " target_screen, target_id) VALUES (1, 'JOIN_DECIDED', '승인', ?, false,"
                    + " 'CREW_HOME', '7'), (1, 'JOIN_DECIDED', '승인', ?, false, 'CREW_HOME', '7')",
                T0,
                T0);
        assertThat(count("SELECT count(*) FROM notification WHERE recipient_id = 1")).isEqualTo(6);
    }
}
