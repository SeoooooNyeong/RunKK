package com.a207.runkk.battle.live;

/**
 * 상대 표 한 점(BattleStart.opponentTrack, 설계 5.7.2). 위치 좌표가 없다(FR-089).
 *
 * @param elapsedSeconds 경과 시간(초)
 * @param distanceMeters 누적 거리(미터)
 */
public record TrackPoint(long elapsedSeconds, long distanceMeters) {}
