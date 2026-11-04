package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.PlacementCount;
import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.member.PlacementRoundInput;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.member.PlacementVerdict;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunSummary;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 배치고사 판을 센다(05-21 2.2, D-481·D-511·D-512·D-516). 판정 사건(BattleJudged)과 기록 연결
 * 사건(PlacementRecordAttached)을 커밋 뒤 받아 새 트랜잭션에서 count(battleId)를 부른다. 셀 조건은
 * PlacementRoundCheck.countable() 하나다. 일시 오류는 3번까지 다시 하고(TierUpdateListener와 같음), 그래도 실패하면
 * placement_count_failed 운영 로그를 남기고 복구 작업(D-490)이 다시 센다.
 */
@Component
public class PlacementRoundListener {

    private static final Logger log = LoggerFactory.getLogger(PlacementRoundListener.class);
    private static final int MAX_ATTEMPTS = 3;

    private final BattleRepository battleRepository;
    private final RunQueryService runQueryService;
    private final PlacementService placementService;
    private final TransactionTemplate newTransaction;

    public PlacementRoundListener(
            BattleRepository battleRepository,
            RunQueryService runQueryService,
            PlacementService placementService,
            PlatformTransactionManager transactionManager) {
        this.battleRepository = battleRepository;
        this.runQueryService = runQueryService;
        this.placementService = placementService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 판정(포기 포함). 고스트만 배치고사일 수 있다. 정정이면 이미 센 판의 승패만 고친다(셈 여부는 바꾸지 않음). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBattleJudged(BattleJudged e) {
        if (e.type() != BattleType.GHOST) {
            return;
        }
        if (e.judgement().corrected()) {
            correct(e);
        }
        count(e.battleId());
    }

    /** 판정 없이 기록만 붙은 배치고사 대결(D-512). 판정 전이면 countable이 거짓이라 아무것도 하지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecordAttached(PlacementRecordAttached e) {
        count(e.battleId());
    }

    /**
     * 대결을 읽어 배치고사 판이고 셀 수 있으면 PlacementService.countRound를 부른다. 셌거나 이미 센 판이면 참. 복구 작업(D-490)도 부른다.
     * 예외를 밖으로 던지지 않는다.
     */
    public boolean count(long battleId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return Boolean.TRUE.equals(newTransaction.execute(status -> countOnce(battleId)));
            } catch (TransientDataAccessException conflict) {
                log.info("placement_count_retry battleId={} attempt={}", battleId, attempt);
            } catch (DataIntegrityViolationException e) {
                if (UniqueViolations.isUniqueViolation(e)) {
                    // uq_placement_round_battle: 다른 계기가 먼저 셌다. 이미 센 것으로 보고 다시 하지 않는다(D-516)
                    log.info("placement_count_duplicate battleId={}", battleId);
                    return true;
                }
                return failed(battleId, e);
            } catch (RuntimeException e) {
                return failed(battleId, e);
            }
        }
        log.error("placement_count_failed battleId={} error=transient", battleId);
        return false;
    }

    private boolean countOnce(long battleId) {
        Optional<Battle> found = battleRepository.findById(battleId);
        if (found.isEmpty() || found.get().placementRound().isEmpty()) {
            return false;
        }
        Battle battle = found.get();
        long memberId = battle.memberIds().get(0);
        BattleParticipant me = battle.participant(memberId).orElseThrow();
        Optional<RunSummary> summary = me.getRecordId().flatMap(this::summaryOf);
        if (me.getRecordId().isPresent() && summary.isEmpty()) {
            // 기록 ID는 붙었는데 요약을 읽지 못한다. 판정 뒤 유예가 지나면 결과는 NOT_COUNTED로 닫힌다(TF-V18-01)
            log.warn(
                    "placement_record_summary_missing battleId={} memberId={}", battleId, memberId);
        }
        PlacementRoundCheck check =
                PlacementRoundCheck.of(
                        battle, memberId, summary.map(RunSummary::valid).orElse(null));
        if (!check.countable()) {
            return false;
        }
        OptionalInt ordinal = me.getValidRunOrdinal();
        if (ordinal.isEmpty() || summary.get().score() == null) {
            log.warn("placement_count_missing_ordinal battleId={} memberId={}", battleId, memberId);
            return false;
        }
        RunSummary run = summary.get();
        PlacementCount result =
                placementService.countRound(
                        memberId,
                        new PlacementRoundInput(
                                battleId,
                                run.score(),
                                battle.standardDistance().orElseThrow(),
                                run.distance(),
                                run.elapsed(),
                                run.averagePace(),
                                verdictOf(battle, memberId),
                                ordinal.getAsInt(),
                                battle.getJudgement().map(Judgement::at).orElseThrow()));
        // 셌거나 이미 센 판이면 참. 진행 없음·탈퇴·랭크·완료로 버려진 판은 거짓(리뷰 M3)
        return !(result instanceof PlacementCount.Ignored ignored)
                || PlacementProgress.DUPLICATE.equals(ignored.reason());
    }

    private Optional<RunSummary> summaryOf(String recordId) {
        try {
            return Optional.of(runQueryService.summary(recordId));
        } catch (BusinessRuleException e) {
            if (e.reason() != ReasonCode.RECORD_NOT_FOUND) {
                throw e;
            }
            return Optional.empty();
        }
    }

    /** 정정은 일시 오류를 count와 같이 3번까지 다시 한다(리뷰 M1). 그래도 실패하면 운영 로그(placement_correct_failed). */
    private void correct(BattleJudged e) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                correctOnce(e);
                return;
            } catch (TransientDataAccessException conflict) {
                log.info("placement_correct_retry battleId={} attempt={}", e.battleId(), attempt);
            } catch (RuntimeException failure) {
                log.error(
                        "placement_correct_failed battleId={} error={}",
                        e.battleId(),
                        failure.getClass().getSimpleName(),
                        failure);
                return;
            }
        }
        log.error("placement_correct_failed battleId={} error=transient", e.battleId());
    }

    private void correctOnce(BattleJudged e) {
        newTransaction.executeWithoutResult(
                status ->
                        battleRepository
                                .findById(e.battleId())
                                .filter(b -> b.placementRound().isPresent())
                                .ifPresent(
                                        b -> {
                                            long memberId = b.memberIds().get(0);
                                            placementService.correctVerdict(
                                                    memberId, e.battleId(), verdictOf(b, memberId));
                                        }));
    }

    private static PlacementVerdict verdictOf(Battle battle, long memberId) {
        Verdict verdict =
                battle.getJudgement()
                        .flatMap(j -> j.verdictOf(Side.member(memberId)))
                        .orElseThrow(() -> new IllegalStateException("판정 없는 판"));
        return PlacementVerdict.valueOf(verdict.name());
    }

    private static boolean failed(long battleId, RuntimeException e) {
        log.error(
                "placement_count_failed battleId={} error={}",
                battleId,
                e.getClass().getSimpleName(),
                e);
        return false;
    }
}
