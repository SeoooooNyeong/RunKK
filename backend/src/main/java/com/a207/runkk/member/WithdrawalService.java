package com.a207.runkk.member;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴 유스케이스를 조정한다(설계 6.1.2, srs.md 6.3 1.5, SQ-12, D-28). 확인 단계는 앱 화면이 하고 서버는 확인된 요청만 받는다(D-36).
 * 다른 패키지 자료는 MemberWithdrawn을 받은 각 패키지가 지운다.
 */
@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);
    private static final int RESIDUAL_RETENTION_DAYS = 7;

    /** 자료 정리 사건(MemberPurged)을 다시 내 주는 날 수. 이 기간이 지난 회원에게는 매일 밤 되풀이해 내지 않는다(BE14-2). */
    private static final int PURGE_EVENT_RETRY_DAYS = 3;

    /** 미이용 게스트 파기 기준(FR-200, D-113, D-156). 윤년 영향이 없게 365일로 고정한다. 운영 기준값이 아니다. */
    static final Duration GUEST_IDLE_LIMIT = Duration.ofDays(365);

    /** 미이용 게스트 파기 감사의 행위자 ID(D-118). */
    static final String IDLE_GUEST_ACTOR = "system:idle-guest";

    private final MemberRepository memberRepository;
    private final PlacementProgressRepository placementProgressRepository;
    private final List<WithdrawalGuard> guards;
    private final List<IdleGuestWithdrawalGuard> idleGuards;
    private final AuditService auditService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;
    private final TransactionOperations transactions;

    @Autowired
    public WithdrawalService(
            MemberRepository memberRepository,
            PlacementProgressRepository placementProgressRepository,
            List<WithdrawalGuard> guards,
            List<IdleGuestWithdrawalGuard> idleGuards,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this(
                memberRepository,
                placementProgressRepository,
                guards,
                idleGuards,
                auditService,
                eventPublisher,
                clock,
                perGuestTransaction(transactionManager));
    }

    /** 시험용: 트랜잭션 없이 실행한다. */
    WithdrawalService(
            MemberRepository memberRepository,
            PlacementProgressRepository placementProgressRepository,
            List<WithdrawalGuard> guards,
            List<IdleGuestWithdrawalGuard> idleGuards,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            Clock clock) {
        this(
                memberRepository,
                placementProgressRepository,
                guards,
                idleGuards,
                auditService,
                eventPublisher,
                clock,
                TransactionOperations.withoutTransaction());
    }

    private WithdrawalService(
            MemberRepository memberRepository,
            PlacementProgressRepository placementProgressRepository,
            List<WithdrawalGuard> guards,
            List<IdleGuestWithdrawalGuard> idleGuards,
            AuditService auditService,
            DomainEventPublisher eventPublisher,
            Clock clock,
            TransactionOperations transactions) {
        this.transactions = transactions;
        this.memberRepository = memberRepository;
        this.placementProgressRepository = placementProgressRepository;
        this.guards = List.copyOf(guards);
        this.idleGuards = List.copyOf(idleGuards);
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 가드 중 하나라도 사유를 주면 BusinessRuleException(사유 코드). 통과하면 Member.withdraw → 감사(회원탈퇴) →
     * MemberWithdrawn 발행.
     */
    @Transactional
    public WithdrawalResult withdraw(long memberId) {
        for (WithdrawalGuard guard : guards) {
            Optional<ReasonCode> reason = guard.check(memberId);
            if (reason.isPresent()) {
                throw new BusinessRuleException(reason.get());
            }
        }
        // 판 셈과 같은 잠금 순서(배치 진행 → 회원)를 지켜 3판째 셈과 겹쳐도 교착하지 않는다(리뷰 H1)
        placementProgressRepository.findByIdForUpdate(memberId);
        Member member = MemberLoader.load(memberRepository, memberId);
        LocalDateTime now = LocalDateTime.now(clock);
        member.withdraw(now);
        memberRepository.save(member);
        placementProgressRepository.deleteByMemberId(memberId); // 배치 기록을 남기지 않는다(D-489)
        String id = String.valueOf(memberId);
        auditService.record(
                new AuditEntry(id, ActorRole.RUNNER, AuditAction.WITHDRAWAL, id, null, null));
        eventPublisher.publish(new MemberWithdrawn(memberId, now));
        return WithdrawalResult.completed();
    }

    /**
     * batch.member의 WithdrawalPurgeJob이 3시에 부른다. 탈퇴 뒤 7일이 지난 회원마다 Member.purgeResidual → 저장. member
     * 자기 자료만 다룬다(D-28, design-review.md M3). 처리 수를 돌려준다. 자료 정리 사건은 정리 대상이 된 첫 날부터
     * PURGE_EVENT_RETRY_DAYS일 안에만 내서 이미 정리한 회원에게 밤마다 되풀이하지 않는다(BE14-2).
     */
    @Transactional
    public int purgeWithdrawn(LocalDateTime now) {
        List<Member> members =
                memberRepository.findWithdrawnBefore(now.minusDays(RESIDUAL_RETENTION_DAYS));
        for (Member member : members) {
            member.purgeResidual();
            memberRepository.save(member);
            if (!member.withdrawnAt()
                    .isBefore(now.minusDays(RESIDUAL_RETENTION_DAYS + PURGE_EVENT_RETRY_DAYS))) {
                eventPublisher.publish(new MemberPurged(member.getId(), now));
            }
        }
        log.info("withdrawal_purged count={}", members.size());
        return members.size();
    }

    /**
     * batch.member의 IdleGuestWithdrawalJob이 3시에 부른다(FR-200, D-118, D-141, D-156). 마지막 접속이 now −
     * 365일 이하(정각 포함)인 ACTIVE 게스트마다 IdleGuestWithdrawalGuard를 보고(사유가 있으면 건너뛰고 다음 날 다시 본다)
     * Member.withdraw → 감사(WITHDRAWAL, SYSTEM, "system:idle-guest") → MemberWithdrawn 발행. 크루장 게스트의
     * 크루는 CrewWithdrawalListener가 넘기거나 지운다(WithdrawalGuard는 부르지 않는다). 처리 수를 돌려준다. 게스트마다 따로 트랜잭션을
     * 연다: 한 게스트의 충돌(그 시각의 접속 기록 등)이나 커밋 뒤 수신자 실패가 다른 게스트의 파기를 되돌리거나 막지 않고, 앞 게스트의 크루 양도가 끝난 뒤 다음
     * 게스트를 본다(코드 리뷰 HIGH).
     */
    public int withdrawIdleGuests(LocalDateTime now) {
        List<Long> ids =
                memberRepository.findIdleGuests(now.minus(GUEST_IDLE_LIMIT)).stream()
                        .map(Member::getId)
                        .toList();
        int count = 0;
        for (long id : ids) {
            try {
                Boolean done = transactions.execute(status -> withdrawIdleGuest(id, now));
                if (Boolean.TRUE.equals(done)) {
                    count++;
                }
            } catch (RuntimeException e) {
                log.error(
                        "idle_guest_withdrawal_failed memberId={} error={}",
                        id,
                        e.getClass().getSimpleName(),
                        e);
            }
        }
        log.info("idle_guests_withdrawn count={} candidates={}", count, ids.size());
        return count;
    }

    /** 한 게스트를 다시 읽어 아직 미이용 ACTIVE 게스트일 때만 파기한다. 파기했으면 참. */
    private boolean withdrawIdleGuest(long memberId, LocalDateTime now) {
        Optional<Member> found = memberRepository.findById(memberId);
        if (found.isEmpty()
                || !found.get().isGuest()
                || found.get().getStatus() != MemberStatus.ACTIVE
                || found.get().lastAccessAt().isAfter(now.minus(GUEST_IDLE_LIMIT))) {
            return false;
        }
        Optional<ReasonCode> skip = idleSkipReason(memberId);
        if (skip.isPresent()) {
            log.info("idle_guest_skipped memberId={} reason={}", memberId, skip.get());
            return false;
        }
        placementProgressRepository.findByIdForUpdate(memberId); // 잠금 순서 배치 진행 → 회원(리뷰 H1)
        Member guest = found.get();
        guest.withdraw(now);
        memberRepository.save(guest);
        placementProgressRepository.deleteByMemberId(memberId); // 배치 기록을 남기지 않는다(D-489)
        auditService.record(
                new AuditEntry(
                        IDLE_GUEST_ACTOR,
                        ActorRole.SYSTEM,
                        AuditAction.WITHDRAWAL,
                        String.valueOf(memberId),
                        null,
                        null));
        eventPublisher.publish(new MemberWithdrawn(memberId, now));
        return true;
    }

    private static TransactionOperations perGuestTransaction(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Optional<ReasonCode> idleSkipReason(long memberId) {
        for (IdleGuestWithdrawalGuard guard : idleGuards) {
            Optional<ReasonCode> reason = guard.check(memberId);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }
}
