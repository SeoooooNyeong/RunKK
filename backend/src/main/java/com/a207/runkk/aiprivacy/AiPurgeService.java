package com.a207.runkk.aiprivacy;

import com.a207.runkk.audit.*;
import com.a207.runkk.member.MemberQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiPurgeService {
    private static final Logger log = LoggerFactory.getLogger(AiPurgeService.class);
    private final AiPurgeRequestRepository repository;
    private final AiDataEraser eraser;
    private final AuditService audit;
    private final Clock clock;
    private final MemberQueryService members;
    private final TransactionTemplate transactions;

    public AiPurgeService(
            AiPurgeRequestRepository repository,
            AiDataEraser eraser,
            AuditService audit,
            Clock clock,
            MemberQueryService members,
            PlatformTransactionManager manager) {
        this.repository = repository;
        this.eraser = eraser;
        this.audit = audit;
        this.clock = clock;
        this.members = members;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** BEFORE_COMMIT에서는 탈퇴와 같은 트랜잭션에 기록해 프로세스 종료에도 재시도가 남는다. */
    @Transactional
    public void register(long memberId, LocalDateTime withdrawnAt) {
        if (repository.findForUpdate(memberId).isEmpty()) {
            repository.save(AiPurgeRequest.of(memberId, withdrawnAt));
        }
    }

    public void attempt(long memberId, boolean force) {
        String token =
                transactions.execute(
                        tx -> {
                            AiPurgeRequest request =
                                    repository.findForUpdate(memberId).orElseThrow();
                            String claim = request.claim(LocalDateTime.now(clock), force);
                            repository.save(request);
                            return claim;
                        });
        if (token == null) return;
        boolean success;
        try {
            eraser.erase(memberId);
            success = true;
        } catch (RuntimeException error) {
            // 외부 예외 본문에는 주소·토큰·식별자가 있을 수 있다. 유형만 기록한다.
            log.warn("ai_purge_failed error={}", error.getClass().getSimpleName());
            success = false;
        }
        finish(memberId, token, success);
    }

    private void finish(long memberId, String token, boolean success) {
        transactions.executeWithoutResult(
                tx -> {
                    AiPurgeRequest request = repository.findForUpdate(memberId).orElseThrow();
                    if (!request.owns(token)) return;
                    if (success) {
                        request.succeeded(LocalDateTime.now(clock));
                    } else if (request.failed(LocalDateTime.now(clock))) {
                        audit.record(
                                new AuditEntry(
                                        "SYSTEM",
                                        ActorRole.SYSTEM,
                                        AuditAction.AI_DATA_PURGE_OVERDUE,
                                        Long.toString(memberId),
                                        "PENDING",
                                        "ALERTED attempts=" + request.attempts()));
                        log.error("ai_purge_overdue attempts={}", request.attempts());
                    }
                    repository.save(request);
                });
    }

    /** ALERTED도 계속 재시도한다. 한 회원 실패가 다음 회원을 막지 않는다. */
    public void retryDue() {
        for (var withdrawal : members.withdrawalsBefore(LocalDateTime.now(clock)).entrySet()) {
            try {
                transactions.executeWithoutResult(
                        tx -> register(withdrawal.getKey(), withdrawal.getValue()));
            } catch (RuntimeException error) {
                log.error("ai_purge_backfill_failed error={}", error.getClass().getSimpleName());
            }
        }
        List<Long> dueMembers =
                transactions.execute(tx -> repository.dueMembers(LocalDateTime.now(clock)));
        for (long memberId : dueMembers) {
            try {
                attempt(memberId, false);
            } catch (RuntimeException error) {
                log.error("ai_purge_retry_failed error={}", error.getClass().getSimpleName());
            }
        }
    }

    public void purgeResidual(long memberId, LocalDateTime now) {
        transactions.executeWithoutResult(tx -> register(memberId, now.minusDays(7)));
        attempt(memberId, true);
    }
}
