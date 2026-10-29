package com.a207.runkk.running;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 집계 제외 기록이 삭제되었다(설계 5.6.2, SQ-33, D-232). 기록 ID 문자열만 들고 외래 키가 없는 다른 패키지 표를 커밋 뒤 정리한다: runimal
 * course_verdict, crew 크루런 참가 연결, battle 참가자 기록 ID.
 *
 * @param recordId 기록 ID
 * @param memberId 기록 주인
 * @param battleId 대결 ID(없으면 null)
 * @param crewRunPostId 크루런 게시글 ID(없으면 null)
 * @param at 삭제 시각
 */
public record RunRecordDeleted(
        String recordId, long memberId, Long battleId, Long crewRunPostId, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
