package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.CrewRunRule;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunRecordDeleted;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.SavedRun;
import java.time.Clock;
import java.time.Duration;
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

    /**
     * check(RESPOND) → CrewRunPost.enterLobby(memberId, now, rule)(FR-147). 응답 없음·미정·불참이어도 입장하면
     * GOING이 되어 참가자다(D-152).
     */
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

    /** check(RESPOND) → CrewRunPost.toggleReady(memberId). */
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
                    crewAccess.check(memberId, post.crewId(), CrewAction.RESPOND);
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
            announcer.cancelled(post, outcome.memberIds(), rule.minReady());
        }
        if (post.shouldClose(now, rule)) {
            post.close(now, announcer.lastProgress(postId));
            announcer.ended(post);
        }
        crewPostRepository.save(post);
        return Boolean.TRUE;
    }

    /**
     * 크루런게시글ID가 있는 기록이 저장되면 linkRun(FR-146). 합산 방식이라 도착 판정은 없다. 커밋 뒤 새 트랜잭션에서 하고 시계와 충돌하는 등 일시 오류면
     * 다시 읽는다. 그래도 실패하면 로그만 남기고, 빠진 연결은 0시 일 단위 보정(CrewRunLinkReconcileService)이 relink로 채운다(M4).
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
                                .ifPresent(post -> link(post, SavedRun.of(e))));
    }

    /**
     * 기록이 삭제되면(v0.14 D-232) 그 크루런 참가의 기록 연결(linked_record_id)을 비운다. 크루런 ID가 없으면 아무 일도 없다. 집계 제외
     * 기록이라 크루 주간 거리는 바뀌지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunDeleted(RunRecordDeleted e) {
        if (e.crewRunPostId() == null) {
            return;
        }
        transactions.afterCommit(
                "crew_run_unlink",
                e.recordId(),
                () ->
                        crewPostRepository
                                .findCrewRunByIdForUpdate(e.crewRunPostId())
                                .ifPresent(
                                        post -> {
                                            if (post.unlinkRun(e.memberId(), e.recordId())) {
                                                crewPostRepository.save(post);
                                            }
                                        }));
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
                                .map(post -> link(post, run) ? 1 : 0)
                                .orElse(0),
                0);
    }

    private static boolean isUnlinked(CrewRunPost post, long memberId) {
        return post.participationOf(memberId).map(p -> p.linkedRecordId().isEmpty()).orElse(false);
    }

    /** 수신자(onRunSaved)와 보정(relink)이 같은 저장 기록 형식 SavedRun으로 부른다(코드 리뷰 3차 B-M3). */
    private boolean link(CrewRunPost post, SavedRun run) {
        boolean linked = post.linkRun(run.memberId(), run.recordId());
        crewPostRepository.save(post);
        return linked;
    }

    /**
     * 크루 삭제·모집글 삭제가 부른다(D-66, K-2). RUNNING이면 출발 참가자에게 CrewRunEnded, LOBBY_OPEN이면 대기실 입장자에게
     * CrewRunCancelled("크루런이 삭제되었어요"), 그 밖은 보내지 않는다. 세션을 지운다. 메시지는 삭제 트랜잭션 커밋 뒤에 나간다. 이미 저장된 개인 기록은
     * 지우지 않는다.
     */
    public void endByDeletion(CrewRunPost post) {
        announcer.deleted(post);
    }

    /**
     * 일시정지(합산 방식): RUNNING인 진행 참가자이고 아직 끝내지 않았는지 확인하고(CrewRunPost.checkPausable) 세션에 멈춤을 표시해 모두에게
     * 중계한다. DB에 쓰지 않는다. 재개는 정지 때보다 큰 경과의 진행값으로 세션이 판단한다.
     */
    @Transactional(readOnly = true)
    public void pause(long memberId, long postId, Duration elapsedAt) {
        load(postId).checkPausable(memberId);
        announcer.paused(postId, memberId, elapsedAt);
    }

    /**
     * 자기 크루런 끝내기(합산 방식, FR-192 대체). 앱이 보낸 마지막 거리·경과(없으면 세션의 최신 진행값)를 참여에 남기고 모두에게 "끝냄"을 중계한다. 이미
     * 끝냈으면 아무 일도 없다. 진행 참가자가 모두 끝내면 다음 1초 시계가 크루런을 닫는다.
     */
    public void quit(long memberId, long postId, FinalProgress reported) {
        if (reported != null) {
            reported.validate();
        }
        transactions.retryingRun(
                () -> {
                    CrewRunPost post = locked(postId);
                    LiveProgress last =
                            reported != null
                                    ? new LiveProgress(
                                            reported.distance(),
                                            reported.elapsed(),
                                            LocalDateTime.now(clock))
                                    : announcer.lastProgress(postId).get(memberId);
                    boolean ended =
                            post.end(
                                    memberId,
                                    last == null ? null : last.distance(),
                                    last == null ? null : last.elapsed());
                    if (!ended) {
                        return;
                    }
                    crewPostRepository.save(post);
                    if (post.status() == CrewRunStatus.RUNNING) {
                        announcer.ender(postId, memberId, last);
                    }
                });
    }

    /** check(VIEW_CREW_INTERNAL) → 크루 합산 거리, 참가자별 거리·시간·평균 페이스, 내 기록, 첫 크루런 여부, 크루 반영 거리 합. */
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
