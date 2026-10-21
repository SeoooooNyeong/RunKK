package com.a207.runkk.crew;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·탈퇴·퇴장 유스케이스(설계 6.1.7, SQ-08, srs.md 6.3 5.3). 정원은 크루 행을 잠근 뒤(SELECT … FOR UPDATE) Crew.admit이
 * 검사하고, 1인 1크루·같은 크루 대기 중 신청 하나는 부분 고유 인덱스가 한 번 더 막는다(5.3).
 */
@Service
public class MembershipService {

    /** 소멸 사유(FR-142). */
    static final String DISSOLVED_FULL = "정원 초과";

    static final String DISSOLVED_OTHER_CREW = "다른 크루 가입";

    private final CrewRepository crewRepository;
    private final CrewAccess crewAccess;
    private final JoinRequestRepository joinRequestRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    MembershipService(
            CrewRepository crewRepository,
            CrewAccess crewAccess,
            JoinRequestRepository joinRequestRepository,
            NotificationService notificationService,
            AuditService auditService,
            Clock clock) {
        this.crewRepository = crewRepository;
        this.crewAccess = crewAccess;
        this.joinRequestRepository = joinRequestRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * SQ-08. JoinEligibility → 크루 잠금 → admissionFor → IMMEDIATE면 admit, REQUEST면 대기 중 신청 확인 뒤
     * JoinRequest + 알림 (JOIN_REQUESTED). 크루가 없거나 코드가 틀리면 INVALID_CODE, 정원이면 CREW_FULL.
     */
    @Transactional
    public JoinResult apply(long memberId, JoinTarget target) {
        LocalDateTime now = LocalDateTime.now(clock);
        JoinEligibility.evaluate(crewRepository.findMembershipHistory(memberId), now).require();
        Crew crew = lockTarget(target);
        Admission admission = crew.admissionFor(target, now);
        if (admission == Admission.INVALID_CODE) {
            throw new BusinessRuleException(ReasonCode.INVALID_CODE);
        }
        if (admission == Admission.FULL) {
            throw new BusinessRuleException(ReasonCode.CREW_FULL);
        }
        if (admission == Admission.IMMEDIATE) {
            crew.admit(memberId, now);
            crewRepository.save(crew);
            return new JoinResult(JoinResult.Status.JOINED, crew.getId(), null);
        }
        if (joinRequestRepository.existsPending(crew.getId(), memberId)) {
            throw new BusinessRuleException(ReasonCode.ALREADY_REQUESTED);
        }
        JoinRequest request =
                joinRequestRepository.save(new JoinRequest(crew.getId(), memberId, now));
        notify(
                crew.leaderId(),
                NotificationType.JOIN_REQUESTED,
                "새 가입 신청이 있어요",
                at(Screen.CREW_MANAGE, crew.getId()));
        return new JoinResult(JoinResult.Status.PENDING, crew.getId(), request.getId());
    }

    private Crew lockTarget(JoinTarget target) {
        Optional<Long> crewId =
                target.code()
                        .map(code -> crewRepository.findByInviteCode(code).map(Crew::getId))
                        .orElse(Optional.ofNullable(target.crewId()));
        return crewId.flatMap(crewRepository::findByIdForUpdate)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.INVALID_CODE));
    }

    /** 본인 대기 중 신청만 CANCELLED(FR-143). */
    @Transactional
    public JoinResult cancel(long memberId, long requestId) {
        JoinRequest request = requireRequest(requestId);
        request.cancel(memberId, LocalDateTime.now(clock));
        joinRequestRepository.save(request);
        return new JoinResult(JoinResult.Status.CANCELLED, request.crewId(), requestId);
    }

    /**
     * check(VIEW_OR_DECIDE_JOIN) → 크루 잠금 → 정원·소속 재확인 → admit + approve 또는 dissolve(사유) →
     * 알림(JOIN_DECIDED). 처리된 신청이면 REQUEST_DECIDED.
     */
    @Transactional
    public DecisionResult approveJoin(long requesterId, long requestId) {
        JoinRequest request = requireRequest(requestId);
        Crew crew =
                crewAccess.checkForUpdate(
                        requesterId, request.crewId(), CrewAction.VIEW_OR_DECIDE_JOIN);
        requirePending(request);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<String> dissolved = dissolveReason(crew, request.memberId(), now);
        if (dissolved.isPresent()) {
            request.dissolve(dissolved.get(), now);
        } else {
            crew.admit(request.memberId(), now);
            request.approve(now);
            crewRepository.save(crew);
        }
        joinRequestRepository.save(request);
        String text = dissolved.map(r -> "가입 신청이 소멸되었어요(" + r + ")").orElse("가입이 승인되었어요");
        notify(
                request.memberId(),
                NotificationType.JOIN_DECIDED,
                text,
                at(Screen.CREW_HOME, crew.getId()));
        return new DecisionResult(requestId, request.status(), dissolved.orElse(null));
    }

    private Optional<String> dissolveReason(Crew crew, long applicantId, LocalDateTime now) {
        if (crew.isFull()) {
            return Optional.of(DISSOLVED_FULL);
        }
        if (!JoinEligibility.evaluate(crewRepository.findMembershipHistory(applicantId), now)
                .isEligible()) {
            return Optional.of(DISSOLVED_OTHER_CREW);
        }
        return Optional.empty();
    }

    /** check(VIEW_OR_DECIDE_JOIN) → JoinRequest.reject → 알림(JOIN_DECIDED). */
    @Transactional
    public DecisionResult rejectJoin(long requesterId, long requestId) {
        JoinRequest request = requireRequest(requestId);
        crewAccess.check(requesterId, request.crewId(), CrewAction.VIEW_OR_DECIDE_JOIN);
        requirePending(request);
        request.reject(LocalDateTime.now(clock));
        joinRequestRepository.save(request);
        notify(
                request.memberId(),
                NotificationType.JOIN_DECIDED,
                "가입 신청이 거절되었어요",
                at(Screen.CREW_HOME, request.crewId()));
        return new DecisionResult(requestId, request.status(), null);
    }

    /**
     * Crew.release(WITHDREW). 크루원이 남은 크루장은 LEADER_MUST_TRANSFER, 혼자인 크루장은
     * CREW_DELETE_REQUIRED(5.3.3).
     */
    @Transactional
    public CrewResult leave(long memberId, long crewId) {
        Crew crew = crewAccess.requireForUpdate(crewId);
        crew.release(memberId, LeaveReason.WITHDREW, LocalDateTime.now(clock));
        crewRepository.save(crew);
        return CrewResult.of(crewId);
    }

    /**
     * check(KICK) → release(KICKED) → 알림(KICKED) → 감사(강제퇴장). 본인이나 크루원이 아닌 대상은
     * TARGET_INVALID(FR-127).
     */
    @Transactional
    public CrewResult kick(long requesterId, long crewId, long targetId) {
        Crew crew = crewAccess.checkForUpdate(requesterId, crewId, CrewAction.KICK);
        crew.release(targetId, LeaveReason.KICKED, LocalDateTime.now(clock));
        crewRepository.save(crew);
        notify(targetId, NotificationType.KICKED, "크루에서 내보내졌어요", at(Screen.CREW_HOME, crewId));
        auditService.record(
                new AuditEntry(
                        String.valueOf(requesterId),
                        ActorRole.CREW_LEADER,
                        AuditAction.KICK,
                        String.valueOf(targetId),
                        String.valueOf(crewId),
                        null));
        return CrewResult.of(crewId);
    }

    /** 본인 소속 행만 바꾼다. 소속이 아니면 NOT_CREW_MEMBER(5.5.3, Q-43). */
    @Transactional
    public CrewResult editCrewProfile(long memberId, long crewId, CrewProfileEdit edit) {
        Crew crew = crewAccess.require(crewId);
        CrewMembership membership =
                crew.membershipOf(memberId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.NOT_CREW_MEMBER));
        membership.editCrewProfile(edit.intro(), edit.tags());
        crewRepository.save(crew);
        return CrewResult.of(crewId);
    }

    /**
     * 7일 지난 PENDING을 expire → 알림(JOIN_EXPIRED). batch.crew의 JoinRequestExpiryJob이 3시에 부른다(D-39).
     */
    @Transactional
    public int expireRequests(LocalDateTime now) {
        List<JoinRequest> due =
                joinRequestRepository.findPendingOlderThan(now.minusDays(JoinRequest.EXPIRY_DAYS));
        int expired = 0;
        for (JoinRequest request : due) {
            if (request.expire(now)) {
                joinRequestRepository.save(request);
                notify(
                        request.memberId(),
                        NotificationType.JOIN_EXPIRED,
                        "가입 신청이 7일 동안 처리되지 않아 만료되었어요",
                        at(Screen.CREW_HOME, request.crewId()));
                expired++;
            }
        }
        return expired;
    }

    private JoinRequest requireRequest(long requestId) {
        return joinRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.JOIN_REQUEST_NOT_FOUND));
    }

    private static void requirePending(JoinRequest request) {
        if (!request.isPending()) {
            throw new BusinessRuleException(ReasonCode.REQUEST_DECIDED);
        }
    }

    private void notify(
            long recipientId, NotificationType type, String text, NavigationTarget target) {
        notificationService.notify(new NotificationRequest(recipientId, type, text, target));
    }

    private static NavigationTarget at(Screen screen, long crewId) {
        return new NavigationTarget(screen, String.valueOf(crewId));
    }
}
