package com.a207.runkk.realtime;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * 회원 ID ↔ WebSocket 세션 대응(설계 6.1.13). 회원마다 세션 하나이고 새로 연결하면 옛 세션을 1000 NORMAL로 닫는다. 대결·크루런·채팅이 이 연결
 * 하나를 함께 쓰므로(D-103) 토큰 만료 정책도 한 곳이다: 등록할 때 핸드셰이크 접근 토큰의 만료 시각에 그 세션을 4401 TOKEN_EXPIRED로 닫는 작업을
 * TaskScheduler에 예약하고, 등록을 빼거나 새 연결로 바뀌면 예약을 거둔다(D-110, F.3.3). 만료 예약은 시각 작업(batch.*) 스케줄러와 따로 둔 작은
 * 스케줄러에서 돌고 취소한 예약은 대기열에서 바로 지운다(연결마다 예약이 쌓이지 않고, 닫기가 0시·3시 작업에 밀리지 않는다). 보내기는 여러 스레드에서 오므로 세션을 동시
 * 송신 장식으로 감싼다. 서버가 내려갈 때 1012 SERVICE_RESTARTED를 보내 앱이 재연결하게 한다(5.5 종료).
 */
@Component
@ConditionalOnProperty(name = "runkk.live", havingValue = "websocket", matchIfMissing = true)
public class SocketSessionRegistry {

    /** 핸드셰이크에 쓴 접근 토큰의 만료 시각이 됨(F.3.3). 앱은 토큰을 갱신하고 곧바로 재연결한다(D-110, D-153). */
    public static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "TOKEN_EXPIRED");

    private static final Logger log = LoggerFactory.getLogger(SocketSessionRegistry.class);
    private static final int SEND_TIME_LIMIT_MILLIS = 2_000;
    private static final int BUFFER_LIMIT_BYTES = 64 * 1024;
    private static final int EXPIRY_THREADS = 2;

    /** 회원 ID → 등록된 연결(세션 장식과 만료 예약). */
    private final Map<Long, Registration> sessions = new ConcurrentHashMap<>();

    private final TaskScheduler scheduler;
    private final ThreadPoolTaskScheduler ownScheduler;

    /** 운영: 만료 예약 전용 스케줄러를 만든다. */
    @Autowired
    public SocketSessionRegistry() {
        ThreadPoolTaskScheduler expiry = new ThreadPoolTaskScheduler();
        expiry.setPoolSize(EXPIRY_THREADS);
        expiry.setRemoveOnCancelPolicy(true);
        expiry.setThreadNamePrefix("runkk-socket-expiry-");
        expiry.setDaemon(true);
        expiry.initialize();
        this.scheduler = expiry;
        this.ownScheduler = expiry;
    }

    /** 시험: 예약 시각을 손으로 진행하는 스케줄러를 넣는다. */
    SocketSessionRegistry(TaskScheduler scheduler) {
        this.scheduler = scheduler;
        this.ownScheduler = null;
    }

    /** 연결 하나와 그 만료 예약. 예약은 등록한 뒤에 채운다(이미 지난 만료가 등록 전에 돌아 빠지지 않게). */
    private static final class Registration {
        private final WebSocketSession session;
        private volatile ScheduledFuture<?> expiry;

        private Registration(WebSocketSession session) {
            this.session = session;
        }

        boolean isFor(String sessionId) {
            return session.getId().equals(sessionId);
        }

        void cancelExpiry() {
            ScheduledFuture<?> scheduled = expiry;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }

    /**
     * 회원의 연결을 등록하고 expiresAt에 4401로 닫는 작업을 예약한다. 같은 회원의 이전 연결은 1000 NORMAL로 닫고 그 예약을 거둔다. 만료가 이미
     * 지났으면 예약이 곧바로 돌아 닫는다.
     *
     * @param expiresAt 핸드셰이크 접근 토큰의 만료 시각(member.AccessGrant.expiresAt)
     */
    public void register(long memberId, WebSocketSession session, Instant expiresAt) {
        Registration registration =
                new Registration(
                        new ConcurrentWebSocketSessionDecorator(
                                session, SEND_TIME_LIMIT_MILLIS, BUFFER_LIMIT_BYTES));
        Registration previous = sessions.put(memberId, registration);
        if (previous != null) {
            previous.cancelExpiry();
            if (!previous.isFor(session.getId())) {
                close(previous.session, CloseStatus.NORMAL);
            }
        }
        registration.expiry = scheduler.schedule(() -> expire(memberId, registration), expiresAt);
    }

    /** 그 세션이 지금 등록된 세션일 때만 빼고 만료 예약을 거둔다(새 연결이 먼저 등록됐을 수 있다). */
    public void unregister(long memberId, WebSocketSession session) {
        sessions.computeIfPresent(
                memberId,
                (id, current) -> {
                    if (!current.isFor(session.getId())) {
                        return current;
                    }
                    current.cancelExpiry();
                    return null;
                });
    }

    public Optional<WebSocketSession> find(long memberId) {
        return Optional.ofNullable(sessions.get(memberId))
                .map(r -> r.session)
                .filter(WebSocketSession::isOpen);
    }

    /** 만료 예약: 그 등록이 아직 지금 등록이면 빼고 4401로 닫는다(이미 바뀐 연결은 건드리지 않는다). */
    private void expire(long memberId, Registration registration) {
        if (!sessions.remove(memberId, registration)) {
            return;
        }
        log.info("socket_token_expired memberId={}", memberId);
        close(registration.session, TOKEN_EXPIRED);
    }

    @PreDestroy
    void closeAllForRestart() {
        sessions.values()
                .forEach(
                        r -> {
                            r.cancelExpiry();
                            close(r.session, CloseStatus.SERVICE_RESTARTED);
                        });
        sessions.clear();
        if (ownScheduler != null) {
            ownScheduler.shutdown();
        }
    }

    /** 세션을 닫는다. 실패는 디버그 로그만 남긴다(이미 끊긴 연결). LiveSocketHandler가 만료 방어에도 쓴다. */
    static void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.debug("socket_close_failed error={}", e.getClass().getSimpleName());
        }
    }
}
