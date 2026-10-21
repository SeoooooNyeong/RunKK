package com.a207.runkk.batch.crew;

import com.a207.runkk.crew.MembershipService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 7일 지난 가입 신청 만료를 부른다(설계 6.1.13 batch.crew, Q-40, D-39). 새벽 푸시 정책은 부록 E E-16에서 확인한다. 실패는 운영 로그로
 * 남긴다.
 */
@Component
public class JoinRequestExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(JoinRequestExpiryJob.class);

    private final MembershipService membershipService;
    private final Clock clock;

    public JoinRequestExpiryJob(MembershipService membershipService, Clock clock) {
        this.membershipService = membershipService;
        this.clock = clock;
    }

    public void run() {
        try {
            membershipService.expireRequests(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("join_request_expiry_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
