package com.a207.runkk.web.record;

import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingSnapshotRebuilt;
import com.a207.runkk.avatar.RunnerLookQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 랭킹 재집계(0시 작업·시작 집계·운영자 재계산)가 개인 스냅숏을 커밋하면 그 스냅숏 상위 줄 회원의 모습을 조회와 같은 키로 곧바로 저장한다(PM 2026-11-14
 * 「0시에 미리 채운다」, DF-089, D-858). analysis·batch.analysis·web.admin.ranking은 avatar를 쓸 수 없어(D-46) 둘을
 * 다 쓸 수 있는 web.record가 사건으로 받는다. 커밋 뒤 동기로 새 읽기 트랜잭션에서 돈다(running.TierUpdateListener와 같은 방식). 실패는 경고
 * 로그만 남기고 재집계는 성공으로 둔다. 조회 때 지연 채움(RunnerLookQuery.snapshotLooksOf)이 안전망이다(미리 채우기 실패, 상위 밖 본인 줄).
 * 크루 랭킹은 모습이 없어 건너뛴다.
 */
@Component
public class RankingLookPrefill {

    private static final Logger log = LoggerFactory.getLogger(RankingLookPrefill.class);

    private final RunnerLookQuery runnerLookQuery;
    private final TransactionTemplate newReadTransaction;

    RankingLookPrefill(
            RunnerLookQuery runnerLookQuery, PlatformTransactionManager transactionManager) {
        this.runnerLookQuery = runnerLookQuery;
        this.newReadTransaction = new TransactionTemplate(transactionManager);
        this.newReadTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.newReadTransaction.setReadOnly(true);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRebuilt(RankingSnapshotRebuilt e) {
        if (e.kind() != RankingKind.PERSONAL || e.topSubjectIds().isEmpty()) {
            return;
        }
        String snapshot = RankingSnapshotIds.of(e.kind(), e.period(), e.computedAt());
        try {
            newReadTransaction.executeWithoutResult(
                    status -> runnerLookQuery.snapshotLooksOf(snapshot, e.topSubjectIds()));
        } catch (RuntimeException failure) {
            log.warn(
                    "ranking_look_prefill_failed kind={} period={} error={}",
                    e.kind(),
                    e.period(),
                    failure.getClass().getSimpleName());
        }
    }
}
