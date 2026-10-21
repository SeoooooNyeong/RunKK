package com.a207.runkk.battle;

import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.WithdrawalGuard;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 대기 중이거나 활성 대결이 있으면 탈퇴를 막는다(설계 6.1.6, member.WithdrawalGuard 구현, FR-024). */
@Component
public class ActiveBattleGuard implements WithdrawalGuard {

    private final MatchQueue matchQueue;
    private final BattleRepository battleRepository;

    public ActiveBattleGuard(MatchQueue matchQueue, BattleRepository battleRepository) {
        this.matchQueue = matchQueue;
        this.battleRepository = battleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReasonCode> check(long memberId) {
        if (matchQueue.findWaiting(memberId).isPresent()
                || battleRepository.existsActive(memberId)) {
            return Optional.of(ReasonCode.BATTLE_IN_PROGRESS);
        }
        return Optional.empty();
    }
}
