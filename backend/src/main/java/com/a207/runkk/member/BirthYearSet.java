package com.a207.runkk.member;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 회원이 프로필에서 출생연도를 넣거나 바꿨다(MR !91 e2e 1). 런바디가 출생연도가 없어 멈춘 분석을 다시 낸다(RunBodyService).
 *
 * @param memberId 회원 ID
 * @param at 편집 일시
 */
public record BirthYearSet(long memberId, LocalDateTime at) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
