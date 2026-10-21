package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.CrewRunRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.SavedRun;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 크루런 대기실·출발·결과 유스케이스(설계 6.1.7, SQ-09, srs.md 6.3 5.7). 모집글은 낙관적 잠금이라 1초 시계·러닝 연결·그만두기가 겹치면 다시 읽어
 * 재시도한다(5.3). 실시간 메시지는 커밋 뒤에 보낸다.
 */
@Service
public class CrewRunService {

    private final CrewPostRepository crewPostRepository;
    private final CrewAccess crewAccess;
    private final CrewRunAnnouncer announcer;
    private final CrewRunViews views;
    private final OperationPolicy operationPolicy;
    private final CrewTransactions transactions;
    private final Clock clock;

    CrewRunService(
            CrewPostRepository crewPostRepository,
            CrewAccess crewAccess,
            CrewRunAnnouncer announcer,
            CrewRunViews views,
            OperationPolicy operationPolicy,
            CrewTransactions transactions,
            Clock clock) {
        this.crewPostRepository = crewPostRepository;
        this.crewAccess = crewAccess;
        this.announcer = announcer;
        this.views = views;
        this.operationPolicy = operationPolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** check(VOTE_OR_JOIN_CREW_RUN) → CrewRunPost.enterLobby(memberId, now, rule)(FR-147). */
    public LobbyView enterLobby(long memberId, long postId) {
        LocalDateTime now = LocalDateTime.now(clock);
        CrewRunRule rule = operationPolicy.crewRunRule();
        return lobbyChange(
                memberId,
                postId,
                post -> {
                    post.enterLobby(memberId, now, rule);
                    return post;
                });
    }

    /** check(VOTE_OR_JOIN_CREW_RUN) → CrewRunPost.toggleReady(memberId). */
    public LobbyView toggleReady(long memberId, long postId) {
        return lobbyChange(
                memberId,
                postId,
                post -> {
                    post.toggleReady(memberId);
                    return post;
                });
    }

    /** CrewRunPost.leaveLobby(memberId). */
    public LobbyView leaveLobby(long memberId, long postId) {
        return lobbyChange(
                memberId,
                postId,
                post -> {
                    post.leaveLobby(memberId);
                    return post;
                });
    }

    private LobbyView lobbyChange(
            long memberId, long postId, Function<CrewRunPost, CrewRunPost> change) {
        return transactions.retrying(
                () -> {
                    CrewRunPost post = locked(postId);
                    crewAccess.check(memberId, post.crewId(), CrewAction.VOTE_OR_JOIN_CREW_RUN);
                    crewPostRepository.save(change.apply(post));
                    return views.lobby(post, LocalDateTime.now(clock));
                });
    }

    /**
     * 1초. 크루런마다 따로: openLobby, 10분 전 알림(CREW_RUN_SOON, 한 번), depart → CrewRunStart·CrewRunCancelled
     * + 세션 생성, shouldClose → close → CrewRunEnded + 세션 삭제. 한 크루런의 실패는 다음 tick에 다시 본다.
     */
    public void tick(LocalDateTime now) {
        List<Long> due =
                transactions.read(
                        () ->
                                crewPostRepository.findActiveCrewRuns(now).stream()
                                        .map(CrewPost::getId)
                                        .toList());
        CrewRunRule rule = operationPolicy.crewRunRule();
        for (long postId : due) {
            transactions.once("crew_run_tick", () -> step(postId, now, rule), Boolean.FALSE);
        }
        announcer.sweep(due);
    }

    private Boolean step(long postId, LocalDateTime now, CrewRunRule rule) {
        CrewRunPost post = locked(postId);
        announcer.ensureSession(post);
        post.openLobby(now, rule);
        if (post.markReminded(now, rule)) {
            announcer.remind(post);
        }
        DepartureOutcome outcome = post.depart(now, rule);
        if (outcome.kind() == DepartureOutcome.Kind.STARTED) {
            announcer.started(post, outcome);
        } else if (outcome.kind() == DepartureOutcome.Kind.CANCELLED) {
            announcer.cancelled(post, outcome.memberIds());
        }
        if (post.shouldClose(now, rule)) {
            post.close(now);
            announcer.ended(post);
        }
        crewPostRepository.save(post);
        return Boolean.TRUE;
    }

    /**
     * 크루런게시글ID가 있는 기록이 저장되면 linkRun(FR-146), 기록이 크루런 거리에 닿았으면 recordArrival(startedAt +
     * timeToReach)(FR-191). 커밋 뒤 새 트랜잭션에서 하고 시계와 충돌하는 등 일시 오류면 다시 읽는다. 그래도 실패하면 로그만 남기고, 빠진 연결은 0시
     * 일 단위 보정(CrewRunLinkReconcileService)이 relink로 채운다(M4).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (e.crewRunPostId() == null) {
            return;
        }
        transactions.afterCommit(
                "crew_run_link",
                e.recordId(),
                () ->
                        crewPostRepository
                                .findCrewRunByIdForUpdate(e.crewRunPostId())
                                .ifPresent(
                                        post ->
                                                link(
                                                        post,
                                                        e.memberId(),
                                                        e.recordId(),
                                                        e.startedAt(),
                                                        e.distance())));
    }

    /**
     * 보정 한 건: 그 회원의 참여에 아직 연결이 없을 때만 수신자와 같은 link를 한다. 새로 이었으면 1, 이미 이었거나 크루런·참여가 없으면 0. 한 건의 실패는
     * 로그만 남기고 0이다(CrewRunLinkReconcileService가 부른다).
     */
    int relink(SavedRun run) {
        return transactions.once(
                "crew_run_link_reconcile",
                () ->
                        crewPostRepository
                                .findCrewRunByIdForUpdate(run.crewRunPostId())
                                .filter(post -> isUnlinked(post, run.memberId()))
                                .map(
                                        post ->
                                                link(
                                                                post,
                                                                run.memberId(),
                                                                run.recordId(),
                                                                run.startedAt(),
                                                                run.distance())
                                                        ? 1
                                                        : 0)
                                .orElse(0),
                0);
    }

    private static boolean isUnlinked(CrewRunPost post, long memberId) {
        return post.participationOf(memberId).map(p -> p.linkedRecordId().isEmpty()).orElse(false);
    }

    private boolean link(
            CrewRunPost post,
            long memberId,
            String recordId,
            LocalDateTime startedAt,
            Distance distance) {
        boolean linked = post.linkRun(memberId, recordId);
        if (distance.isAtLeast(post.distance().distance())) {
            views.arrivalAt(recordId, startedAt, post.distance().distance())
                    .ifPresent(at -> post.recordArrival(memberId, at));
        }
        crewPostRepository.save(post);
        return linked;
    }

    /** CrewRunPost.quit → 세션 markQuit → 다른 참가자에게 "그만둠"(FR-192). */
    public void quit(long memberId, long postId) {
        transactions.retryingRun(
                () -> {
                    CrewRunPost post = locked(postId);
                    post.quit(memberId, LocalDateTime.now(clock));
                    crewPostRepository.save(post);
                    announcer.quitter(postId, memberId);
                });
    }

    /** check(VIEW_CREW_INTERNAL) → 순위별 표시명·기록·평균 페이스, 첫 크루런 여부, 크루 반영 거리 합(FR-191). */
    @Transactional(readOnly = true)
    public CrewRunResultView result(long memberId, long postId) {
        CrewRunPost post = load(postId);
        crewAccess.check(memberId, post.crewId(), CrewAction.VIEW_CREW_INTERNAL);
        return views.result(post, memberId);
    }

    private CrewRunPost load(long postId) {
        return crewPostRepository
                .findCrewRunById(postId)
                .filter(CrewPost::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
    }

    /** 쓰기 경로: 모집글 행을 잠가 읽는다(참가자 10명의 동시 러닝 연결·대기실 입장이 재시도 3회를 넘기지 않게). */
    private CrewRunPost locked(long postId) {
        return crewPostRepository
                .findCrewRunByIdForUpdate(postId)
                .filter(CrewPost::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.POST_NOT_FOUND));
    }
}
