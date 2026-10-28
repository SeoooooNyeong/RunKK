package com.a207.runkk.member;

import com.a207.runkk.common.ReasonCode;
import java.util.Optional;

/**
 * 미이용 게스트 파기(WithdrawalService.withdrawIdleGuests)만 보는 가드 포트(설계 6.1.2, D-141). 사유가 있으면 그 게스트를 건너뛰고
 * 다음 날 다시 본다. WithdrawalGuard 목록에서 구현 형식을 골라내지 않으려고 따로 둔다(member → battle 순환 방지). 구현은
 * battle.ActiveBattleGuard 하나다. WithdrawalGuard와 같은 구현이 함께 구현하므로 반환 형식을 WithdrawalGuard.check와 같게
 * 둔다.
 */
public interface IdleGuestWithdrawalGuard {

    /** 건너뛸 사유. 없으면 비어 있음. */
    Optional<ReasonCode> check(long memberId);
}
