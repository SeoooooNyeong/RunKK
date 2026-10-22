package com.a207.runkk.reward;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원의 리워드 지갑(잔액·적립 내역·보유 아이템)을 지운다(설계 6.1.8, FR-021 D12, D-28). */
@Component
public class RewardWithdrawalListener {

    private final RewardWalletRepository rewardWalletRepository;

    public RewardWithdrawalListener(RewardWalletRepository rewardWalletRepository) {
        this.rewardWalletRepository = rewardWalletRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        rewardWalletRepository.deleteByMember(e.memberId());
    }
}
