package com.a207.runkk.running;

import com.a207.runkk.member.RunScoreInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 유효 러닝 저장 사건을 member의 티어 갱신 호출로 넘긴다(설계 6.1.3, D-03 예외). 저장 커밋 뒤 동기로 새 트랜잭션에서 TierApplier로 반영하고 그
 * 기록의 반영 표시(tier_applied)를 같은 트랜잭션에서 세운다(총괄 결정 7-3). 같은 회원 행을 함께 고쳐 낙관적 잠금 충돌·잠금 대기 같은 일시 오류면 3번까지
 * 다시 한다(코드 리뷰 2차 L16). 그래도 실패하면 운영 로그를 남기고 표시가 서지 않은 기록을 TierReconcileService가 다음 0시 10분에
 * 채운다(D-108).
 */
@Component
public class TierUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(TierUpdateListener.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TierApplier tierApplier;
    private final TransactionTemplate newTransaction;

    TierUpdateListener(TierApplier tierApplier, PlatformTransactionManager transactionManager) {
        this.tierApplier = tierApplier;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid() || e.validRunOrdinal() < 1 || e.score() == null) {
            return;
        }
        RunScoreInput input = new RunScoreInput(e.score(), e.distance(), e.validRunOrdinal());
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                newTransaction.executeWithoutResult(
                        status -> tierApplier.apply(e.memberId(), e.recordId(), input));
                return;
            } catch (TransientDataAccessException conflict) {
                log.info("tier_update_retry memberId={} attempt={}", e.memberId(), attempt);
            } catch (RuntimeException failure) {
                log.error(
                        "tier_update_failed memberId={} ordinal={} error={}",
                        e.memberId(),
                        e.validRunOrdinal(),
                        failure.getClass().getSimpleName());
                return;
            }
        }
        log.error(
                "tier_update_failed memberId={} ordinal={} error=transient",
                e.memberId(),
                e.validRunOrdinal());
    }
}
