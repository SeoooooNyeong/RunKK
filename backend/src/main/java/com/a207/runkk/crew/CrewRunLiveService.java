package com.a207.runkk.crew;

import com.a207.runkk.common.ProgressReport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 진행 중인 크루런의 진행값 중계(설계 6.1.7, SQ-09, srs.md 6.3 5.7.3). 세션은 출발 때 열리고 종료 때 지워지므로 세션이 없으면 진행 중이 아니라고
 * 보고 무시한다. 1초 진행값은 DB에 쓰지 않는다(6.5). 한 크루런의 세션은 게시글 ID 단위로 하나씩 처리한다(인스턴스 하나, 5.4).
 */
@Service
public class CrewRunLiveService {

    private static final Logger log = LoggerFactory.getLogger(CrewRunLiveService.class);

    private final CrewLiveSessionStore crewLiveSessionStore;
    private final CrewRunMessenger crewRunMessenger;

    /**
     * 세션 잠금 줄무늬(striped lock). 세션 ID를 고정된 잠금 64개 중 하나에 대응시켜, 잠금 객체를 지우고 다시 만드는 사이에 같은 세션에 잠금이 둘 생기는
     * 경합이 없다(코드 리뷰 2차 L7). 다른 세션이 같은 잠금을 나눠 쓰면 잠깐 기다릴 뿐 정합성은 같다.
     */
    private static final int LOCK_STRIPES = 64;

    private final Object[] sessionLocks = newStripes();

    public CrewRunLiveService(
            CrewLiveSessionStore crewLiveSessionStore, CrewRunMessenger crewRunMessenger) {
        this.crewLiveSessionStore = crewLiveSessionStore;
        this.crewRunMessenger = crewRunMessenger;
    }

    /** postId는 realtime이 수신 메시지의 채널 ID로 넘긴다(D-48). 세션이 없거나 참가자가 아니면 무시한다. */
    public void onProgress(long memberId, long postId, ProgressReport r, LocalDateTime now) {
        if (crewLiveSessionStore.load(postId).isEmpty()) {
            return; // 없는 채널 ID로 잠금 객체가 쌓이지 않게 세션부터 확인한다
        }
        update(postId, session -> session.onProgress(memberId, r, now));
    }

    /** 진행 중 세션마다 3초 넘게 진행값이 없는 참가자를 "연결 확인 중"으로 알린다. 한 세션의 실패가 다른 세션을 막지 않는다. */
    public void detectTimeouts(LocalDateTime now) {
        for (long postId : crewLiveSessionStore.activePostIds()) {
            try {
                update(postId, session -> session.detectTimeouts(now));
            } catch (RuntimeException e) {
                log.warn(
                        "crew_live_timeout_failed postId={} error={}",
                        postId,
                        e.getClass().getSimpleName(),
                        e);
            }
        }
    }

    /** 그만둠 표시 → 다른 참가자에게 "그만둠"(FR-192). */
    public void markQuit(long memberId, long postId) {
        synchronized (lockOf(postId)) {
            Optional<CrewRunLiveSession> loaded = crewLiveSessionStore.load(postId);
            if (loaded.isEmpty()) {
                return;
            }
            CrewRunLiveSession session = loaded.get();
            session.markQuit(memberId);
            crewLiveSessionStore.save(session);
            crewRunMessenger.sendAll(
                    session.participants().stream().filter(id -> id != memberId).toList(),
                    session.relay());
        }
    }

    /** 세션이 끝났다(CrewRunService 종료 때). 잠금은 줄무늬라 버릴 것이 없다(L7). */
    void forget(long postId) {
        log.debug("crew_live_session_forgotten postId={}", postId);
    }

    private void update(long postId, Function<CrewRunLiveSession, List<CrewRunMessage>> change) {
        synchronized (lockOf(postId)) {
            Optional<CrewRunLiveSession> loaded = crewLiveSessionStore.load(postId);
            if (loaded.isEmpty()) {
                return;
            }
            CrewRunLiveSession session = loaded.get();
            List<CrewRunMessage> out = change.apply(session);
            if (out.isEmpty()) {
                return;
            }
            crewLiveSessionStore.save(session);
            out.forEach(m -> crewRunMessenger.sendAll(session.participants(), m));
        }
    }

    private Object lockOf(long postId) {
        return sessionLocks[Math.floorMod(Long.hashCode(postId), LOCK_STRIPES)];
    }

    private static Object[] newStripes() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
        return locks;
    }
}
