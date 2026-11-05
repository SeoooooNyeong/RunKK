package com.a207.runkk.battle.live;

import com.a207.runkk.battle.BattleMessage;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;
import java.time.Duration;

/**
 * 매칭 성사 통지(설계 5.7.2, FR-086). 고스트 대결도 같은 항목만 가진다(Q-02). JSON 항목 이름은 구성요소 이름이다(계약 시험, H-17).
 *
 * @param battleId 대결 ID
 * @param opponentName 상대 표시명
 * @param opponentTier 상대 티어
 * @param opponentGender 상대 캐릭터 성별(MALE·FEMALE), 셀럽·나와의 싸움이면 null(2026-10-31 결정)
 * @param distance 대결 거리(미터)
 * @param myBest 본인 그 거리 최고 기록(초), 없으면 null
 * @param opponentBest 상대 최고 기록(초), 없으면 null
 * @param animationSeconds 매칭 애니메이션 길이(D-17)
 * @param placementRound 배치고사 대결이면 판 번호(1~3), 아니면 null(05-03 5.7.3.4). 이때 opponentTier는
 *     UNRANKED(J24-3)
 */
public record MatchFound(
        long battleId,
        DisplayName opponentName,
        Tier opponentTier,
        String opponentGender,
        Distance distance,
        Duration myBest,
        Duration opponentBest,
        int animationSeconds,
        Integer placementRound)
        implements BattleMessage {

    /** 배치고사가 아닌 성사(placementRound 없음). */
    public MatchFound(
            long battleId,
            DisplayName opponentName,
            Tier opponentTier,
            String opponentGender,
            Distance distance,
            Duration myBest,
            Duration opponentBest,
            int animationSeconds) {
        this(
                battleId,
                opponentName,
                opponentTier,
                opponentGender,
                distance,
                myBest,
                opponentBest,
                animationSeconds,
                null);
    }

    @Override
    public String type() {
        return "MatchFound";
    }
}
