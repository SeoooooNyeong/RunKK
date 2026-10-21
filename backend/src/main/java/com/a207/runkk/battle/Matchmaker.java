package com.a207.runkk.battle;

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
 * (FR-082), 15초 넘게 기다린 대기자는 고스트 매칭으로 넘긴다. 짝·고스트 하나마다 새 트랜잭션이라 한 건의 실패가 다른 건을 막지 않는다. 서버 인스턴스 하나에서만
 * 돈다(5.4).
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

    public Matchmaker(
            MatchQueue matchQueue,
            BattleRepository battleRepository,
            BattleStartService battleStartService,
            GhostMatcher ghostMatcher,
            OperationPolicy operationPolicy,
            BattleTransactions transactions) {
        this.matchQueue = matchQueue;
        this.battleRepository = battleRepository;
        this.battleStartService = battleStartService;
        this.ghostMatcher = ghostMatcher;
        this.operationPolicy = operationPolicy;
        this.transactions = transactions;
    }

    /** A-06. 오래 기다린 순으로 짝 → Battle.random → announceMatch. 15초 초과 → GhostMatcher.match. */
    public void tick(LocalDateTime now) {
        MatchCondition cond = operationPolicy.matchCondition();
        List<MatchTicket> waiting = matchQueue.waitingOldestFirst();
        Set<Long> paired = new HashSet<>();
        for (MatchTicket a : waiting) {
            if (paired.contains(a.getMemberId())) {
                continue;
            }
            Optional<MatchTicket> b = partnerOf(a, waiting, paired, cond);
            if (b.isPresent() && matchQueue.tryAssignPair(a.getMemberId(), b.get().getMemberId())) {
                pair(a, b.get(), now);
                paired.add(a.getMemberId());
                paired.add(b.get().getMemberId());
            } else if (a.isExpired(now, cond.ghostAfter()) && matchQueue.remove(a.getMemberId())) {
                transactions.once("ghost_match", () -> ghostMatcher.match(a, now));
            }
        }
    }

    /** a 뒤의 첫 후보(가장 오래 기다린 순). */
    private static Optional<MatchTicket> partnerOf(
            MatchTicket a, List<MatchTicket> waiting, Set<Long> paired, MatchCondition cond) {
        return waiting.stream()
                .skip(waiting.indexOf(a) + 1L)
                .filter(b -> !paired.contains(b.getMemberId()))
                .filter(b -> a.canPairWith(b, cond))
                .findFirst();
    }

    /** 배정된 두 표로 대결을 저장하고 알린다. 실패하면 두 표를 지워 다시 요청할 수 있게 한다. */
    private void pair(MatchTicket a, MatchTicket b, LocalDateTime now) {
        a.assign();
        b.assign();
        boolean created =
                transactions.once(
                        "random_match",
                        () -> {
                            Battle battle = battleRepository.save(Battle.random(a, b, now));
                            battleStartService.announceMatch(battle);
                        });
        matchQueue.remove(a.getMemberId());
        matchQueue.remove(b.getMemberId());
        if (!created) {
            log.warn("random_match_dropped tickets=2");
        }
    }
}
