package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * develop·prod와 같은 MySQL 8.4·Redis 8 위에서 컨텍스트가 뜨는지, Flyway 마이그레이션이 엔티티와 맞는지(ddl validate) 확인한다.
 * Docker가 없는 PC에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("integration")
class ContainersIntegrationTest {

    @Container @ServiceConnection
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Container @ServiceConnection
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:8").withExposedPorts(6379);

    @Autowired DataSource dataSource;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void mysqlAndRedis_areReachable() {
        Integer one = new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
        assertThat(one).isEqualTo(1);

        redisTemplate.opsForValue().set("ci:ping", "pong");
        assertThat(redisTemplate.opsForValue().get("ci:ping")).isEqualTo("pong");
    }
}
