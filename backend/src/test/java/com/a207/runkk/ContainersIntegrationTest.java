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
}
