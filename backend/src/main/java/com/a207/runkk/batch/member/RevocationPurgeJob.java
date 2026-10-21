package com.a207.runkk.batch.member;

import com.a207.runkk.member.AuthService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 3시에 만료된 갱신 토큰 폐기 목록 행 정리를 부른다(M6 후속 결정, D-39). AuthService.purgeExpiredRevocations 하나만 부르고 실패는 운영
 * 로그로 남긴다(남은 행은 다음 날 지운다, 만료된 토큰은 서명 검사에서 이미 거부된다).
 */
@Component
public class RevocationPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(RevocationPurgeJob.class);

    private final AuthService authService;
    private final Clock clock;

    public RevocationPurgeJob(AuthService authService, Clock clock) {
        this.authService = authService;
        this.clock = clock;
    }

    public void run() {
        try {
            authService.purgeExpiredRevocations(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("revocation_purge_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
