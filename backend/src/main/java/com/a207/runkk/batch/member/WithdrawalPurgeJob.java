package com.a207.runkk.batch.member;

import com.a207.runkk.member.WithdrawalService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 탈퇴 7일 지난 회원 자료 정리를 부른다(설계 6.1.13 batch.member, D-28). 업무 규칙 없이
 * WithdrawalService.purgeWithdrawn 하나만 부르고, 실패는 운영 로그로 남겨 다른 작업을 막지 않는다(D-39). 실행 시각은
 * MemberSchedulingConfig가 갖는다.
 */
@Component
public class WithdrawalPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalPurgeJob.class);

    private final WithdrawalService withdrawalService;
    private final Clock clock;

    public WithdrawalPurgeJob(WithdrawalService withdrawalService, Clock clock) {
        this.withdrawalService = withdrawalService;
        this.clock = clock;
    }

    public void run() {
        try {
            withdrawalService.purgeWithdrawn(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("withdrawal_purge_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
