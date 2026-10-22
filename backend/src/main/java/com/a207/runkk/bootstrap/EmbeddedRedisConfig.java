package com.a207.runkk.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import redis.embedded.RedisServer;

/**
 * Starts an in-process Redis so the app runs with no external services. Enabled only when
 * runkk.embedded-redis.enabled=true (local and test profiles); develop/prod use the real Redis from
 * docker compose. If something already listens on the port (a real Redis, or a leftover embedded
 * server after a force-killed JVM) it is reused instead of failing startup. Settings are bound with
 * {@code @ConfigurationProperties} (stage-1 review L3).
 */
@Configuration
@ConditionalOnProperty(prefix = "runkk.embedded-redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EmbeddedRedisConfig.Properties.class)
public class EmbeddedRedisConfig implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisConfig.class);
    private static final int PORT_PROBE_TIMEOUT_MILLIS = 200;

    private final int port;
    private RedisServer server;

    public EmbeddedRedisConfig(Properties properties) {
        this.port = properties.port();
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
            socket.connect(new InetSocketAddress("127.0.0.1", port), PORT_PROBE_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * runkk.embedded-redis.* settings.
     *
     * @param enabled start the embedded server
     * @param port listening port
     */
    @ConfigurationProperties("runkk.embedded-redis")
    public record Properties(boolean enabled, int port) {}
}
