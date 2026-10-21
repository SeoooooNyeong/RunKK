package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 대결 이력(자료사전 대결이력 = 대결통계 + 목록, FR-101).
 *
 * @param total 대결 수
 * @param wins 승
 * @param losses 패
 * @param selfNewRecords 나와의 싸움 신기록 수
 * @param entries 최신순 목록
 */
public record BattleHistoryView(
        int total, int wins, int losses, int selfNewRecords, List<Entry> entries) {

    public BattleHistoryView {
        entries = List.copyOf(entries);
    }

    /**
     * 이력 한 줄. 고스트는 RANDOM으로 보인다(Q-02).
     *
     * @param battleId 대결 ID
     * @param playedAt 생성 일시
     * @param type 이력 분류(BattleType.historyGroup)
     * @param opponentName 상대 표시명(탈퇴면 "탈퇴한 사용자")
     * @param distance 대결 거리
     * @param verdict 판정, 보류면 null
     * @param newRecord 신기록 여부
     */
    public record Entry(
            long battleId,
            LocalDateTime playedAt,
            BattleType type,
            DisplayName opponentName,
            Distance distance,
            Verdict verdict,
            boolean newRecord) {}
}
