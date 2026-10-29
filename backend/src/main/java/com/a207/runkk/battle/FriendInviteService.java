package com.a207.runkk.battle;

import com.a207.runkk.battle.live.FriendInviteDeclined;
import com.a207.runkk.battle.live.FriendInviteExpired;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettingService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 친구 대결 초대의 생성, 취소, 거절, 참가, 만료(설계 5.6.2, SQ-31·SQ-32, D-218~D-223). 바쁨 판정은 RunnerAvailability, 대결
 * 저장과 성사 알림은 BattleStartService.startFriend가 맡는다(v0.14 검증 D-299). 실시간 알림은 커밋 뒤에만 보낸다.
 */
@Service
public class FriendInviteService {

    /** 정리 작업 한 번에 만료할 초대 수(D-221). */
    static final int EXPIRE_BATCH = 100;

    private static final Logger log = LoggerFactory.getLogger(FriendInviteService.class);

    private final FriendInviteRepository friendInviteRepository;
    private final RunnerAvailability runnerAvailability;
    private final MemberQueryService memberQueryService;
    private final MemberSettingService memberSettingService;
    private final BattleStartService battleStartService;
    private final BattleMessenger battleMessenger;
    private final BattleTransactions transactions;
    private final Clock clock;

    public FriendInviteService(
            FriendInviteRepository friendInviteRepository,
            RunnerAvailability runnerAvailability,
            MemberQueryService memberQueryService,
            MemberSettingService memberSettingService,
            BattleStartService battleStartService,
            BattleMessenger battleMessenger,
            BattleTransactions transactions,
            Clock clock) {
        this.friendInviteRepository = friendInviteRepository;
        this.runnerAvailability = runnerAvailability;
        this.memberQueryService = memberQueryService;
        this.memberSettingService = memberSettingService;
        this.battleStartService = battleStartService;
        this.battleMessenger = battleMessenger;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 초대를 만든다(D-220). 검사 순서: 거리(1·3·5·10km, 아니면 BATTLE_DISTANCE_INVALID) → 위치
     * 동의(LOCATION_CONSENT_REQUIRED) → 기한 남은 대기 초대(INVITE_ALREADY_PENDING, 기한 지난 것은 EXPIRED로 바꾸고 진행)
     * → 바쁨(ALREADY_IN_BATTLE, 대기열 장애는 QUEUE_UNAVAILABLE). 저장 뒤 그 거리를 기본 대결 거리로 기억한다. 티어는 보지 않는다. 기한
     * 지난 자기 초대를 정리하는 작업과 같은 행을 두고 겹쳐 낙관적 잠금이 충돌하면 다시 읽어 한 번 더 한다(그 행은 이미 EXPIRED).
     */
    public FriendInviteIssue invite(long memberId, StandardDistance distance) {
        return transactions.retrying(() -> tryInvite(memberId, distance));
    }

    private FriendInviteIssue tryInvite(long memberId, StandardDistance distance) {
        if (distance == null || !distance.isRandomBattle()) {
            throw new BusinessRuleException(ReasonCode.BATTLE_DISTANCE_INVALID);
        }
        if (!memberQueryService.hasConsent(memberId, ConsentItem.LOCATION)) {
            throw new BusinessRuleException(ReasonCode.LOCATION_CONSENT_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<FriendInvite> pending = friendInviteRepository.findPending(memberId);
        if (pending.isPresent()) {
            FriendInvite previous = pending.get();
            if (previous.isOpen(now)) {
                throw new BusinessRuleException(ReasonCode.INVITE_ALREADY_PENDING);
            }
            previous.expireIfDue(now); // 정리 작업 전의 만료 초대가 부분 고유 인덱스로 새 초대를 막지 않게 한다
            friendInviteRepository.saveImmediately(previous);
        }
        if (queueing(() -> runnerAvailability.isBusy(memberId, now))) {
            // 바쁜 까닭이 그사이 커밋된 자기 초대면(동시 요청) 대기 초대 사유로 알린다(TC-9804)
            throw new BusinessRuleException(
                    friendInviteRepository.existsOpen(memberId, now)
                            ? ReasonCode.INVITE_ALREADY_PENDING
                            : ReasonCode.ALREADY_IN_BATTLE);
        }
        FriendInvite saved;
        try {
            saved =
                    friendInviteRepository.saveImmediately(
                            FriendInvite.open(memberId, distance, now));
        } catch (DataIntegrityViolationException racing) {
            if (!UniqueViolations.isUniqueViolation(racing)) {
                throw racing; // NOT NULL·CHECK 위반은 경합이 아니다(코드 리뷰 8차 BE8-5)
            }
            // 같은 회원의 동시 요청: uq_friend_invite_pending이 둘째를 막는다(TC-9804)
            throw new BusinessRuleException(ReasonCode.INVITE_ALREADY_PENDING);
        }
        memberSettingService.rememberBattleDistance(memberId, distance);
        log.info(
                "friend_invite_opened inviteId={} memberId={} distance={}",
                saved.getId(),
                memberId,
                distance);
        return FriendInviteIssue.of(saved);
    }

    /**
     * 초대한 사람의 취소(D-223). 없으면 INVITE_NOT_FOUND, 초대한 사람이 아니면 PermissionDeniedException, ACCEPTED면
     * INVITE_ALREADY_JOINED. 이미 끝난 초대는 그대로 성공(멱등).
     */
    public void cancel(long memberId, long inviteId) {
        transactions.retryingRun(
                () -> {
                    FriendInvite invite =
                            friendInviteRepository
                                    .findById(inviteId)
                                    .orElseThrow(
                                            () ->
                                                    new BusinessRuleException(
                                                            ReasonCode.INVITE_NOT_FOUND));
                    if (invite.getInviterId() != memberId) {
                        throw new PermissionDeniedException("초대한 사람만 취소할 수 있다");
                    }
                    if (invite.cancel(LocalDateTime.now(clock))) {
                        friendInviteRepository.save(invite);
                    }
                });
    }

    /**
     * 받는 사람의 거절(D-223). 없으면 INVITE_NOT_FOUND, 거부 사유가 있으면 그 코드. 커밋 뒤 초대한 쪽에 FriendInviteDeclined.
     */
    public void decline(long memberId, String token) {
        transactions.retryingRun(
                () -> {
                    FriendInvite invite = byToken(token);
                    invite.decline(memberId, LocalDateTime.now(clock));
                    friendInviteRepository.save(invite);
                    long inviterId = invite.getInviterId();
                    FriendInviteDeclined declined = new FriendInviteDeclined(invite.getId());
                    AfterCommit.run(() -> battleMessenger.send(inviterId, declined));
                });
    }

    /**
     * 참가(D-222). 한 트랜잭션에서: 초대 거부 사유 → 받는 쪽 위치 동의 → 받는 쪽 바쁨 → 초대한 쪽 대결·대기 확인(바쁘면 초대를 CANCELLED로 저장하고
     * INVITE_CANCELLED) → FriendInvite.accept → BattleStartService.startFriend → linkBattle. 낙관적 잠금
     * 충돌이나 활성 대결 칸 고유 제약 충돌로 다시 실행되면 다시 읽은 초대가 ACCEPTED라 INVITE_ALREADY_JOINED다. 돌려주는 값은 대결 ID.
     */
    public long accept(long memberId, String token) {
        AcceptOutcome outcome;
        try {
            outcome = transactions.retrying(() -> tryAccept(memberId, token));
        } catch (DataIntegrityViolationException racing) {
            if (!UniqueViolations.isUniqueViolation(racing)) {
                throw racing; // 고유 제약이 아닌 위반은 다시 실행하지 않는다(코드 리뷰 8차 BE8-5)
            }
            // 동시 수락: 늦은 쪽이 활성 대결 칸(V15) 고유 제약에 걸렸다. 다시 읽어 거부 사유를 정한다(TC-9803)
            outcome = transactions.retrying(() -> tryAccept(memberId, token));
        }
        if (outcome.refusal() != null) {
            throw new BusinessRuleException(outcome.refusal());
        }
        return outcome.battleId();
    }

    private AcceptOutcome tryAccept(long memberId, String token) {
        FriendInvite invite = byToken(token);
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<ReasonCode> refusal = invite.refusalFor(memberId, now);
        if (refusal.isPresent()) {
            throw new BusinessRuleException(refusal.get());
        }
        if (!memberQueryService.hasConsent(memberId, ConsentItem.LOCATION)) {
            throw new BusinessRuleException(ReasonCode.LOCATION_CONSENT_REQUIRED);
        }
        if (queueing(() -> runnerAvailability.isBusy(memberId, now))) {
            throw new BusinessRuleException(ReasonCode.ALREADY_IN_BATTLE);
        }
        if (queueing(() -> runnerAvailability.isInBattleOrQueue(invite.getInviterId()))) {
            invite.cancel(now); // 초대한 쪽이 그사이 다른 대결에 들어갔다. 취소를 커밋하고 거부한다
            friendInviteRepository.save(invite);
            log.info(
                    "friend_invite_cancelled_inviter_busy inviteId={} inviterId={}",
                    invite.getId(),
                    invite.getInviterId());
            return AcceptOutcome.refused(ReasonCode.INVITE_CANCELLED);
        }
        invite.accept(memberId, now);
        Battle battle =
                battleStartService.startFriend(invite, now); // 대결 저장, linkBattle, 성사 알림(D-299)
        friendInviteRepository.save(invite);
        log.info(
                "friend_invite_accepted inviteId={} battleId={} joinerId={}",
                invite.getId(),
                battle.getId(),
                memberId);
        return AcceptOutcome.started(battle.getId());
    }

    /**
     * 기한 지난 PENDING을 EXPIRED로 저장한다(D-221, FriendInviteExpiryJob이 5초마다). 초대마다 새 트랜잭션이고 한 건의 실패가 다른
     * 건을 막지 않는다. 커밋 뒤 초대한 쪽에 FriendInviteExpired. 만료한 수.
     */
    public int expireDue(LocalDateTime now) {
        AtomicInteger expired = new AtomicInteger();
        for (FriendInvite due : friendInviteRepository.findDuePending(now, EXPIRE_BATCH)) {
            long inviteId = due.getId();
            boolean done =
                    transactions.once(
                            "friend_invite_expire", () -> expireOne(inviteId, now, expired));
            if (!done) {
                log.warn("friend_invite_expire_failed inviteId={}", inviteId);
            }
        }
        return expired.get();
    }

    private void expireOne(long inviteId, LocalDateTime now, AtomicInteger expired) {
        FriendInvite invite = friendInviteRepository.findById(inviteId).orElse(null);
        if (invite == null || !invite.expireIfDue(now)) {
            return; // 그사이 참가·거절·취소됐다
        }
        friendInviteRepository.save(invite);
        long inviterId = invite.getInviterId();
        FriendInviteExpired message = new FriendInviteExpired(inviteId);
        AfterCommit.run(
                () -> {
                    expired.incrementAndGet();
                    battleMessenger.send(inviterId, message);
                });
    }

    /** 탈퇴 정리(D-223). 그 회원의 PENDING 초대가 있으면 취소한다. */
    public void cancelOpenOf(long memberId) {
        transactions.once(
                "friend_invite_withdrawal",
                () ->
                        friendInviteRepository
                                .findPending(memberId)
                                .ifPresent(
                                        invite -> {
                                            if (invite.cancel(LocalDateTime.now(clock))) {
                                                friendInviteRepository.save(invite);
                                            }
                                        }));
    }

    /** 탈퇴 파기: 그 회원이 보낸 초대와 참가한 초대 행을 지운다(대결 기록은 남는다). 실패하면 로그만 남긴다. */
    public void eraseOf(long memberId) {
        transactions.once(
                "friend_invite_erase", () -> friendInviteRepository.deleteByMember(memberId));
    }

    private FriendInvite byToken(String token) {
        return friendInviteRepository
                .findByToken(token)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.INVITE_NOT_FOUND));
    }

    /** 대기열 저장소 장애(5.5 장애: Redis)는 "잠시 후 다시 시도"로 바꾼다. */
    private static <T> T queueing(Supplier<T> call) {
        try {
            return call.get();
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (DataAccessException e) {
            log.warn("friend_invite_queue_unavailable error={}", e.getClass().getSimpleName());
            throw new BusinessRuleException(ReasonCode.QUEUE_UNAVAILABLE);
        }
    }

    /** 참가 시도 결과. 초대한 쪽이 바빠 취소를 커밋한 경우만 refusal이 있다. */
    private record AcceptOutcome(Long battleId, ReasonCode refusal) {
        static AcceptOutcome started(long battleId) {
            return new AcceptOutcome(battleId, null);
        }

        static AcceptOutcome refused(ReasonCode refusal) {
            return new AcceptOutcome(null, refusal);
        }
    }
}
