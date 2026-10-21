package com.a207.runkk.member;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 위치·심박 동의 철회 사건(설계 6.1.1 사건 표). running.DisposalService가 받아 7일 기한의 파기 지시를 만든다(FR-026, FR-029).
 *
 * @param memberId 회원 ID
 * @param item LOCATION 또는 HEART_RATE
 * @param at 철회 일시
 */
public record ConsentWithdrawn(long memberId, ConsentItem item, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
