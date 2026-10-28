package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;

/**
 * 추월·역전 안내(설계 5.7.2, FR-188).
 *
 * @param battleId 대결 ID
 * @param kind 추월 또는 역전당함
 * @param gapMeters 거리 차(미터, 0 이상)
 */
public record BattleGuidance(long battleId, Kind kind, long gapMeters) implements BattleMessage {

    @Override
    public String type() {
        return "BattleGuidance";
    }

    /** 안내 종류. */
    public enum Kind {
        OVERTOOK,
        OVERTAKEN
    }
}
