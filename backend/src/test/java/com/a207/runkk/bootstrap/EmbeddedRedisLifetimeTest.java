package com.a207.runkk.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** 위험 R-109: 내장 Redis를 처음 띄운 문맥이 시험 문맥 캐시에서 밀려 닫혀도 같은 JVM의 다른 문맥은 Redis를 계속 써야 한다. */
@DisplayName("내장 Redis 수명")
class EmbeddedRedisLifetimeTest {

    @Test
    @DisplayName("R-109: Redis를 띄운 문맥이 닫혀도 다른 문맥이 쓰는 Redis는 PING에 답한다")
    void redisSurvivesClosingTheContextThatStartedIt() throws IOException {
        int port = freePort();
        AnnotationConfigApplicationContext first = context(port);
        AnnotationConfigApplicationContext second = context(port);

        first.close();

        try {
            assertThat(ping(port)).isEqualTo("+PONG");
        } finally {
            second.close();
        }
    }

    private static AnnotationConfigApplicationContext context(int port) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                        "runkk.embedded-redis.enabled=true", "runkk.embedded-redis.port=" + port)
                .applyTo(context);
        context.register(EmbeddedRedisConfig.class);
        context.refresh();
        return context;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String ping(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 1_000);
            socket.setSoTimeout(1_000);
            OutputStream out = socket.getOutputStream();
            out.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            byte[] reply = new byte[5];
            int read = in.readNBytes(reply, 0, reply.length);
            return new String(reply, 0, read, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            return "연결 실패: " + e.getMessage();
        }
    }
}
