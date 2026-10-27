package com.a207.runkk.crew;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 회원의 crew 자료를 정리한다(설계 6.1.7, FR-021, D-28): 소속·신청 행 삭제, 혼자 남은 크루 삭제, 게시글 작성자·주간 기여자·크루런 참가자
 * 익명화, 그 회원이 쓴 게시글의 사진 삭제, 투표 응답·대면 모임 참석 행 삭제(D-100, D-117). 일반 탈퇴는 CrewLeaderGuard가 크루원이 있는 크루의
 * 크루장을 막고(FR-024), 미이용 게스트 파기(FR-200)는 가드를 보지 않으므로 크루원이 남은 크루의 크루장이면 운영진(없으면 크루원) 중 가입일시가 가장 이른
 * 사람(같으면 회원 ID가 작은 쪽)에게 넘기고 감사(LEADER_TRANSFER, SYSTEM)·알림(LEADER_TRANSFERRED)을 남긴다(D-118, D-165).
 */
@Component
public class CrewWithdrawalListener {

    private static final Logger log = LoggerFactory.getLogger(CrewWithdrawalListener.class);

    private final CrewRepository crewRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final CrewPostRepository crewPostRepository;
    private final PostPhotoStore postPhotoStore;
    private final WeeklyRaceRepository weeklyRaceRepository;
    private final CrewDeletion crewDeletion;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** 자동 양도 감사의 행위자 ID(D-118). */
    static final String SYSTEM_ACTOR = "system:leader-auto-transfer";

    private static final String LEADER_TRANSFERRED_TEXT = "크루장을 넘겨받았어요";

    CrewWithdrawalListener(
            CrewRepository crewRepository,
            JoinRequestRepository joinRequestRepository,
            CrewPostRepository crewPostRepository,
            PostPhotoStore postPhotoStore,
            WeeklyRaceRepository weeklyRaceRepository,
            CrewDeletion crewDeletion,
            AuditService auditService,
            NotificationService notificationService) {
        this.crewRepository = crewRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.crewPostRepository = crewPostRepository;
        this.postPhotoStore = postPhotoStore;
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.crewDeletion = crewDeletion;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        long memberId = e.memberId();
        joinRequestRepository.deleteByMember(memberId);
        crewRepository.findMembershipHistory(memberId).stream()
                .map(CrewMembership::crewId)
                .distinct()
                .toList()
                .forEach(crewId -> leaveCrew(crewId, memberId));
        // 투표 응답·대면 모임 참석(FR-021, D-117)은 글을 영속성 문맥에 올리기 전에 SQL로 지운다
        crewPostRepository.deleteResponsesByMember(memberId);
        crewPostRepository
                .findByAuthor(memberId)
                .forEach(
                        post -> {
                            post.anonymizeAuthor();
                            crewPostRepository.save(post);
                            if (post instanceof BoardPost) {
                                postPhotoStore.deleteByPost(post.getId()); // D-100 (5)
                            }
                        });
        crewPostRepository
                .findCrewRunsOf(
                        memberId, new DateRange(CrewRunViews.SERVICE_START, e.at().toLocalDate()))
                .forEach(
                        post -> {
                            post.anonymizeParticipant(memberId);
                            crewPostRepository.save(post);
                        });
        weeklyRaceRepository.anonymizeMember(memberId);
    }

    /** 혼자 남은 크루면 크루를 지우고, 아니면 그 회원의 멤버십 행만 지운다. */
    private void leaveCrew(long crewId, long memberId) {
        crewRepository.findByIdForUpdate(crewId).ifPresent(crew -> leaveCrew(crew, memberId));
    }

    /** 운영진(없으면 크루원) 중 가입일시가 가장 이른 사람, 같으면 회원 ID가 작은 쪽(D-118, D-165). */
    static long successorOf(Crew crew, long leaderId) {
        Comparator<CrewMembership> order =
                Comparator.comparing((CrewMembership m) -> m.role() != CrewRole.MANAGER)
                        .thenComparing(CrewMembership::joinedAt)
                        .thenComparingLong(CrewMembership::memberId);
        return crew.activeMembers().stream()
                .filter(m -> m.memberId() != leaderId)
                .min(order)
                .map(CrewMembership::memberId)
                .orElseThrow(() -> new IllegalStateException("넘겨받을 크루원이 없다"));
    }

    private void leaveCrew(Crew crew, long memberId) {
        boolean leader = crew.relationOf(memberId) == CrewRelation.LEADER;
        if (leader && crew.headcount() == 1) {
            crewDeletion.deleteAll(crew.getId());
            return;
        }
        if (leader) {
            long successor = successorOf(crew, memberId);
            crew.transferLeadership(memberId, successor);
            log.info(
                    "crew_leader_auto_transferred crewId={} newLeader={}", crew.getId(), successor);
            crew.removeMember(memberId);
            crewRepository.save(crew);
            auditService.record(
                    new AuditEntry(
                            SYSTEM_ACTOR,
                            ActorRole.SYSTEM,
                            AuditAction.LEADER_TRANSFER,
                            String.valueOf(successor),
                            String.valueOf(memberId),
                            String.valueOf(successor)));
            notificationService.notify(
                    new NotificationRequest(
                            successor,
                            NotificationType.LEADER_TRANSFERRED,
                            LEADER_TRANSFERRED_TEXT,
                            new NavigationTarget(Screen.CREW_HOME, String.valueOf(crew.getId()))));
            return;
        }
        crew.removeMember(memberId);
        crewRepository.save(crew);
    }
}
