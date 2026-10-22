package com.a207.runkk.battle;

import java.time.LocalDateTime;

/**
 * 회원이 이긴 판정 한 건(reward 일 단위 보정용). 정정으로 승자가 바뀌었으면 지금 승자와 정정 판정 시각이다.
 *
 * @param battleId 대결 ID
 * @param winnerId 이긴 회원 ID
 * @param judgedAt 판정 시각(BattleJudged의 Judgement.at과 같다)
 */
public record BattleWin(long battleId, long winnerId, LocalDateTime judgedAt) {}
