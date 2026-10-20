package com.a207.runkk.member;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.DomainEventPublisher;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 유스케이스를 조정한다(설계 6.1.2, srs.md 6.3 1.5, SQ-12, D-28). 확인 단계는 앱 화면이 하고 서버는 확인된 요청만 받는다(D-36).
 * 다른 패키지 자료는 MemberWithdrawn을 받은 각 패키지가 지운다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);
    private static final int RESIDUAL_RETENTION_DAYS = 7;

    private final MemberRepository memberRepository;
    private final List<WithdrawalGuard> guards;
    private final AuditService auditService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public WithdrawalService(
            MemberRepository memberRepository,
            List<WithdrawalGuard> guards,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this.memberRepository = memberRepository;
        this.guards = List.copyOf(guards);
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 가드 중 하나라도 사유를 주면 rejected(사유). 통과하면 Member.withdraw → 감사(회원탈퇴) → MemberWithdrawn 발행. */
    @Transactional
    public WithdrawalResult withdraw(long memberId) {
        for (WithdrawalGuard guard : guards) {
            Optional<String> reason = guard.check(memberId);
            if (reason.isPresent()) {
                return WithdrawalResult.rejected(reason.get());
            }
        }
        Member member = MemberLoader.load(memberRepository, memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        member.withdraw(now);
        memberRepository.save(member);
        String id = String.valueOf(memberId);
        auditService.record(
                new AuditEntry(id, ActorRole.RUNNER, AuditAction.WITHDRAWAL, id, null, null));
        eventPublisher.publish(new MemberWithdrawn(memberId, now));
        return WithdrawalResult.completed();
    }

    /**
     * batch.member의 WithdrawalPurgeJob이 3시에 부른다. 탈퇴 뒤 7일이 지난 회원마다 Member.purgeResidual → 저장. member
     * 자기 자료만 다룬다(D-28, design-review.md M3). 처리 수를 돌려준다.
     */
    @Transactional
    public int purgeWithdrawn(LocalDateTime now) {
        List<Member> members =
                memberRepository.findWithdrawnBefore(now.minusDays(RESIDUAL_RETENTION_DAYS));
        for (Member member : members) {
            member.purgeResidual();
            memberRepository.save(member);
        }
        log.info("withdrawal_purged count={}", members.size());
        return members.size();
    }
}
