package com.a207.runkk.aiprivacy;

import com.a207.runkk.member.MemberPurged;
import com.a207.runkk.member.MemberWithdrawn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AiDataWithdrawalListener {
    private static final Logger log = LoggerFactory.getLogger(AiDataWithdrawalListener.class);
    private final AiPurgeService service;

    public AiDataWithdrawalListener(AiPurgeService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void prepare(MemberWithdrawn event) {
        service.register(event.memberId(), event.at());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawn event) {
        try {
            service.attempt(event.memberId(), false);
        } catch (RuntimeException error) {
            log.error(
                    "ai_purge_after_withdrawal_failed error={}", error.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberPurged(MemberPurged event) {
        try {
            // 배포 전 탈퇴하여 요청 행이 없던 회원도 7일 배치에서 복구한다.
            service.purgeResidual(event.memberId(), event.at());
        } catch (RuntimeException error) {
            log.error("ai_purge_after_residual_failed error={}", error.getClass().getSimpleName());
        }
    }
}
