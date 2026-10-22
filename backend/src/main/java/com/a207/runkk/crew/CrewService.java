package com.a207.runkk.crew;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.TagOwner;
import com.a207.runkk.common.TagSet;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 생성·설정·양도·삭제 유스케이스(설계 6.1.7, srs.md 6.3 5.1). 권한은 자료를 바꾸기 전에 권한표로 검사한다(FR-128). 삭제 확인은 앱 화면이 받고
 * 서버는 확인된 요청만 받는다(D-36).
 */
@Service
public class CrewService {

    private static final String LEADER_TRANSFERRED_TEXT = "크루장을 넘겨받았어요";
    private static final String CREW_DELETED_TEXT = "크루가 삭제되었어요";

    private final CrewRepository crewRepository;
    private final CrewAccess crewAccess;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CrewDeletion crewDeletion;
    private final Clock clock;

    CrewService(
            CrewRepository crewRepository,
            CrewAccess crewAccess,
            InviteCodeGenerator inviteCodeGenerator,
            NotificationService notificationService,
            AuditService auditService,
            CrewDeletion crewDeletion,
            Clock clock) {
        this.crewRepository = crewRepository;
        this.crewAccess = crewAccess;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.crewDeletion = crewDeletion;
        this.clock = clock;
    }

    /**
     * JoinEligibility(소속·쿨타임) → CrewName·이름 중복 → TagSet(CREW) → 초대 코드 발급 → Crew.create(srs.md 6.3
     * 5.1.1). 이름이 동시에 겹치면 고유 인덱스가 막는다.
     */
    @Transactional
    public CrewCreateResult create(long memberId, CrewCreateCommand cmd) {
        LocalDateTime now = LocalDateTime.now(clock);
        JoinEligibility.evaluate(crewRepository.findMembershipHistory(memberId), now).require();
        CrewName name = CrewName.of(cmd.name());
        if (crewRepository.existsByName(name.value())) {
            throw new BusinessRuleException(ReasonCode.CREW_NAME_TAKEN);
        }
        CrewProfile profile =
                new CrewProfile(
                        name,
                        cmd.intro(),
                        TagSet.of(cmd.tags(), TagOwner.CREW),
                        cmd.visibility(),
                        cmd.joinPolicy(),
                        cmd.logoUrl());
        Crew crew =
                crewRepository.save(
                        Crew.create(memberId, profile, inviteCodeGenerator.issue(now), now));
        return new CrewCreateResult(
                crew.getId(), crew.inviteCode().code(), crew.inviteCode().expiresAt());
    }

    /** check(EDIT_CREW) → 이름 중복 → Crew.changeInfo. 대기 중 가입 신청은 그대로다(Q-36). */
    @Transactional
    public CrewResult changeInfo(long requesterId, long crewId, CrewInfoChange change) {
        Crew crew = crewAccess.checkForUpdate(requesterId, crewId, CrewAction.EDIT_CREW);
        if (change.name() != null
                && !change.name().equals(crew.name())
                && crewRepository.existsByName(change.name().value())) {
            throw new BusinessRuleException(ReasonCode.CREW_NAME_TAKEN);
        }
        crew.changeInfo(change);
        crewRepository.save(crew);
        return CrewResult.of(crewId);
    }

    /** check(EDIT_CREW) → 새 코드. 이전 코드는 바로 무효(FR-114). */
    @Transactional
    public CrewResult reissueInviteCode(long requesterId, long crewId) {
        Crew crew = crewAccess.checkForUpdate(requesterId, crewId, CrewAction.EDIT_CREW);
        crew.reissueInviteCode(inviteCodeGenerator.issue(LocalDateTime.now(clock)));
        crewRepository.save(crew);
        return CrewResult.withCode(crew);
    }

    /** check(TRANSFER_OR_DELETE) → 두 역할 교체 → 알림(LEADER_TRANSFERRED) → 감사(크루장양도)(FR-115). */
    @Transactional
    public CrewResult transferLeadership(long requesterId, long crewId, long newLeaderId) {
        Crew crew = crewAccess.checkForUpdate(requesterId, crewId, CrewAction.TRANSFER_OR_DELETE);
        crew.transferLeadership(requesterId, newLeaderId);
        crewRepository.save(crew);
        notify(newLeaderId, NotificationType.LEADER_TRANSFERRED, LEADER_TRANSFERRED_TEXT, crewId);
        auditService.record(
                new AuditEntry(
                        String.valueOf(requesterId),
                        ActorRole.CREW_LEADER,
                        AuditAction.LEADER_TRANSFER,
                        String.valueOf(newLeaderId),
                        String.valueOf(requesterId),
                        String.valueOf(newLeaderId)));
        return CrewResult.of(crewId);
    }

    /**
     * check(TRANSFER_OR_DELETE) → 크루원마다 알림(CREW_DELETED) → 크루·소속·대기 신청·게시글·주간 거리 삭제 →
     * 감사(크루삭제)(FR-116). 소속이 없어진 크루원은 쿨타임 없이 바로 다른 크루에 가입할 수 있다.
     */
    @Transactional
    public CrewResult delete(long requesterId, long crewId) {
        Crew crew = crewAccess.checkForUpdate(requesterId, crewId, CrewAction.TRANSFER_OR_DELETE);
        List<Long> members =
                crew.activeMemberIds().stream().filter(id -> id != requesterId).toList();
        crewDeletion.deleteAll(crewId);
        members.forEach(id -> notify(id, NotificationType.CREW_DELETED, CREW_DELETED_TEXT, crewId));
        auditService.record(
                new AuditEntry(
                        String.valueOf(requesterId),
                        ActorRole.CREW_LEADER,
                        AuditAction.CREW_DELETE,
                        String.valueOf(crewId),
                        crew.name().value(),
                        null));
        return CrewResult.of(crewId);
    }

    private void notify(long recipientId, NotificationType type, String text, long crewId) {
        notificationService.notify(
                new NotificationRequest(
                        recipientId,
                        type,
                        text,
                        new NavigationTarget(Screen.CREW_HOME, String.valueOf(crewId))));
    }
}
