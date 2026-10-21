package com.a207.runkk.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 탈퇴한 회원의 crew 자료를 정리한다(설계 6.1.7, FR-021, D-28): 소속·신청 행 삭제, 혼자 남은 크루 삭제, 게시글 작성자·주간 기여자·크루런 참가자
 * 익명화. 크루원이 있는 크루의 크루장은 CrewLeaderGuard가 탈퇴를 막는다(FR-024).
 */
@Component
public class CrewWithdrawalListener {

    private final CrewRepository crewRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final CrewPostRepository crewPostRepository;
    private final WeeklyRaceRepository weeklyRaceRepository;
    private final CrewDeletion crewDeletion;

    CrewWithdrawalListener(
            CrewRepository crewRepository,
            JoinRequestRepository joinRequestRepository,
            CrewPostRepository crewPostRepository,
            WeeklyRaceRepository weeklyRaceRepository,
            CrewDeletion crewDeletion) {
        this.crewRepository = crewRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.crewPostRepository = crewPostRepository;
        this.weeklyRaceRepository = weeklyRaceRepository;
        this.crewDeletion = crewDeletion;
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
        crewPostRepository
                .findByAuthor(memberId)
                .forEach(
                        post -> {
                            post.anonymizeAuthor();
                            crewPostRepository.save(post);
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
        crewRepository
                .findByIdForUpdate(crewId)
                .ifPresent(
                        crew -> {
                            boolean alone =
                                    crew.membershipOf(memberId).isPresent()
                                            && crew.headcount() == 1;
                            if (alone) {
                                crewDeletion.deleteAll(crewId);
                            } else {
                                crew.removeMember(memberId);
                                crewRepository.save(crew);
                            }
                        });
    }
}
