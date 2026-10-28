package com.a207.runkk.running;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원의 러닝 기록(경로·심박 포함), 최고 기록 장부, 파기 지시를 지운다(설계 6.1.3, FR-021 D2). */
@Component
public class RunWithdrawalListener {

    private final RunRecordRepository runRecordRepository;
    private final PersonalRecordBookRepository personalRecordBookRepository;
    private final DisposalOrderRepository disposalOrderRepository;

    public RunWithdrawalListener(
            RunRecordRepository runRecordRepository,
            PersonalRecordBookRepository personalRecordBookRepository,
            DisposalOrderRepository disposalOrderRepository) {
        this.runRecordRepository = runRecordRepository;
        this.personalRecordBookRepository = personalRecordBookRepository;
        this.disposalOrderRepository = disposalOrderRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        runRecordRepository.deleteByMember(e.memberId());
        personalRecordBookRepository.deleteByMember(e.memberId());
        disposalOrderRepository.deleteByMember(e.memberId());
    }
}
