package com.a207.runkk.battle;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원을 매칭 대기열에서 뺀다(설계 6.1.6, D-28). 대결 기록은 남기고 참가자 표시명은 조회 때 회원이 없으면 "탈퇴한 사용자"다(FR-021). */
@Component
public class BattleWithdrawalListener {

    private final MatchQueue matchQueue;
    private final FriendInviteService friendInviteService;

    public BattleWithdrawalListener(
            MatchQueue matchQueue, FriendInviteService friendInviteService) {
        this.matchQueue = matchQueue;
        this.friendInviteService = friendInviteService;
    }

    /** 대기열에서 빼고 대기 중인 친구 대결 초대를 취소한 뒤 그 회원의 초대 행을 지운다(v0.14 D-223, 탈퇴 파기). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        try {
            matchQueue.remove(e.memberId());
        } finally {
            // 대기열(Redis) 장애가 초대 취소를 막지 않게 한다
            friendInviteService.cancelOpenOf(e.memberId());
            friendInviteService.eraseOf(e.memberId());
        }
    }
}
