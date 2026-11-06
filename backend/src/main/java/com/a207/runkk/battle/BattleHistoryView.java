package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 대결 이력(자료사전 대결이력 = 대결통계 + 목록, FR-101).
 *
 * @param total 대결 수
 * @param wins 승
 * @param losses 패
 * @param selfNewRecords 나와의 싸움 신기록 수
 * @param entries 최신순 목록(페이지를 나눴으면 그 페이지)
 * @param nextCursor 다음 페이지 커서, 마지막 페이지거나 나누지 않았으면 null
 */
public record BattleHistoryView(
        int total, int wins, int losses, int selfNewRecords, List<Entry> entries, Long nextCursor) {

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
     * @param gap 기록 차이(본인 도달 시간 − 상대 도달 시간, 음수면 본인이 빠름, FR-101), 어느 쪽이든 도달 시간이 없으면 null
     * @param sourceLabel 유명인 기록 출처 표기(FR-100), 유명인 대결이 아니거나 기록이 지워졌으면 null
     * @param placementRound 배치고사 대결이면 판 번호, 아니면 null(05-03 P6, J24-22)
     * @param opponentMemberId 친구 대결이고 상대가 탈퇴하지 않았으면 상대 회원 ID, 그 밖은 null(D-492)
     */
    public record Entry(
            long battleId,
            LocalDateTime playedAt,
            BattleType type,
            DisplayName opponentName,
            Distance distance,
            Verdict verdict,
            boolean newRecord,
            Duration gap,
            String sourceLabel,
            Integer placementRound,
            Long opponentMemberId) {}
}
