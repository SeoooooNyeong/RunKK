package com.a207.runkk.battle;

import com.a207.runkk.battle.live.MatchFailed;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.MatchCondition;
import com.a207.runkk.policy.OperationPolicy;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 1초마다 대기자를 짝짓는다(설계 6.1.6, A-06, srs.md 6.3 4.2, 활동도 11.2). 짝은 MatchQueue.tryAssignPair로 원자적으로 배정하고
 * (FR-082), 15초 넘게 기다린 대기자는 고스트 매칭으로 넘긴다. 짝·고스트 하나마다 새 트랜잭션이라 한 건의 실패가 다른 건을 막지 않는다. 만들지 못한 대결의
 * 대기자는 대기열에서 빠지므로 실시간 MatchFailed로 알린다. 서버 인스턴스 하나에서만 돈다(5.4).
 */
@Service
public class Matchmaker {

    private static final Logger log = LoggerFactory.getLogger(Matchmaker.class);

    private final MatchQueue matchQueue;
    private final BattleRepository battleRepository;
    private final BattleStartService battleStartService;
    private final GhostMatcher ghostMatcher;
    private final OperationPolicy operationPolicy;
    private final BattleTransactions transactions;
    private final BattleMessenger battleMessenger;

    public Matchmaker(
            MatchQueue matchQueue,
            BattleRepository battleRepository,
            BattleStartService battleStartService,
            GhostMatcher ghostMatcher,
            OperationPolicy operationPolicy,
            BattleTransactions transactions,
            BattleMessenger battleMessenger) {
        this.matchQueue = matchQueue;
        this.battleRepository = battleRepository;
        this.battleStartService = battleStartService;
        this.ghostMatcher = ghostMatcher;
        this.operationPolicy = operationPolicy;
        this.transactions = transactions;
        this.battleMessenger = battleMessenger;
    }

    /** A-06. 오래 기다린 순으로 짝 → Battle.random → announceMatch. 15초 초과 → GhostMatcher.match. */
    public void tick(LocalDateTime now) {
        MatchCondition cond = operationPolicy.matchCondition();
        List<MatchTicket> waiting = matchQueue.waitingOldestFirst();
        Set<Long> paired = new HashSet<>();
        for (int index = 0; index < waiting.size(); index++) {
            MatchTicket ticket = waiting.get(index);
            if (paired.contains(ticket.getMemberId())) {
                continue;
            }
            Optional<MatchTicket> partner = partnerOf(index, waiting, paired, cond);
            if (partner.isPresent() && assignPair(ticket, partner.get())) {
                pair(ticket, partner.get(), now);
                paired.add(ticket.getMemberId());
                paired.add(partner.get().getMemberId());
            } else if (ticket.isExpired(now, cond.ghostAfter())
                    && matchQueue.remove(ticket.getMemberId())) {
                boolean created =
                        transactions.once("ghost_match", () -> ghostMatcher.match(ticket, now));
                if (!created) {
                    notifyFailed(List.of(ticket.getMemberId()));
                }
            }
        }
    }

    private boolean assignPair(MatchTicket ticket, MatchTicket partner) {
        return matchQueue.tryAssignPair(ticket.getMemberId(), partner.getMemberId());
    }

    /** index 뒤의 첫 후보(가장 오래 기다린 순). */
    private static Optional<MatchTicket> partnerOf(
            int index, List<MatchTicket> waiting, Set<Long> paired, MatchCondition cond) {
        MatchTicket ticket = waiting.get(index);
        return waiting.stream()
                .skip(index + 1L)
                .filter(candidate -> !paired.contains(candidate.getMemberId()))
                .filter(candidate -> ticket.canPairWith(candidate, cond))
                .findFirst();
    }

    /** 배정된 두 표로 대결을 저장하고 알린다. 실패해도 두 표를 지워 다시 요청할 수 있게 한다. */
    private void pair(MatchTicket first, MatchTicket second, LocalDateTime now) {
        first.assign();
        second.assign();
        boolean created =
                transactions.once(
                        "random_match",
                        () -> {
                            Battle battle =
                                    battleRepository.save(Battle.random(first, second, now));
                            battleStartService.announceMatch(battle);
                        });
        matchQueue.remove(first.getMemberId());
        matchQueue.remove(second.getMemberId());
        if (!created) {
            log.warn("random_match_dropped tickets={}", LiveBattleSession.RUNNERS);
            notifyFailed(List.of(first.getMemberId(), second.getMemberId()));
        }
    }

    /** 대결을 만들지 못해 대기가 풀렸음을 알린다(앱은 대기 화면을 닫는다). 알림 실패는 로그만 남긴다. */
    private void notifyFailed(List<Long> memberIds) {
        try {
            battleMessenger.sendAll(
                    memberIds,
                    new MatchFailed(
                            ReasonCode.MATCH_FAILED.name(), ReasonCode.MATCH_FAILED.message()));
        } catch (RuntimeException e) {
            log.warn("match_failed_notice_failed error={}", e.getClass().getSimpleName());
        }
    }
}
