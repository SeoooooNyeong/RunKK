package com.a207.runkk.reward;

import com.a207.runkk.battle.Battle;

/**
 * ArchUnit 음성 시험 전용(규칙 11 위반, D-134). reward는 battle에서
 * BattleJudged·BattleWin·Side·BattleResultService만 쓴다.
 */
public class ArchFixtureRewardUsesBattleEntity {
    public Long idOf(Battle battle) {
        return battle.getId();
    }
}
