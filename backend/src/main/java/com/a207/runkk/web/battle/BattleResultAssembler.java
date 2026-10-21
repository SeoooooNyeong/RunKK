package com.a207.runkk.web.battle;

import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleResultView;
import org.springframework.stereotype.Component;

/**
 * 대결 결과 화면 응답을 조합한다(설계 6.1.13 web.battle, D-33). BattleResultService.result에
 * reward.RewardService.accruedFor(BATTLE_WIN, battleId, memberId)를 붙인다. reward 패키지는 다음 단계(B4)에서
 * 생기므로 그때까지 적립량은 비어 있다(null).
 */
@Component
class BattleResultAssembler {

    private final BattleResultService battleResultService;

    BattleResultAssembler(BattleResultService battleResultService) {
        this.battleResultService = battleResultService;
    }

    BattleDtos.BattleResultResponse assemble(long memberId, long battleId) {
        BattleResultView view = battleResultService.result(memberId, battleId);
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
                null,
                view.newRecord());
    }
}
