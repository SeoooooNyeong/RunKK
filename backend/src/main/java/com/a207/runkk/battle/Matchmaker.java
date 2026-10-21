package com.a207.runkk.battle;

import com.a207.runkk.battle.live.MatchFailed;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.MatchCondition;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 1초마다 대기자를 짝짓는다(설계 6.1.6, A-06, srs.md 6.3 4.2, 활동도 11.2, D-64, D-79). 짝은
 * MatchQueue.tryAssignPair로, 고스트는 MatchQueue.tryAssign으로 표를 먼저 ASSIGNED로 잡아(그동안 취소는
 * ALREADY_MATCHED, FR-085) 새 트랜잭션에서 대결을 만든다. 커밋되면 표를 지우고, 실패하면 release로 WAITING에 되돌려 다음 tick에 다시
 * 한다. 대기 시작 뒤 ghostAfter + 15초(기본 30초)를 넘도록 대결을 만들지 못하면 표를 지우고 MatchFailed로 알린다. 표 하나의 처리 실패가 같은
 * tick의 다른 표를 막지 않는다. 서버 인스턴스 하나에서만 돈다(5.4).
 */
@Service
public class Matchmaker {

    /** 고스트 전환(ghostAfter) 뒤 이만큼 더 대결을 만들지 못하면 포기하고 MatchFailed(기본 30초, D-64). */
    static final Duration GIVE_UP_MARGIN = Duration.ofSeconds(15);

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

    /** A-06. 오래 기다린 순으로 짝(pairUp), 짝이 없으면 대기 시간에 따라 고스트·포기(resolveUnpaired). */
    public void tick(LocalDateTime now) {
        MatchCondition cond = operationPolicy.matchCondition();
        List<MatchTicket> waiting = withoutActiveBattle(matchQueue.waitingOldestFirst());
        Set<Long> paired = new HashSet<>();
        for (MatchTicket ticket : waiting) {
            if (paired.contains(ticket.getMemberId())) {
                continue;
            }
            try {
                MatchTicket partner = partnerOf(ticket, waiting, paired, cond);
                if (pairUp(ticket, partner, now)) {
                    paired.add(ticket.getMemberId());
                    paired.add(partner.getMemberId());
                } else {
                    resolveUnpaired(ticket, cond, now);
                }
            } catch (RuntimeException e) {
                log.warn(
                        "match_ticket_failed memberId={} error={}",
                        ticket.getMemberId(),
                        e.getClass().getSimpleName(),
                        e);
            }
        }
    }

    /**
     * 이미 활성 대결이 있는 회원의 표를 대기열에서 뺀다(활성 대결 1개 불변식 결정). 대기 등록(MatchingService.join)과 기록 대결 생성이 겹치면 한
     * 회원이 표와 활성 대결을 함께 가질 수 있고, 그 표로 만든 대결은 활성 칸 고유 제약에 걸려 매 tick 실패하며 짝이 된 상대까지 막는다. 그런 표는
     * WAITING일 때 지우고 MatchFailed로 대기 화면을 닫는다.
     */
    private List<MatchTicket> withoutActiveBattle(List<MatchTicket> waiting) {
        List<MatchTicket> free = new ArrayList<>();
        for (MatchTicket ticket : waiting) {
            long memberId = ticket.getMemberId();
            if (!battleRepository.existsActive(memberId)) {
                free.add(ticket);
            } else if (matchQueue.removeWaiting(memberId)) {
                log.warn("match_ticket_dropped memberId={} reason=active_battle", memberId);
                notifyFailed(List.of(memberId));
            }
        }
        return free;
    }

    /** a 뒤에서 a와 짝지을 수 있고 아직 짝짓지 않은 첫 표(가장 오래 기다린 순). 없으면 null(D-79). */
    private static MatchTicket partnerOf(
            MatchTicket a, List<MatchTicket> waiting, Set<Long> paired, MatchCondition cond) {
        return waiting.stream()
                .dropWhile(candidate -> candidate != a)
                .skip(1)
                .filter(candidate -> !paired.contains(candidate.getMemberId()))
                .filter(candidate -> a.canPairWith(candidate, cond))
                .findFirst()
                .orElse(null);
    }

    /**
     * 후보와의 랜덤 대결 성사를 한 번 시도한다. 후보가 없거나 원자 배정이 실패하면 false. 배정했으면 새 트랜잭션에서 대결을 저장·알리고 커밋되면 두 표를 지운다.
     * 실패면 두 표를 WAITING으로 되돌리고 다음 tick에 다시 한다(D-64). 배정했으면 true.
     */
    private boolean pairUp(MatchTicket a, MatchTicket b, LocalDateTime now) {
        if (b == null || !matchQueue.tryAssignPair(a.getMemberId(), b.getMemberId())) {
            return false;
        }
        a.assign();
        b.assign();
        boolean created =
                transactions.once(
                        "random_match",
                        () -> {
                            Battle battle = battleRepository.save(Battle.random(a, b, now));
                            battleStartService.announceMatch(battle);
                        });
        if (created) {
            matchQueue.remove(a.getMemberId());
            matchQueue.remove(b.getMemberId());
        } else {
            matchQueue.release(a.getMemberId());
            matchQueue.release(b.getMemberId());
            log.warn("random_match_failed members={},{}", a.getMemberId(), b.getMemberId());
        }
        return true;
    }

    /**
     * 짝을 짓지 못한 표를 대기 시간으로 처리한다(D-64, D-79). ghostAfter + 15초를 넘었으면 WAITING일 때 지우고 MatchFailed,
     * ghostAfter를 넘었으면 tryAssign → 새 트랜잭션 GhostMatcher.match → 성공 remove, 실패 release. 정각은 넘은 것이
     * 아니다(TF-VB-15).
     */
    private void resolveUnpaired(MatchTicket a, MatchCondition cond, LocalDateTime now) {
        long memberId = a.getMemberId();
        if (a.isExpired(now, cond.ghostAfter().plus(GIVE_UP_MARGIN))) {
            if (matchQueue.removeWaiting(memberId)) {
                log.warn("match_given_up memberId={}", memberId);
                notifyFailed(List.of(memberId));
            }
        } else if (a.isExpired(now, cond.ghostAfter()) && matchQueue.tryAssign(memberId)) {
            boolean created = transactions.once("ghost_match", () -> ghostMatcher.match(a, now));
            if (created) {
                matchQueue.remove(memberId);
            } else {
                matchQueue.release(memberId);
                log.warn("ghost_match_failed memberId={}", memberId);
            }
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
