package com.a207.runkk.global.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

/**
 * Starts an in-process Redis so the app runs with no external services. Enabled only when
 * runkk.embedded-redis.enabled=true (local and test profiles); develop/prod use the real Redis from
 * docker compose. If something already listens on the port (a real Redis, or a leftover embedded
 * server after a force-killed JVM) it is reused instead of failing startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "runkk.embedded-redis", name = "enabled", havingValue = "true")
public class EmbeddedRedisConfig implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisConfig.class);

    private final int port;
    private RedisServer server;

    public EmbeddedRedisConfig(@Value("${runkk.embedded-redis.port}") int port) {
        this.port = port;
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        if (isPortInUse()) {
            log.info("Embedded Redis: port {} already in use, reusing the existing server", port);
            return;
        }
        server = RedisServer.newRedisServer().port(port).setting("maxmemory 64M").build();
        server.start();
        log.info("Embedded Redis started on port {}", port);
    }

    @Override
    public void destroy() throws IOException {
        if (server != null) {
            server.stop();
            log.info("Embedded Redis stopped");
        }
    }

    private boolean isPortInUse() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
