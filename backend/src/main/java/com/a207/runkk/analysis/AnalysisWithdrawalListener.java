package com.a207.runkk.analysis;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 회원의 목표·계획(D3)과 리포트(D4)를 지운다(설계 6.1.4 AnalysisWithdrawalListener, D-28, FR-021). 랭킹 스냅숏의 줄은 다음
 * 0시 재집계 때 빠진다(탈퇴 회원은 rankedMemberIds에 없다).
 */
@Component
public class AnalysisWithdrawalListener {

    private final RunningGoalRepository runningGoalRepository;
    private final ReportRepository reportRepository;

    public AnalysisWithdrawalListener(
            RunningGoalRepository runningGoalRepository, ReportRepository reportRepository) {
        this.runningGoalRepository = runningGoalRepository;
        this.reportRepository = reportRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        runningGoalRepository.deleteByMember(e.memberId());
        reportRepository.deleteByMember(e.memberId());
    }
}
