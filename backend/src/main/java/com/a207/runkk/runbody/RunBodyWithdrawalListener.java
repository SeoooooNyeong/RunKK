package com.a207.runkk.runbody;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 회원의 런바디 작업을 지운다(설계 05-19 D-448, FR-021). ai 쪽 분석 결과 파기는 aiprivacy가 DELETE /users/{id}로
 * 한다(FR-272).
 */
@Component
public class RunBodyWithdrawalListener {

    private final RunBodyJobRepository jobs;

    public RunBodyWithdrawalListener(RunBodyJobRepository jobs) {
        this.jobs = jobs;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        jobs.deleteByMember(e.memberId());
    }
}
