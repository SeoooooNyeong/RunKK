package com.a207.runkk.member;

import com.a207.runkk.common.Tier;

/**
 * 배치고사 대결을 만들 때의 진행(05-21 2.1). 상대 점수를 정한다(A-38).
 *
 * @param nextRound 다음 판 번호 1~3
 * @param provisionalTier 임시 티어, 아직 센 판이 없으면 null
 */
public record PlacementBasis(int nextRound, Tier provisionalTier) {}
