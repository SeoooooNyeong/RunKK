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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * 진행 중인 랜덤 대결의 진행값 중계(설계 6.1.6, SQ-04, srs.md 6.3 4.4). 세션은 대결이 시작될 때 열리고 대결이 진행 중인 동안만 있으므로 세션이
 * 없으면 진행 중이 아니라고 보고 무시한다. 1초 진행값은 DB에 쓰지 않고 도착·끊김·복구만 Battle에 남긴다(6.5). 한 대결의 진행값은 대결 ID 단위로 하나씩
 * 처리한다(5.3 세션 잠금 키, 인스턴스 하나).
 */
@Service
public class LiveBattleService {

    private static final String OPPONENT_ARRIVED_TEXT = "상대가 먼저 도착했어요. 10초 안에 들어오세요!";

    private final LiveSessionStore liveSessionStore;
    private final BattleRepository battleRepository;
    private final BattleMessenger battleMessenger;
    private final NotificationService notificationService;
    private final BattleTransactions transactions;
    private final Map<Long, Object> sessionLocks = new ConcurrentHashMap<>();

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

    /** SQ-04. battleId는 realtime이 수신 메시지의 채널 ID로 넘긴다(D-48). 진행 중 세션이 없거나 회원이 참가자가 아니면 무시한다. */
    public void onProgress(long memberId, long battleId, ProgressReport r, LocalDateTime now) {
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
            liveSessionStore.save(session);
            session.reconnected()
                    .ifPresent(
                            id ->
                                    transactions.retryingRun(
                                            () ->
                                                    update(
                                                            battleId,
                                                            b -> b.markReconnected(id, now))));
            session.newArrival().ifPresent(id -> arrive(session, id, now));
            out.forEach(d -> battleMessenger.send(d.recipientId(), d.message()));
        }
    }

    /** 첫 도착이면 모두에게 CountdownStarted, 아직 달리는 쪽에 OPPONENT_ARRIVED 알림(FR-102, FR-107). */
    private void arrive(LiveBattleSession session, long memberId, LocalDateTime now) {
        transactions.retryingRun(
                () -> {
                    Battle battle = load(session.battleId());
                    if (!battle.recordArrival(Side.member(memberId), now)) {
                        battleRepository.save(battle);
                        return;
                    }
                    battleRepository.save(battle);
                    DisplayName name = session.nameOf(memberId).orElse(DisplayName.WITHDRAWN);
                    CountdownStarted countdown =
                            new CountdownStarted(
                                    battle.getId(),
                                    name,
                                    battle.getCountdownEndsAt().orElseThrow());
                    battleMessenger.sendAll(battle.memberIds(), countdown);
                    battle.rivalOf(memberId)
                            .ifPresent(rival -> notifyArrival(rival.getMemberId(), battle.getId()));
                });
    }

    private void notifyArrival(long recipientId, long battleId) {
        notificationService.notify(
                new NotificationRequest(
                        recipientId,
                        NotificationType.OPPONENT_ARRIVED,
                        OPPONENT_ARRIVED_TEXT,
                        new NavigationTarget(Screen.BATTLE_RESULT, String.valueOf(battleId))));
    }

    /** 진행 중 세션마다 3초 끊김을 판정해 상대에게 알리고 Battle.markDisconnected. 대결이 끝났으면 세션을 지운다. */
    public void detectTimeouts(LocalDateTime now) {
        for (long battleId : liveSessionStore.activeBattleIds()) {
            synchronized (lockOf(battleId)) {
                liveSessionStore.load(battleId).ifPresent(session -> timeout(session, now));
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
            sessionLocks.remove(session.battleId());
            return;
        }
        liveSessionStore.save(session);
        out.forEach(d -> battleMessenger.send(d.recipientId(), d.message()));
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
        return sessionLocks.computeIfAbsent(battleId, id -> new Object());
    }
}
