package com.a207.runkk.battle;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크를 연 러너와 초대한 러너에게 초대 정보를 보인다(설계 5.6.2, D-224). 끝난 초대도 오류가 아니라 status·blockedReason으로 알리고, 없는
 * 토큰만 INVITE_NOT_FOUND다.
 */
@Service
@Transactional(readOnly = true)
public class FriendInviteQuery {

    private static final Logger log = LoggerFactory.getLogger(FriendInviteQuery.class);

    private final FriendInviteRepository friendInviteRepository;
    private final RunnerAvailability runnerAvailability;
    private final MemberQueryService memberQueryService;
    private final RunQueryService runQueryService;
    private final Clock clock;

    public FriendInviteQuery(
            FriendInviteRepository friendInviteRepository,
            RunnerAvailability runnerAvailability,
            MemberQueryService memberQueryService,
            RunQueryService runQueryService,
            Clock clock) {
        this.friendInviteRepository = friendInviteRepository;
        this.runnerAvailability = runnerAvailability;
        this.memberQueryService = memberQueryService;
        this.runQueryService = runQueryService;
        this.clock = clock;
    }

    /**
     * 링크 조회. 초대한 쪽 표시명·티어·그 거리 최고 기록과 본인 티어·최고 기록. blockedReason = refusalFor(본인·끝난 초대), 없고 본인이 바쁘면
     * ALREADY_IN_BATTLE, 아니면 null.
     */
    public FriendInviteView view(long memberId, String token) {
        FriendInvite invite =
                friendInviteRepository
                        .findByToken(token)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.INVITE_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        long inviterId = invite.getInviterId();
        StandardDistance distance = invite.getDistance();
        Map<Long, MemberSummary> members =
                memberQueryService.summaries(List.of(inviterId, memberId));
        MemberSummary inviter = members.get(inviterId);
        MemberSummary me = members.get(memberId);
        ReasonCode blocked =
                invite.refusalFor(memberId, now).orElseGet(() -> busyReason(memberId, now));
        return new FriendInviteView(
                invite.getId(),
                invite.statusAt(now),
                distance,
                invite.getExpiresAt(),
                inviter == null ? DisplayName.WITHDRAWN : inviter.displayName(),
                inviter == null ? Tier.UNRANKED : inviter.tier(),
                best(inviterId, distance),
                me == null ? Tier.UNRANKED : me.tier(),
                best(memberId, distance),
                blocked);
    }

    /**
     * 초대한 쪽 복구용(D-224): 열린 대기 초대(기한 전 PENDING). 링크를 다시 만들 수 있게 토큰을 담은 FriendInviteIssue로 돌려준다(설계의
     * FriendInviteView에는 토큰이 없다).
     */
    public Optional<FriendInviteIssue> mine(long memberId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return friendInviteRepository
                .findPending(memberId)
                .filter(invite -> invite.isOpen(now))
                .map(FriendInviteIssue::of);
    }

    private ReasonCode busyReason(long memberId, LocalDateTime now) {
        try {
            return runnerAvailability.isBusy(memberId, now) ? ReasonCode.ALREADY_IN_BATTLE : null;
        } catch (DataAccessException e) {
            log.warn("friend_invite_queue_unavailable error={}", e.getClass().getSimpleName());
            throw new BusinessRuleException(ReasonCode.QUEUE_UNAVAILABLE);
        }
    }

    private Duration best(long memberId, StandardDistance distance) {
        return runQueryService.best(memberId, distance).flatMap(BestEntry::time).orElse(null);
    }
}
