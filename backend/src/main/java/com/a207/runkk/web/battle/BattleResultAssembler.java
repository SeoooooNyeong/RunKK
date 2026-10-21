package com.a207.runkk.web.battle;

import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleResultView;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import org.springframework.stereotype.Component;

/**
 * 대결 결과 화면 응답을 조합한다(설계 6.1.13 web.battle, D-33). BattleResultService.result에
 * reward.RewardService.accruedFor(BATTLE_WIN, battleId, memberId)를 붙인다. battle → reward 화살표를 만들지
 * 않으려고 표현 계층이 붙인다. 적립이 없으면(패·무승부·보류·하루 상한) 0이다.
 */
@Component
class BattleResultAssembler {

    private final BattleResultService battleResultService;
    private final RewardService rewardService;

    BattleResultAssembler(BattleResultService battleResultService, RewardService rewardService) {
        this.battleResultService = battleResultService;
        this.rewardService = rewardService;
    }

    BattleDtos.BattleResultResponse assemble(long memberId, long battleId) {
        BattleResultView view = battleResultService.result(memberId, battleId);
        int rewardPoints =
                rewardService.accruedFor(
                        AccrualReason.BATTLE_WIN, String.valueOf(battleId), memberId);
        return new BattleDtos.BattleResultResponse(
                view.battleId(),
                view.type().historyGroup().name(),
                view.pending(),
                view.retryUpload(),
                view.verdict(),
                view.corrected(),
                BattleDtos.seconds(view.myReach()),
                BattleDtos.seconds(view.opponentReach()),
                BattleDtos.Row.of(view.comparison()),
                view.weekly(),
                rewardPoints,
                view.newRecord());
    }
}
