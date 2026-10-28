package com.a207.runkk.web.auth;

import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 접근 토큰의 회원이 아직 ACTIVE인지(코드 리뷰 2차 L21). 서명·만료만 보면 탈퇴한 회원의 접근 토큰이 최대 1시간 통한다. 확인 결과(ACTIVE)는 30초 동안
 * 메모리에 둬 요청마다 회원 행을 읽지 않고, 탈퇴 사건(MemberWithdrawn, 커밋 뒤)을 받으면 바로 지운다. 인스턴스 1개 기준(5.4).
 */
@Component
public class ActiveMembers {

    /** ACTIVE 확인 결과를 믿는 시간. */
    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    /** 캐시 항목이 이만큼 쌓이면 만료된 것을 치운다(메모리 상한). */
    private static final int PRUNE_THRESHOLD = 10_000;

    private final MemberQueryService memberQueryService;
    private final Clock clock;
    private final Map<Long, Instant> activeUntil = new ConcurrentHashMap<>();

    /** 탈퇴 사건을 받은 회원과 표시 만료(접근 토큰 수명 1시간). DB 확인과 캐시 저장 사이에 탈퇴가 끼어도 낡은 ACTIVE가 남지 않게 한다. */
    private final Map<Long, Instant> withdrawnUntil = new ConcurrentHashMap<>();

    /** 탈퇴 표시를 두는 시간(접근 토큰 수명과 같다). */
    static final Duration WITHDRAWN_MARK_TTL = Duration.ofHours(1);

    ActiveMembers(MemberQueryService memberQueryService, Clock clock) {
        this.memberQueryService = memberQueryService;
        this.clock = clock;
    }

    /** 회원이 있고 ACTIVE인가. */
    boolean isActive(long memberId) {
        Instant now = clock.instant();
        Instant withdrawn = withdrawnUntil.get(memberId);
        if (withdrawn != null && withdrawn.isAfter(now)) {
            return false;
        }
        Instant until = activeUntil.get(memberId);
        if (until != null && until.isAfter(now)) {
            return true;
        }
        if (!memberQueryService.isActive(memberId)) {
            activeUntil.remove(memberId);
            return false;
        }
        if (activeUntil.size() >= PRUNE_THRESHOLD) {
            activeUntil.values().removeIf(expiry -> !expiry.isAfter(now));
            withdrawnUntil.values().removeIf(expiry -> !expiry.isAfter(now));
        }
        activeUntil.put(memberId, now.plus(CACHE_TTL));
        if (withdrawnUntil.containsKey(memberId)) {
            activeUntil.remove(memberId); // 조회 중에 탈퇴 사건이 왔다
            return false;
        }
        return true;
    }

    /** 탈퇴가 커밋되면 캐시를 지워 남은 토큰을 바로 거부한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        withdrawnUntil.put(e.memberId(), clock.instant().plus(WITHDRAWN_MARK_TTL));
        activeUntil.remove(e.memberId());
    }
}
