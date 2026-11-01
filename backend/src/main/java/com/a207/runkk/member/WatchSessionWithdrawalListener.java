package com.a207.runkk.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴(본인 탈퇴·미이용 게스트 파기)가 커밋되면 그 회원의 워치용 갱신 토큰을 모두 폐기한다(2026-11-01 사용자 결정 W25). 탈퇴 회원의 갱신은
 * AuthService.refresh의 회원 상태 확인이 이미 막으므로 이 폐기는 이중 방어다. 그래서 저장소 장애는 탈퇴를 되돌리지 않고 error 로그만 남긴다.
 */
@Component
public class WatchSessionWithdrawalListener {

    private static final Logger log = LoggerFactory.getLogger(WatchSessionWithdrawalListener.class);

    private final AuthService authService;

    public WatchSessionWithdrawalListener(AuthService authService) {
        this.authService = authService;
    }

    // 커밋 뒤에는 원래 트랜잭션의 연결이 아직 묶여 있어 그대로 쓰면 기록이 커밋되지 않는다: 새 트랜잭션에서 쓴다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        try {
            authService.revokeWatchSessions(e.memberId());
        } catch (ProviderUnavailableException ex) {
            // 운영 경보 대상(error): 탈퇴 회원의 워치 토큰은 회원 상태 확인만으로 막힌 채 남는다
            log.error(
                    "watch_sessions_revoke_failed memberId={} reason=store_unavailable",
                    e.memberId());
        }
    }
}
