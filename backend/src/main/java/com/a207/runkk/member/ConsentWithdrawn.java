package com.a207.runkk.member;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 위치·심박·LLM 동의 철회 사건. 위치·심박은 파기 지시를 만들고 LLM은 진행 중 코치 중계를 닫는다.
 *
 * @param memberId 회원 ID
 * @param item LOCATION, HEART_RATE 또는 LLM_TRANSFER
 * @param at 철회 일시
 */
public record ConsentWithdrawn(long memberId, ConsentItem item, LocalDateTime at)
        implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
