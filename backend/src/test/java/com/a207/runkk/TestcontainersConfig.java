package com.a207.runkk;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * develop·prod와 같은 PostgreSQL·Redis 컨테이너(설계 5.4). 통합 시험이 {@code @Import}로 쓰고, 접속 정보는
 * {@code @ServiceConnection}이 넣는다. 같은 설정을 쓰는 시험은 스프링 컨텍스트 캐시로 컨테이너를 함께 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8")).withExposedPorts(6379);
    }
}
