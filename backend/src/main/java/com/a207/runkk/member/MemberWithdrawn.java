package com.a207.runkk.member;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 회원 탈퇴 사건(설계 6.1.1 사건 표, D-28). 패키지별 …WithdrawalListener가 자기 자료를 지운다.
 *
 * @param memberId 회원 ID
 * @param at 탈퇴 일시
 */
public record MemberWithdrawn(long memberId, LocalDateTime at) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
