package com.a207.runkk.battle;

/**
 * 한 주 대결 전적(자료사전 주간전적 = 승·패·무승부 수).
 *
 * @param wins 승
 * @param losses 패
 * @param draws 무승부
 */
public record WeeklyRecord(int wins, int losses, int draws) {}
