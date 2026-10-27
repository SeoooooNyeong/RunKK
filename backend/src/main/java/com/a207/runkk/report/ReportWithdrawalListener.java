package com.a207.runkk.report;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 회원 탈퇴 때 그 회원의 신고에서 신고자를 지운다(설계 6.1.12a, FR-021, D-104, 12.2 N9). 사례와 신고 건수는 남긴다. 익명화된 신고자는 처리 결과
 * 알림을 받지 않는다.
 */
@Component
public class ReportWithdrawalListener {

    private final ReportCaseRepository cases;

    ReportWithdrawalListener(ReportCaseRepository cases) {
        this.cases = cases;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        cases.anonymizeReporter(e.memberId());
    }
}
