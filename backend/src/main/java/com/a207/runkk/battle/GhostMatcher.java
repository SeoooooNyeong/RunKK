package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.GhostOwner;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.OwnedRun;
import com.a207.runkk.running.RunQueryService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 15초 넘게 상대를 못 찾은 대기자에게 고스트 대결을 만든다(설계 6.1.6, A-07, srs.md 6.3 4.6, SQ-06). 같은 티어 → 인접 → 전체 순으로 다른
 * 러너의 전체공개 유효 기록(경로 남음)을 찾고, 세 단계 모두 없으면 같은 티어 가상 기록 5개 중 하나를 쓴다(Q-61). 고스트 후보는
 * MemberQueryService·RunQueryService 조합으로 구한다(D-32, D-51).
 */
@Service
public class GhostMatcher {

    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final VirtualRecordRepository virtualRecordRepository;
    private final BattleRepository battleRepository;
    private final BattleStartService battleStartService;
    private final RandomGenerator random;

    public GhostMatcher(
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            VirtualRecordRepository virtualRecordRepository,
            BattleRepository battleRepository,
            BattleStartService battleStartService,
            RandomGenerator battleRandom) {
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.virtualRecordRepository = virtualRecordRepository;
        this.battleRepository = battleRepository;
        this.battleStartService = battleStartService;
        this.random = battleRandom;
    }

    /** A-07. 고른 상대로 GHOST 대결을 저장하고 매칭 성사를 알린다. */
    @Transactional
    public Battle match(MatchTicket ticket, LocalDateTime now) {
        Distance distance = ticket.getDistance().distance();
        OpponentRecord opponent =
                findRunner(ticket).orElseGet(() -> virtualOf(ticket.getTier(), ticket));
        RecordBattleSpec spec =
                RecordBattleSpec.of(BattleType.GHOST, ticket.getMemberId(), distance, opponent);
        Battle battle = battleRepository.save(Battle.againstRecord(spec, now));
        battleStartService.announceMatch(battle);
        return battle;
    }

    private Optional<OpponentRecord> findRunner(MatchTicket ticket) {
        for (Set<Tier> tiers : ticket.getTier().searchLadder()) {
            Map<Long, GhostOwner> owners =
                    memberQueryService.ghostEligibleOwners(tiers, ticket.getMemberId());
            if (owners.isEmpty()) {
                continue;
            }
            List<OwnedRun> candidates =
                    runQueryService.validRunsReaching(ticket.getDistance(), owners.keySet());
            if (!candidates.isEmpty()) {
                OwnedRun chosen = candidates.get(random.nextInt(candidates.size()));
                return Optional.of(opponentOf(chosen, owners.get(chosen.memberId()), ticket));
            }
        }
        return Optional.empty();
    }

    private OpponentRecord opponentOf(OwnedRun run, GhostOwner owner, MatchTicket ticket) {
        return new RunRecordOpponent(
                run.recordId(),
                runQueryService.comparisonOf(run.recordId()),
                runQueryService.curve(run.recordId(), ticket.getDistance().distance()),
                new RunRecordOpponent.Owner(owner.displayName(), owner.tier()));
    }

    /** 가상 기록은 늘 있다(Q-61, 서버 시작 때 100개). */
    private OpponentRecord virtualOf(Tier tier, MatchTicket ticket) {
        List<VirtualRecord> records =
                virtualRecordRepository.findByTierAndDistance(tier, ticket.getDistance());
        if (records.isEmpty()) {
            throw new IllegalStateException("가상 기록이 없다: " + tier + " " + ticket.getDistance());
        }
        return records.get(random.nextInt(records.size()));
    }
}
