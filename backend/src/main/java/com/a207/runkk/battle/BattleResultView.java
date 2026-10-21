package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import java.time.Duration;
import java.util.Optional;

/**
 * 참가자 한 명 기준 대결 결과(자료사전 대결결과, FR-093, FR-108). 적립 리워드는 web.battle의 BattleResultAssembler가
 * 붙인다(D-33).
 *
 * @param battleId 대결 ID
 * @param type 대결 종류
 * @param pending 결과 보류인가
 * @param retryUpload 보류이고 본인 기록이 서버에 없어 업로드 재시도를 안내하는가
 * @param verdict 본인 판정, 보류면 null
 * @param myReach 본인 도달 시간, 없으면 null
 * @param opponentReach 상대 도달 시간, 없으면 null
 * @param corrected 정정된 판정인가
 * @param comparison 비교표
 * @param weekly 이번 주 전적
 * @param newRecord 나와의 싸움에서 이겨 최고 기록이 바뀌었는가
 * @param opponentName 상대 표시명(결과 문구 "○○ 님보다 …", FR-093)
 * @param myRecordId 본인 러닝 기록 ID(기록 카드 FR-068로 가는 길), 아직 없으면 null
 */
public record BattleResultView(
        long battleId,
        BattleType type,
        boolean pending,
        boolean retryUpload,
        Verdict verdict,
        Duration myReach,
        Duration opponentReach,
        boolean corrected,
        ComparisonTable comparison,
        WeeklyRecord weekly,
        boolean newRecord,
        DisplayName opponentName,
        String myRecordId) {

    public Optional<Verdict> verdictValue() {
        return Optional.ofNullable(verdict);
    }
}
