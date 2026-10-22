package com.a207.runkk.battle;

/**
 * 받는 회원이 정해진 대결 메시지(LiveBattleSession이 돌려주고 LiveBattleService가 BattleMessenger로 보낸다).
 *
 * @param recipientId 받는 회원
 * @param message 메시지
 */
public record BattleDelivery(long recipientId, BattleMessage message) {}
