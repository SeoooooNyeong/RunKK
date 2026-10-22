package com.a207.runkk.runimal;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원의 Runimal 판정 상태·코스 판정을 지운다(설계 6.1.8, FR-021 D11의 유형 부분, D-28). */
@Component
public class RunimalWithdrawalListener {

    private final RunimalProfileRepository runimalProfileRepository;
    private final CourseVerdictRepository courseVerdictRepository;

    public RunimalWithdrawalListener(
            RunimalProfileRepository runimalProfileRepository,
            CourseVerdictRepository courseVerdictRepository) {
        this.runimalProfileRepository = runimalProfileRepository;
        this.courseVerdictRepository = courseVerdictRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        runimalProfileRepository.deleteByMember(e.memberId());
        courseVerdictRepository.deleteByMember(e.memberId());
    }
}
