package com.a207.runkk.batch.member;

import com.a207.runkk.member.WithdrawalService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 1년 미접속 게스트 자료 파기를 부른다(설계 6.1.13 batch.member, FR-200, D-89).
 * WithdrawalService.withdrawIdleGuests 하나만 부르고 실패는 운영 로그로 남겨 다른 작업을 막지 않는다(D-39). 실행 시각은
 * MemberSchedulingConfig가 갖는다.
 */
@Component
public class IdleGuestWithdrawalJob {

    private static final Logger log = LoggerFactory.getLogger(IdleGuestWithdrawalJob.class);

    private final WithdrawalService withdrawalService;
    private final Clock clock;

    public IdleGuestWithdrawalJob(WithdrawalService withdrawalService, Clock clock) {
        this.withdrawalService = withdrawalService;
        this.clock = clock;
    }

    public void run() {
        try {
            withdrawalService.withdrawIdleGuests(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("idle_guest_withdrawal_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
