package com.a207.runkk.battle;

/**
 * 기록 목록 행에 붙일 대결 결과(설계 5.6.2 BattleHistoryService.outcomesOf, D-233).
 *
 * @param type 이력 행 종류(historyGroup, 친구 대결은 FRIEND)
 * @param verdict 본인 판정(보류면 null)
 * @param pending 결과 보류 중
 */
public record BattleOutcome(BattleType type, Verdict verdict, boolean pending) {}
