package com.a207.runkk.member;

import com.a207.runkk.common.DomainEvent;
import java.time.LocalDateTime;

/**
 * 탈퇴 7일 뒤 회원 자료를 정리(WithdrawalService.purgeWithdrawn)했다는 사건(코드 리뷰 13차 BE13-2). 회원 ID를 가리키는 보조 기록(예:
 * 워치 세션 세대 행)을 지울 때 쓴다. 정리 대상이 된 첫 며칠만 되풀이해 내므로(실패 재시도) 받는 쪽은 멱등이어야 한다.
 *
 * @param memberId 회원 ID
 * @param at 정리 일시
 */
public record MemberPurged(long memberId, LocalDateTime at) implements DomainEvent {

    @Override
    public LocalDateTime occurredAt() {
        return at;
    }
}
