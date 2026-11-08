package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.crew.live.CrewRunCancelled;
import com.a207.runkk.crew.live.CrewRunEnded;
import com.a207.runkk.crew.live.CrewRunStart;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 크루런 시계가 알릴 것을 보낸다(CrewRunService.tick·quit의 알림·실시간 메시지·세션 관리, SQ-09). 실시간 메시지와 세션 생성·삭제는 커밋 뒤에
 * 한다(AfterCommit). CrewRunService의 생성자 의존을 Fan-Out 7 안에 두려고 뗀 패키지 전용 보조다.
 */
@Component
class CrewRunAnnouncer {

    /** 준비 완료 2명 미만 취소 문구(srs.md 6.3 5.7.2). */
    static final String CANCELLED_REASON = "함께 달릴 크루원이 없어 취소됨";

    /** 대기실에서 모집글·크루가 지워졌을 때 문구(D-66). */
    static final String DELETED_REASON = "크루런이 삭제되었어요";

    private static final String SOON_TEXT = "곧 크루런이 시작돼요. 대기실이 열렸어요";

    private final CrewRunMessenger crewRunMessenger;
    private final CrewLiveSessionStore crewLiveSessionStore;
    private final CrewRunLiveService crewRunLiveService;
    private final MemberQueryService memberQueryService;
    private final NotificationService notificationService;

    CrewRunAnnouncer(
            CrewRunMessenger crewRunMessenger,
            CrewLiveSessionStore crewLiveSessionStore,
            CrewRunLiveService crewRunLiveService,
            MemberQueryService memberQueryService,
            NotificationService notificationService) {
        this.crewRunMessenger = crewRunMessenger;
        this.crewLiveSessionStore = crewLiveSessionStore;
        this.crewRunLiveService = crewRunLiveService;
        this.memberQueryService = memberQueryService;
        this.notificationService = notificationService;
    }

    /** 10분 전 알림(CREW_RUN_SOON): 갈래요 참가자에게 대기실 안내(FR-193). */
    void remind(CrewRunPost post) {
        NavigationTarget target =
                new NavigationTarget(Screen.CREW_RUN_RECRUIT, String.valueOf(post.getId()));
        post.participantsToRemind()
                .forEach(
                        id ->
                                notificationService.notify(
                                        new NotificationRequest(
                                                id,
                                                NotificationType.CREW_RUN_SOON,
                                                SOON_TEXT,
                                                target)));
    }

    /** 출발: 진행 참가자에게 CrewRunStart, 세션을 연다(커밋 뒤). */
    void started(CrewRunPost post, DepartureOutcome outcome) {
        Map<Long, DisplayName> names = namesOf(outcome.memberIds());
        CrewRunStart start =
                new CrewRunStart(
                        post.getId(),
                        outcome.startAt(),
                        outcome.memberIds().stream().map(names::get).toList());
        CrewRunLiveSession session =
                CrewRunLiveSession.open(post.getId(), names, outcome.startAt());
        AfterCommit.run(() -> crewLiveSessionStore.save(session));
        AfterCommit.run(() -> crewRunMessenger.sendAll(outcome.memberIds(), start));
    }

    /** 취소: 갈래요 참가자에게 CrewRunCancelled(커밋 뒤). */
    void cancelled(CrewRunPost post, List<Long> recipients) {
        CrewRunCancelled cancelled = new CrewRunCancelled(post.getId(), CANCELLED_REASON);
        AfterCommit.run(() -> crewRunMessenger.sendAll(recipients, cancelled));
    }

    /** 종료: 진행 참가자에게 CrewRunEnded, 세션 삭제(커밋 뒤). */
    void ended(CrewRunPost post) {
        long postId = post.getId();
        List<Long> runners = post.runners();
        AfterCommit.run(
                () -> {
                    crewLiveSessionStore.delete(postId);
                    crewRunLiveService.forget(postId);
                });
        AfterCommit.run(() -> crewRunMessenger.sendAll(runners, new CrewRunEnded(postId)));
    }

