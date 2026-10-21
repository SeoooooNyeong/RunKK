package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Tier;

/**
 * 기록 상대(설계 6.1.6, 6.3 인터페이스 추출). 다른 러너 기록·가상 기록·유명인 기록이 같은 메시지에 다르게 답해 매칭 통지·보간·비교표·이력 표시명·고스트 표시
 * 다섯 곳의 종류 분기를 없앤다(6.4).
 */
public interface OpponentRecord {

    /** 경과 시간별 누적 거리 표. 대결 거리 도달 뒤는 자른다. */
    OpponentTrack track(Distance battleDistance);

    DisplayName displayName();

    /** 상대 티어(유명인은 표시하지 않아 UNRANKED). */
    Tier displayTier();

    OpponentKind kind();

    /** 상대 기록 ID(OpponentRef.recordId). */
    String recordId();

    /** 비교표 상대 값. 공개되지 않은 항목은 비어 있다(FR-093). */
    ComparisonValues comparisonValues(Distance battleDistance);
}
