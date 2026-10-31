package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import com.a207.runkk.bootstrap.RunkkApplication;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * develop·prod와 같은 PostgreSQL·Redis 위에서 컨텍스트가 뜨는지, Flyway 마이그레이션이 엔티티와 맞는지(ddl validate) 확인한다.
 * Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("PostgreSQL·Redis 컨테이너와 마이그레이션 검증")
class ContainersIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void postgresAndRedis_areReachable() {
        String version =
                new JdbcTemplate(dataSource).queryForObject("select version()", String.class);
        assertThat(version).startsWith("PostgreSQL");

        redisTemplate.opsForValue().set("ci:ping", "pong");
        assertThat(redisTemplate.opsForValue().get("ci:ping")).isEqualTo("pong");
    }

    /** V29: analytics 스키마는 runkk_ai가 소유하고, runkk_ai는 public을 읽기만 한다(S15P31A207-172). */
    @Test
    void analyticsSchema_isOwnedByAiRole_andPublicIsReadOnly() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(
                        jdbc.queryForObject(
                                "select pg_get_userbyid(nspowner) from pg_namespace where nspname ="
                                        + " 'analytics'",
                                String.class))
                .isEqualTo("runkk_ai");
        assertThat(
                        jdbc.queryForObject(
                                "select rolcanlogin from pg_roles where rolname = 'runkk_ai'",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select has_table_privilege('runkk_ai', 'public.run_record',"
                                        + " 'SELECT')",
                                Boolean.class))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select has_table_privilege('runkk_ai', 'public.run_record',"
                                        + " 'INSERT')",
                                Boolean.class))
                .isFalse();
    }
}
