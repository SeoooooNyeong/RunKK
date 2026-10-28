package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;

/**
 * 대결 종료 신호(설계 5.7.2).
 *
 * @param battleId 대결 ID
 * @param status 종료 상태
 */
public record BattleEnded(long battleId, Status status) implements BattleMessage {

    @Override
    public String type() {
        return "BattleEnded";
    }

    /** 종료 상태. */
    public enum Status {
        RESULT_PENDING,
        FINISHED,
        OPPONENT_FORFEITED
    }
}
