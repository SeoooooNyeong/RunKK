package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;

/**
 * 기록 대결 시작 정보(자료사전 기록대결안내, design-review.md M34). 앱은 3초 카운트다운 뒤 POST /battles/{id}/started로 시작을
 * 알린다(R-02).
 *
 * @param battleId 대결 ID
 * @param type SELF 또는 CELEBRITY
 * @param opponentName 상대 표시명
 * @param distance 대결 거리
 * @param track 상대 경과 시간·누적 거리 표(D-05)
 * @param source 유명인 출처, 나와의 싸움이면 null
 * @param estimated 평균 페이스로 추정한 표인가(A-006)
 */
public record RecordBattleStart(
        long battleId,
        BattleType type,
        DisplayName opponentName,
        Distance distance,
        OpponentTrack track,
        Source source,
        boolean estimated) {}
