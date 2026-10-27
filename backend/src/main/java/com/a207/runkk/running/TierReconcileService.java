package com.a207.runkk.running;

import com.a207.runkk.member.RunScoreInput;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 티어에 빠진 유효 러닝 반영을 매일 보정한다(설계 6.1.3, A-27, D-108, 결정 L16). 총괄 결정(설계 쟁점 7-3)으로 보정 판단 근거는 순번이 아니라 기록별
 * 반영 표시(tier_applied)다: TierUpdateListener가 실패해 표시가 서지 않은 유효 기록을 batch.running.TierReconcileJob(0시
 * 10분)이 부르는 이 서비스가 저장 순서대로 반영한다. 뒤 기록이 먼저 반영된 구멍도 메운다(TierApplier). 표시가 서면 다시 고르지 않으므로 여러 번 돌려도
 * 같다(멱등). 방향은 running → member(12.2 D3) 그대로다.
 */
@Service
public class TierReconcileService {

    private static final Logger log = LoggerFactory.getLogger(TierReconcileService.class);
    private static final int LOOKBACK_DAYS = 2;

    private final RunRecordRepository runRecordRepository;
    private final TierApplier tierApplier;
    private final TransactionTemplate memberTransaction;

    TierReconcileService(
            RunRecordRepository runRecordRepository,
            TierApplier tierApplier,
            PlatformTransactionManager transactionManager) {
        this.runRecordRepository = runRecordRepository;
        this.tierApplier = tierApplier;
        this.memberTransaction = new TransactionTemplate(transactionManager);
        this.memberTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 저장 일시가 [(오늘 − 2일) 0시, now)이고 반영 표시가 없는 유효 기록을 회원별로 묶어 저장 순서대로 반영한다. 회원마다 새 트랜잭션이고 실패는 로그 뒤 다음
     * 회원(그 회원의 반영·표시는 모두 되돌아가 다음 날 다시 고른다). 보정한(하나 이상 반영한) 회원 수를 돌려준다.
     */
    public int reconcile(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().minusDays(LOOKBACK_DAYS).atStartOfDay();
        Map<Long, List<String>> pendingByMember = new LinkedHashMap<>();
        for (SavedRun run : runRecordRepository.tierPendingBetween(from, now)) {
            pendingByMember
                    .computeIfAbsent(run.memberId(), k -> new ArrayList<>())
                    .add(run.recordId());
        }
        int fixed = 0;
        for (Map.Entry<Long, List<String>> entry : pendingByMember.entrySet()) {
            long memberId = entry.getKey();
            try {
                memberTransaction.executeWithoutResult(
                        status -> reconcileMember(memberId, entry.getValue()));
                fixed++;
            } catch (RuntimeException e) {
                log.error(
                        "tier_reconcile_failed memberId={} error={}",
                        memberId,
                        e.getClass().getSimpleName());
            }
        }
        log.info(
                "tier_reconciled from={} members={} fixed={}", from, pendingByMember.size(), fixed);
        return fixed;
    }

    /** 한 회원의 반영 대기 기록을 저장 순서대로 넘긴다. */
    private void reconcileMember(long memberId, List<String> recordIds) {
        for (String recordId : recordIds) {
            ValidRunScore run =
                    runRecordRepository
                            .validRunScoreOf(recordId)
                            .orElseThrow(() -> new IllegalStateException("유효 기록 없음"));
            tierApplier.apply(
                    memberId,
                    recordId,
                    new RunScoreInput(run.score(), run.distance(), run.ordinal()));
        }
    }
}
