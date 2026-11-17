package com.a207.runkk.bootstrap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>The server belongs to the JVM, not to the context that happened to start it (risk R-109).
 * Tests keep up to 32 cached contexts and close the oldest one; when that context owned Redis,
 * every later Redis command waited 60 s and failed. So a port is started at most once per JVM,
 * closing a context leaves it running, and the library's own shutdown hook ({@code
 * RedisInstanceCleaner}) stops it when the JVM exits. A local bootRun has one context, so it still
 * runs until the app stops.
 */
@Configuration
@ConditionalOnProperty(prefix = "runkk.embedded-redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EmbeddedRedisConfig.Properties.class)
public class EmbeddedRedisConfig implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisConfig.class);
    private static final int PORT_PROBE_TIMEOUT_MILLIS = 200;

    /** Ports this JVM started a server on. Guarded by the class lock. */
    private static final Set<Integer> STARTED = new HashSet<>();

    private final int port;

    public EmbeddedRedisConfig(Properties properties) {
        this.port = properties.port();
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        startOnce(port);
    }

    private static synchronized void startOnce(int port) throws IOException {
        if (STARTED.contains(port)) {
            return;
        }
        if (isPortInUse(port)) {
            log.info("Embedded Redis: port {} already in use, reusing the existing server", port);
            return;
        }
        RedisServer server =
                RedisServer.newRedisServer().port(port).setting("maxmemory 64M").build();
        server.start();
        STARTED.add(port);
        log.info("Embedded Redis started on port {}, stops when the JVM exits", port);
    }

    private static boolean isPortInUse(int port) {
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
