package com.a207.runkk.battle;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원을 매칭 대기열에서 뺀다(설계 6.1.6, D-28). 대결 기록은 남기고 참가자 표시명은 조회 때 회원이 없으면 "탈퇴한 사용자"다(FR-021). */
@Component
public class BattleWithdrawalListener {

    private final MatchQueue matchQueue;

    public BattleWithdrawalListener(MatchQueue matchQueue) {
        this.matchQueue = matchQueue;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        matchQueue.remove(e.memberId());
    }
}
