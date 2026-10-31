package com.a207.runkk.web.battle;

import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleResultView;
import com.a207.runkk.reward.AccrualReason;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.RunQueryService;
import org.springframework.stereotype.Component;

/**
 * 대결 결과 화면 응답을 조합한다(설계 6.1.13 web.battle, D-33). v0.14: 적립 합계(totalRewardPoints, D-229)와 본인 기록의 티어
 * 평가(RunQueryService.evaluationOf, D-291, 12.2 W5)를 붙인다. BattleResultService.result에
 * reward.RewardService.accruedFor(BATTLE_WIN, battleId, memberId)를 붙인다. battle → reward 화살표를 만들지
 * 않으려고 표현 계층이 붙인다. 적립이 없으면(패·무승부·보류·하루 상한) 0이다.
 */
@Component
class BattleResultAssembler {

    private final BattleResultService battleResultService;
    private final RewardService rewardService;
    private final RunQueryService runQueryService;

    BattleResultAssembler(
            BattleResultService battleResultService,
            RewardService rewardService,
            RunQueryService runQueryService) {
        this.battleResultService = battleResultService;
        this.rewardService = rewardService;
        this.runQueryService = runQueryService;
    }

    BattleDtos.BattleResultResponse assemble(long memberId, long battleId) {
        BattleResultView view = battleResultService.result(memberId, battleId);
        int rewardPoints =
                rewardService.accruedFor(
                        AccrualReason.BATTLE_WIN, String.valueOf(battleId), memberId);
        String myRecordId = view.myRecordId();
        // 결과 상세의 적립 합계 = 승리 + 본인 기록 거리 적립(v0.14 D-229). 친구 대결은 승리 적립이 없다(D-226)
        int runPoints =
                myRecordId == null
                        ? 0
                        : rewardService.accruedFor(AccrualReason.VALID_RUN, myRecordId, memberId);
        BattleDtos.EvaluationDto evaluation =
                myRecordId == null
                        ? null
                        : runQueryService
                                .evaluationOf(myRecordId)
                                .map(BattleDtos.EvaluationDto::of)
                                .orElse(null);
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
                view.newRecord(),
                view.opponentName().value(),
                myRecordId,
                rewardPoints + runPoints,
                evaluation,
                view.opponentGender(),
                view.celebrityFigure());
    }
}
