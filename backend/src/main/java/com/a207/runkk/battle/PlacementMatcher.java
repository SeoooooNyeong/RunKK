package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.PlacementBasis;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.policy.OperationPolicy;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 중 회원의 배치고사 대결을 대기열 없이 만든다(05-21 2.2, D-477~D-480, D-515, D-518, SQ-49). 같은 회원의 동시 요청은 진행 행 비관
 * 잠금이, 셀프·셀럽 시작·친구 초대 수락과의 경합은 활성 칸 고유 제약이 막는다.
 */
@Service
public class PlacementMatcher {

    private static final Logger log = LoggerFactory.getLogger(PlacementMatcher.class);

    private final PlacementService placementService;
    private final PlacementOpponentFactory factory;
    private final OperationPolicy policy;
    private final BattleRepository battleRepository;
    private final BattleStartService battleStartService;
    private final RunnerAvailability runnerAvailability;
    private final RandomGenerator random;

    public PlacementMatcher(
            PlacementService placementService,
            PlacementOpponentFactory factory,
            OperationPolicy policy,
            BattleRepository battleRepository,
            BattleStartService battleStartService,
            RunnerAvailability runnerAvailability,
            RandomGenerator battleRandom) {
        this.placementService = placementService;
        this.factory = factory;
        this.policy = policy;
        this.battleRepository = battleRepository;
        this.battleStartService = battleStartService;
        this.runnerAvailability = runnerAvailability;
        this.random = battleRandom;
    }

    /**
     * 진행 행을 잠근 뒤 회원이 이미 랭크 티어면 비어 있다(부르는 쪽이 대기열 흐름, D-518). 바쁘면 ALREADY_IN_BATTLE. 가상 상대로 GHOST 대결을
     * 만들어 판 번호를 붙이고 바로 넣는다(활성 칸 고유 제약 위반이면 ALREADY_IN_BATTLE, D-515). 그 뒤 MatchFound를 알리고 13초 뒤 출발을
     * 예약한다. 상대를 못 만들면 MATCH_FAILED.
     */
    @Transactional
    public Optional<Battle> start(long memberId, StandardDistance distance, LocalDateTime now) {
        Optional<PlacementBasis> locked = placementService.lockForMatch(memberId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        PlacementBasis basis = locked.get();
        if (isBusy(memberId, now)) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
        PlacementOpponent opponent =
                factory.create(basis, distance, policy.tierBoundary(), policy.virtualRecordRule());
        Battle created =
                Battle.againstRecord(
                        RecordBattleSpec.of(
                                BattleType.GHOST, memberId, distance.distance(), opponent),
                        now);
        created.markPlacement(basis.nextRound());
        created.showOpponentAs(OpponentGenders.random(random));
        Battle battle = saveOrConflict(memberId, created);
        battleStartService.announceMatch(battle);
        log.info(
                "placement_battle_created memberId={} battleId={} round={}",
                memberId,
                battle.getId(),
                basis.nextRound());
        return Optional.of(battle);
    }

    /** 대기열 저장소 장애(Redis)는 MatchingService와 같이 QUEUE_UNAVAILABLE로 바꾼다(05-03 P2). */
    private boolean isBusy(long memberId, LocalDateTime now) {
        try {
            return runnerAvailability.isBusy(memberId, now);
        } catch (DataAccessException e) {
            log.warn("match_queue_unavailable error={}", e.getClass().getSimpleName());
            throw new BusinessRuleException(ReasonCode.QUEUE_UNAVAILABLE);
        }
    }

    private Battle saveOrConflict(long memberId, Battle battle) {
        try {
            return battleRepository.saveImmediately(battle);
        } catch (DataIntegrityViolationException e) {
            if (!UniqueViolations.isUniqueViolation(e)) {
                throw e;
            }
            log.warn(
                    "record_battle_create_conflict memberId={} error={}",
                    memberId,
                    e.getClass().getSimpleName(),
                    e);
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
    }
}