    /**
     * 모집글·크루 삭제(D-66, K-2): RUNNING이면 출발 참가자에게 CrewRunEnded, LOBBY_OPEN이면 대기실 입장자에게
     * CrewRunCancelled(삭제 문구), 그 밖은 보내지 않는다. 세션은 지운다. 모두 커밋 뒤다. 참가자 기기의 측정은 멈추지 않는다.
     */
    void deleted(CrewRunPost post) {
        long postId = post.getId();
        List<Long> recipients =
                switch (post.status()) {
                    case RUNNING -> post.runners();
                    case LOBBY_OPEN ->
                            post.participations().stream()
                                    .filter(p -> p.readiness() != Readiness.NOT_ENTERED)
                                    .flatMap(p -> p.memberId().stream())
                                    .toList();
                    default -> List.of();
                };
        AfterCommit.run(
                () -> {
                    crewLiveSessionStore.delete(postId);
                    crewRunLiveService.forget(postId);
                });
        if (recipients.isEmpty()) {
            return;
        }
        CrewRunMessage message =
                post.status() == CrewRunStatus.RUNNING
                        ? new CrewRunEnded(postId)
                        : new CrewRunCancelled(postId, DELETED_REASON);
        AfterCommit.run(() -> crewRunMessenger.sendAll(recipients, message));
    }

    /** 진행 중인데 세션이 없으면(출발 커밋 뒤 Redis 저장 실패, Redis 재시작) 진행 참가자로 다시 연다. 1초 시계가 RUNNING 크루런마다 부른다. */
    void ensureSession(CrewRunPost post) {
        if (post.status() != CrewRunStatus.RUNNING
                || crewLiveSessionStore.load(post.getId()).isPresent()) {
            return;
        }
        List<Long> runners = post.runners();
        crewLiveSessionStore.save(
                CrewRunLiveSession.open(
                        post.getId(), namesOf(runners), post.departedAt().orElseThrow()));
    }

    /** 시계가 보는 크루런이 아닌데 남은 세션(삭제된 글·크루)을 지운다. */
    void sweep(Collection<Long> activePostIds) {
        crewLiveSessionStore.activePostIds().stream()
                .filter(id -> !activePostIds.contains(id))
                .forEach(
                        id -> {
                            crewLiveSessionStore.delete(id);
                            crewRunLiveService.forget(id);
                        });
    }

    /** 일시정지: 세션 표시 → 모든 참가자에게 중계(커밋 뒤, 합산 방식). */
    void paused(long postId, long memberId, Duration elapsedAt) {
        AfterCommit.run(() -> crewRunLiveService.markPaused(memberId, postId, elapsedAt));
    }

    /** 끝냄: 세션 표시(마지막 값) → 모든 참가자에게 중계(커밋 뒤, 합산 방식). */
    void ender(long postId, long memberId, LiveProgress last) {
        AfterCommit.run(() -> crewRunLiveService.markEnded(memberId, postId, last));
    }

    /** 참가자별 최신 진행값(종료 때 아직 끝내지 않은 참가자를 마감할 값, 1초 시계가 종료 직전에 읽는다). */
    Map<Long, LiveProgress> lastProgress(long postId) {
        return crewRunLiveService.latest(postId);
    }

    private Map<Long, DisplayName> namesOf(List<Long> memberIds) {
        Map<Long, MemberSummary> summaries = memberQueryService.summaries(memberIds);
        // 세션 줄 순서(대기실 입장 순)를 지키려고 넘긴 순서대로 담는다.
        Map<Long, DisplayName> names = new LinkedHashMap<>();
        memberIds.forEach(
                id ->
                        names.put(
                                id,
                                summaries.containsKey(id)
                                        ? summaries.get(id).displayName()
                                        : DisplayName.WITHDRAWN));
        return names;
    }
}
