package com.a207.runkk.running;

import com.a207.runkk.common.Distance;
import java.time.LocalDateTime;

/**
 * 저장 일시로 고른 기록 한 건(커밋 뒤 수신자 보정 배치용). 좌표가 없다(NFR-021). reward 일 단위 보정은 savedAt 날짜로 원래 사건일의 하루 상한을
 * 세고, crew 크루런 연결 보정은 crewRunPostId·startedAt·distance로 수신자와 같은 연결을 다시 한다.
 *
 * @param recordId 기록 ID
 * @param memberId 회원 ID
 * @param startedAt 시작 일시
 * @param distance 거리
 * @param valid 유효 러닝
 * @param crewRunPostId 크루런 게시글 ID(없으면 null)
 * @param savedAt 서버 저장 일시(RunRecordSaved.occurredAt)
 */
public record SavedRun(
        String recordId,
        long memberId,
        LocalDateTime startedAt,
        Distance distance,
        boolean valid,
        Long crewRunPostId,
        LocalDateTime savedAt) {

    /** 커밋 뒤 수신자가 받은 저장 사건을 보정 배치와 같은 형식으로 바꾼다(저장 일시 = 사건 발생 시각). */
    public static SavedRun of(RunRecordSaved e) {
        return new SavedRun(
                e.recordId(),
                e.memberId(),
                e.startedAt(),
                e.distance(),
                e.valid(),
                e.crewRunPostId(),
                e.occurredAt());
    }
}
