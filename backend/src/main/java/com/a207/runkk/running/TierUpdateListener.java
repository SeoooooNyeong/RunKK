package com.a207.runkk.running;

import com.a207.runkk.member.RunScoreInput;
import com.a207.runkk.member.TierLevelService;
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
 * 유효 러닝 저장 사건을 member의 티어 갱신 호출로 넘긴다(설계 6.1.3, D-03 예외). 저장 커밋 뒤 동기로 새 트랜잭션에서 부르고, 같은 회원 행을 함께 고쳐
 * 낙관적 잠금 충돌·잠금 대기 같은 일시 오류면 3번까지 다시 한다(코드 리뷰 2차 L16). 그래도 실패하면 운영 로그를 남기고, 다음 유효 기록의 순번이 건너뛰어 오면
 * TierProgress가 판정 지점을 놓치지 않는다(D-40).
 */
@Component
public class TierUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(TierUpdateListener.class);
    private static final int MAX_ATTEMPTS = 3;

    private final TierLevelService tierLevelService;
    private final TransactionTemplate newTransaction;

    public TierUpdateListener(
            TierLevelService tierLevelService, PlatformTransactionManager transactionManager) {
        this.tierLevelService = tierLevelService;
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
                        status -> tierLevelService.recordValidRun(e.memberId(), input));
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
