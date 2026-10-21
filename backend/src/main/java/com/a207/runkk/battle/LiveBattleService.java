package com.a207.runkk.battle;

import com.a207.runkk.battle.live.CountdownStarted;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 진행 중인 랜덤 대결의 진행값 중계(설계 6.1.6, SQ-04, srs.md 6.3 4.4). 세션은 대결이 시작될 때 열리고 대결이 진행 중인 동안만 있으므로 세션이
 * 없으면 진행 중이 아니라고 보고 무시한다. 1초 진행값은 DB에 쓰지 않고 도착·끊김·복구만 Battle에 남긴다(6.5). 한 대결의 진행값은 대결 ID 단위로 하나씩
 * 처리한다(5.3 세션 잠금 키, 인스턴스 하나).
 */
@Service
public class LiveBattleService {

    private static final Logger log = LoggerFactory.getLogger(LiveBattleService.class);
    private static final String OPPONENT_ARRIVED_TEXT = "상대가 먼저 도착했어요. 10초 안에 들어오세요!";

    private final LiveSessionStore liveSessionStore;
    private final BattleRepository battleRepository;
    private final BattleMessenger battleMessenger;
    private final NotificationService notificationService;
    private final BattleTransactions transactions;

    /**
     * 세션 잠금 줄무늬(striped lock). 세션 ID를 고정된 잠금 64개 중 하나에 대응시켜, 잠금 객체를 지우고 다시 만드는 사이에 같은 세션에 잠금이 둘 생기는
     * 경합이 없다(코드 리뷰 2차 L7). 다른 세션이 같은 잠금을 나눠 쓰면 잠깐 기다릴 뿐 정합성은 같다.
     */
    private static final int LOCK_STRIPES = 64;

    private final Object[] sessionLocks = newStripes();

    public LiveBattleService(
            LiveSessionStore liveSessionStore,
            BattleRepository battleRepository,
            BattleMessenger battleMessenger,
            NotificationService notificationService,
            BattleTransactions transactions) {
        this.liveSessionStore = liveSessionStore;
        this.battleRepository = battleRepository;
        this.battleMessenger = battleMessenger;
        this.notificationService = notificationService;
        this.transactions = transactions;
    }

    /**
     * SQ-04. battleId는 realtime이 수신 메시지의 채널 ID로 넘긴다(D-48). 진행 중 세션이 없거나 회원이 참가자가 아니면 무시한다. 도착·복구를
     * 대결에 먼저 남기고 나서 세션을 저장하므로, 대결 반영이 실패하면 다음 진행값에서 다시 판정한다.
     */
    public void onProgress(long memberId, long battleId, ProgressReport r, LocalDateTime now) {
        if (liveSessionStore.load(battleId).isEmpty()) {
            return; // 없는 채널 ID로 잠금 객체가 쌓이지 않게 세션부터 확인한다
        }
        synchronized (lockOf(battleId)) {
            Optional<LiveBattleSession> loaded = liveSessionStore.load(battleId);
            if (loaded.isEmpty()) {
                return;
            }
            LiveBattleSession session = loaded.get();
            List<BattleDelivery> out = session.onProgress(memberId, r, now);
            if (out.isEmpty()) {
                return;
            }
            session.reconnected().ifPresent(id -> reconnect(battleId, id, now));
            session.newArrival().ifPresent(id -> arrive(session, id, now));
            liveSessionStore.save(session);
            out.forEach(
                    delivery -> battleMessenger.send(delivery.recipientId(), delivery.message()));
        }
    }

    private void reconnect(long battleId, long memberId, LocalDateTime now) {
        transactions.retryingRun(
                () -> update(battleId, battle -> battle.markReconnected(memberId, now)));
    }

    /** 첫 도착이면 커밋 뒤 모두에게 CountdownStarted, 아직 달리는 쪽에 OPPONENT_ARRIVED 알림(FR-102, FR-107). */
    private void arrive(LiveBattleSession session, long memberId, LocalDateTime now) {
        transactions.retryingRun(
                () -> {
                    Battle battle = load(session.battleId());
                    boolean first = battle.recordArrival(Side.member(memberId), now);
                    battleRepository.save(battle);
                    if (first) {
                        announceCountdown(battle, session.nameOf(memberId), memberId);
                    }
                });
    }

    private void announceCountdown(Battle battle, Optional<DisplayName> name, long memberId) {
        CountdownStarted countdown =
                new CountdownStarted(
                        battle.getId(),
                        name.orElse(DisplayName.WITHDRAWN),
                        battle.getCountdownEndsAt().orElseThrow());
        List<Long> recipients = battle.memberIds();
        AfterCommit.run(() -> battleMessenger.sendAll(recipients, countdown));
        battle.rivalOf(memberId)
                .ifPresent(rival -> notifyArrival(rival.getMemberId(), battle.getId()));
    }

    private void notifyArrival(long recipientId, long battleId) {
        notificationService.notify(
                new NotificationRequest(
                        recipientId,
                        NotificationType.OPPONENT_ARRIVED,
                        OPPONENT_ARRIVED_TEXT,
                        new NavigationTarget(Screen.BATTLE_RESULT, String.valueOf(battleId))));
    }

    /**
     * 진행 중 세션마다 3초 끊김을 판정해 상대에게 알리고 Battle.markDisconnected. 대결이 끝났으면 세션을 지운다. 한 대결의 실패가 다른 대결의 판정을
     * 막지 않게 대결마다 따로 감싼다.
     */
    public void detectTimeouts(LocalDateTime now) {
        for (long battleId : liveSessionStore.activeBattleIds()) {
            try {
                synchronized (lockOf(battleId)) {
                    liveSessionStore.load(battleId).ifPresent(session -> timeout(session, now));
                }
            } catch (RuntimeException e) {
                log.warn(
                        "live_timeout_failed battleId={} error={}",
                        battleId,
                        e.getClass().getSimpleName(),
                        e);
            }
        }
    }

    private void timeout(LiveBattleSession session, LocalDateTime now) {
        List<BattleDelivery> out = session.detectTimeouts(now);
        if (session.timedOut().isEmpty()) {
            return;
        }
        boolean running =
                transactions.retrying(
                        () -> {
                            Battle battle = load(session.battleId());
                            if (!battle.getStatus().isRunning()) {
                                return false;
                            }
                            session.timedOut().forEach(id -> battle.markDisconnected(id, now));
                            battleRepository.save(battle);
                            return true;
                        });
        if (!running) {
            liveSessionStore.delete(session.battleId());
            return;
        }
        liveSessionStore.save(session);
        out.forEach(delivery -> battleMessenger.send(delivery.recipientId(), delivery.message()));
    }

    private void update(long battleId, Consumer<Battle> change) {
        Battle battle = load(battleId);
        change.accept(battle);
        battleRepository.save(battle);
    }

    private Battle load(long battleId) {
        return battleRepository
                .findById(battleId)
                .orElseThrow(() -> new IllegalStateException("세션의 대결이 없다: " + battleId));
    }

    private Object lockOf(long battleId) {
        return sessionLocks[Math.floorMod(Long.hashCode(battleId), LOCK_STRIPES)];
    }

    private static Object[] newStripes() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
        return locks;
    }
}
