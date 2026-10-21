package com.a207.runkk.battle;

import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.battle.live.CountdownStarted;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 대결 시계(설계 6.1.6, A-11). 1초마다 (1) 출발 (2) 기록 상대 도착 (3) 카운트다운 종료·최대 시간 초과 (4) 보류 24시간 만료를 처리한다. 대결마다
 * 새 트랜잭션이라 한 대결의 실패·낙관적 잠금 충돌이 다른 대결을 막지 않고, 실패한 대결은 다음 tick에 다시 본다.
 */
@Service
public class BattleClockService {

    private static final Logger log = LoggerFactory.getLogger(BattleClockService.class);
    private static final String OPPONENT_ARRIVED_TEXT = "상대 기록이 먼저 도착했어요. 10초 안에 들어오세요!";

    private final BattleRepository battleRepository;
    private final BattleStartService battleStartService;
    private final LiveSessionStore liveSessionStore;
    private final BattleMessenger battleMessenger;
    private final NotificationService notificationService;
    private final DomainEventPublisher eventPublisher;
    private final BattleTransactions transactions;

    public BattleClockService(
            BattleRepository battleRepository,
            BattleStartService battleStartService,
            LiveSessionStore liveSessionStore,
            BattleMessenger battleMessenger,
            NotificationService notificationService,
            DomainEventPublisher eventPublisher,
            BattleTransactions transactions) {
        this.battleRepository = battleRepository;
        this.battleStartService = battleStartService;
        this.liveSessionStore = liveSessionStore;
        this.battleMessenger = battleMessenger;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.transactions = transactions;
    }

    /** A-11 네 단계. */
    public void tick(LocalDateTime now) {
        battleStartService.startDue(now);
        for (Battle due : battleRepository.findOpponentArrivalDue(now)) {
            transactions.once("opponent_arrival", () -> arriveOpponent(due.getId(), now));
        }
        for (Battle due : battleRepository.findCountdownDue(now)) {
            transactions.once("countdown_close", () -> close(due.getId(), now));
        }
        for (Battle due : battleRepository.findPendingExpired(now.minus(Battle.PENDING_LIMIT))) {
            transactions.once("pending_expire", () -> expire(due.getId(), now));
        }
    }

    private void arriveOpponent(long battleId, LocalDateTime now) {
        Battle battle = load(battleId);
        LocalDateTime arrivesAt = battle.getOpponentArrivesAt().orElse(now);
        if (!battle.isOpponentArrivalDue(now)) {
            return;
        }
        boolean first = battle.recordArrival(Side.OPPONENT, arrivesAt);
        battleRepository.save(battle);
        if (!first) {
            return;
        }
        OpponentRef ref = battle.getOpponent().orElseThrow();
        CountdownStarted countdown =
                new CountdownStarted(
                        battleId, ref.displayName(), battle.getCountdownEndsAt().orElseThrow());
        List<Long> recipients = battle.memberIds();
        AfterCommit.run(() -> battleMessenger.sendAll(recipients, countdown));
        for (long memberId : recipients) {
            notificationService.notify(
                    new NotificationRequest(
                            memberId,
                            NotificationType.OPPONENT_ARRIVED,
                            OPPONENT_ARRIVED_TEXT,
                            new NavigationTarget(Screen.BATTLE_RESULT, String.valueOf(battleId))));
        }
    }

    /** 추월 횟수 저장 → RESULT_PENDING → BattleEnded(RESULT_PENDING) → 세션 삭제 → 기록이 다 왔으면 바로 판정. */
    private void close(long battleId, LocalDateTime now) {
        Battle battle = load(battleId);
        if (!battle.isCloseDue(now)) {
            return;
        }
        liveSessionStore
                .load(battleId)
                .ifPresent(s -> s.overtakeCounts().forEach(battle::recordOvertakes));
        battle.closeCountdown(now);
        Optional<Judgement> judgement = battle.judge(now);
        battleRepository.save(battle);
        BattleEnded.Status status =
                judgement.isPresent()
                        ? BattleEnded.Status.FINISHED
                        : BattleEnded.Status.RESULT_PENDING;
        List<Long> recipients = battle.memberIds();
        AfterCommit.run(
                () -> {
                    battleMessenger.sendAll(recipients, new BattleEnded(battleId, status));
                    liveSessionStore.delete(battleId);
                });
        judgement.ifPresent(j -> publish(battle, j));
    }

    private void expire(long battleId, LocalDateTime now) {
        Battle battle = load(battleId);
        Optional<Judgement> judgement = battle.expirePending(now);
        if (judgement.isEmpty()) {
            return;
        }
        battleRepository.save(battle);
        publish(battle, judgement.get());
    }

    private void publish(Battle battle, Judgement judgement) {
        eventPublisher.publish(
                new BattleJudged(battle.getId(), battle.getType(), judgement, judgement.at()));
    }

    /**
     * 서버 시작 때 한 번(5.5, D-55). 남은 세션 중 IN_PROGRESS·COUNTDOWN 대결만 남기고 나머지를 지운다. 이어 간 대결 수. 시작 사이에 지난
     * 최대 시간·카운트다운은 첫 tick이 처리한다.
     */
    public int resumeLiveSessions(LocalDateTime now) {
        int resumed = 0;
        List<Long> sessions = liveSessionStore.activeBattleIds();
        for (long battleId : sessions) {
            boolean running =
                    battleRepository
                            .findById(battleId)
                            .map(b -> b.getStatus().isRunning())
                            .orElse(false);
            if (running) {
                resumed++;
            } else {
                liveSessionStore.delete(battleId);
            }
        }
        log.info(
                "live_sessions_resumed resumed={} dropped={} at={}",
                resumed,
                sessions.size() - resumed,
                now);
        return resumed;
    }

    private Battle load(long battleId) {
        return battleRepository
                .findById(battleId)
                .orElseThrow(() -> new IllegalStateException("대결이 없다: " + battleId));
    }
}
