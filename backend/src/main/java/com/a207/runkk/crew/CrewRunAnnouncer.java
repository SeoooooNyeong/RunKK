package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.crew.live.CrewRunCancelled;
import com.a207.runkk.crew.live.CrewRunChanged;
import com.a207.runkk.crew.live.CrewRunDeparture;
import com.a207.runkk.crew.live.CrewRunEnded;
import com.a207.runkk.crew.live.CrewRunStart;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 크루런 시계가 알릴 것을 보낸다(CrewRunService.tick·quit의 알림·실시간 메시지·세션 관리, SQ-09). 실시간 메시지와 세션 생성·삭제는 커밋 뒤에
 * 한다(AfterCommit). CrewRunService의 생성자 의존을 Fan-Out 7 안에 두려고 뗀 패키지 전용 보조다. 설계 05-32: 확정한 변경을 현재
 * 크루원에게 CrewRunChanged로 알리고(changed), 출발 사실을 대기실 입장자 전원에게 CrewRunDeparture로 보내며(started), 중간 참여자를
 * 세션에 더한다(joined).
 */
@Component
class CrewRunAnnouncer {

    /** 준비 완료 인원이 최소 준비 인원(운영 기준 CrewRunRule.minReady)보다 적을 때 취소 문구(srs.md 6.3 5.7.2, 피그마 F-14c). */
    static final String CANCELLED_REASON = "준비 완료한 참가자가 %d명보다 적어 시작하지 않았어요";

    /** 대기실에서 모집글·크루가 지워졌을 때 문구(D-66). */
    static final String DELETED_REASON = "크루런이 삭제되었어요";

    private static final String SOON_TEXT = "곧 크루런이 시작돼요. 대기실이 열렸어요";

    private final CrewRunMessenger crewRunMessenger;
    private final CrewLiveSessionStore crewLiveSessionStore;
    private final CrewRunLiveService crewRunLiveService;
    private final MemberQueryService memberQueryService;
    private final NotificationService notificationService;
    private final CrewRepository crewRepository;
    private final Clock clock;

    CrewRunAnnouncer(
            CrewRunMessenger crewRunMessenger,
            CrewLiveSessionStore crewLiveSessionStore,
            CrewRunLiveService crewRunLiveService,
            MemberQueryService memberQueryService,
            NotificationService notificationService,
            CrewRepository crewRepository,
            Clock clock) {
        this.crewRunMessenger = crewRunMessenger;
        this.crewLiveSessionStore = crewLiveSessionStore;
        this.crewRunLiveService = crewRunLiveService;
        this.memberQueryService = memberQueryService;
        this.notificationService = notificationService;
        this.crewRepository = crewRepository;
        this.clock = clock;
    }

    /**
     * 확정한 변경 알림(설계 05-32 [임시:D-성-878], [임시:D-성-891]): 글 행 잠금 안에서 변경 순번을 하나 올리고, 지금 그 크루의 현재 크루원에게
     * 바뀐 자료마다 CrewRunChanged를 커밋 뒤에 보낸다. 롤백되면 보내지 않는다(AfterCommit). 받는 사람은 발송 직전 소속으로 정하며 임의 crewId
     * 구독은 없다. 같은 트랜잭션에서 여러 번 부르면 순번이 그만큼 오른다.
     */
    void changed(CrewRunPost post, CrewRunChanged.Resource... resources) {
        long revision = post.markChanged();
        long crewId = post.crewId();
        long postId = post.getId();
        LocalDateTime at = LocalDateTime.now(clock);
        Set<CrewRunChanged.Resource> distinct = EnumSet.noneOf(CrewRunChanged.Resource.class);
        distinct.addAll(List.of(resources));
        List<Long> recipients =
                crewRepository.findById(crewId).map(Crew::activeMemberIds).orElse(List.of());
        if (recipients.isEmpty()) {
            return;
        }
        for (CrewRunChanged.Resource resource : distinct) {
            CrewRunChanged message = CrewRunChanged.of(crewId, postId, resource, revision, at);
            AfterCommit.run(() -> crewRunMessenger.sendAll(recipients, message));
        }
    }

    /** 목록에 보이는 값이 바뀌었다: SCHEDULE, 대기실이 열려 있으면 LOBBY, 진행 중이면 PROGRESS도(설계 05-32 5.3). */
    void scheduleChanged(CrewRunPost post) {
        List<CrewRunChanged.Resource> resources = new ArrayList<>();
        resources.add(CrewRunChanged.Resource.SCHEDULE);
        if (post.status() == CrewRunStatus.LOBBY_OPEN) {
            resources.add(CrewRunChanged.Resource.LOBBY);
        } else if (post.status() == CrewRunStatus.RUNNING) {
            resources.add(CrewRunChanged.Resource.PROGRESS);
        }
        changed(post, resources.toArray(CrewRunChanged.Resource[]::new));
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

    /**
     * 출발(설계 05-32 [임시:D-성-883]): 세션을 열고, 그때 대기실 입장자 전원(준비하지 않은 사람 포함)에게 CrewRunDeparture, 확정 진행
     * 참가자에게만 기존 CrewRunStart(옛 앱 호환)를 보낸다(커밋 뒤). 진행 참가자가 0명이어도 세션을 연다(늦은 참여를 받는다).
     */
    void started(CrewRunPost post, DepartureOutcome outcome) {
        Map<Long, DisplayName> names = namesOf(outcome.memberIds());
        CrewRunStart start =
                new CrewRunStart(
                        post.getId(),
                        outcome.startAt(),
                        outcome.memberIds().stream().map(names::get).toList());
        CrewRunLiveSession session =
                CrewRunLiveSession.open(post.getId(), names, outcome.startAt());
        long crewId = post.crewId();
        long postId = post.getId();
        AfterCommit.run(() -> crewLiveSessionStore.save(session));
        // 순번은 커밋 뒤에 읽는다(같은 트랜잭션의 changed가 올린 값)
        AfterCommit.run(
                () ->
                        crewRunMessenger.sendAll(
                                outcome.notifiedIds(),
                                new CrewRunDeparture(
                                        crewId,
                                        postId,
                                        post.revision(),
                                        outcome.countdownToken(),
                                        outcome.startAt(),
                                        outcome.memberIds())));
        if (!outcome.memberIds().isEmpty()) {
            AfterCommit.run(() -> crewRunMessenger.sendAll(outcome.memberIds(), start));
        }
    }

    /**
     * 중간 참여자를 세션에 더하고 중계한다(커밋 뒤, 설계 05-32 [임시:D-성-885]). 실패해도 1초 시계가 누락 참가자를 보충한다(ensureSession).
     */
    void joined(CrewRunPost post, long memberId) {
        long postId = post.getId();
        LocalDateTime departedAt = post.departedAt().orElseThrow();
        LocalDateTime joinedAt = joinedAtOf(post, memberId, departedAt);
        DisplayName name = namesOf(List.of(memberId)).get(memberId);
        AfterCommit.run(() -> crewRunLiveService.join(postId, memberId, name, joinedAt));
    }

    /** 취소: 갈래요 참가자에게 CrewRunCancelled(커밋 뒤). 문구의 인원은 [minReady](운영 기준값)로 채운다. */
    void cancelled(CrewRunPost post, List<Long> recipients, int minReady) {
        CrewRunCancelled cancelled = new CrewRunCancelled(post.getId(), cancelledReason(minReady));
        AfterCommit.run(() -> crewRunMessenger.sendAll(recipients, cancelled));
    }

    /** 준비 인원 부족 취소 문구. */
    static String cancelledReason(int minReady) {
        return CANCELLED_REASON.formatted(minReady);
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

    /**
     * 진행 중인데 세션이 없으면(출발 커밋 뒤 Redis 저장 실패, Redis 재시작) 진행 참가자로 다시 연다. 이미 끝낸 참가자는 참여에 남은 마감 거리·경과로
     * "끝냄"을 되살려 중계 합산과 상태가 DB와 같게 한다(MR !91 BR-2). 1초 시계가 RUNNING 크루런마다 부른다.
     */
    void ensureSession(CrewRunPost post) {
        if (post.status() != CrewRunStatus.RUNNING) {
            return;
        }
        List<Long> runners = post.runners();
        LocalDateTime departedAt = post.departedAt().orElseThrow();
        Optional<CrewRunLiveSession> existing = crewLiveSessionStore.load(post.getId());
        if (existing.isPresent()) {
            supplementMissing(post, existing.get(), runners, departedAt);
            return;
        }
        Map<Long, DisplayName> names = namesOf(runners);
        CrewRunLiveSession session = CrewRunLiveSession.open(post.getId(), Map.of(), departedAt);
        for (long memberId : runners) {
            session.join(memberId, names.get(memberId), joinedAtOf(post, memberId, departedAt));
            post.participationOf(memberId)
                    .filter(CrewRunParticipation::hasEnded)
                    .ifPresent(p -> session.markEnded(memberId, finalProgressOf(p, departedAt)));
        }
        crewLiveSessionStore.save(session);
    }

    /**
     * 세션은 있는데 DB의 진행 참가자가 빠졌으면(중간 참여 커밋 뒤 Redis 반영 실패, 설계 05-32 TC-11046) 개인 측정 시작 시각으로 보충한다. 이미 있는
     * 참가자의 거리와 시작 시각은 건드리지 않는다.
     */
    private void supplementMissing(
            CrewRunPost post,
            CrewRunLiveSession session,
            List<Long> runners,
            LocalDateTime departedAt) {
        List<Long> missing =
                runners.stream().filter(id -> !session.participants().contains(id)).toList();
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, DisplayName> names = namesOf(missing);
        for (long memberId : missing) {
            crewRunLiveService.join(
                    post.getId(),
                    memberId,
                    names.get(memberId),
                    joinedAtOf(post, memberId, departedAt));
        }
    }

    private static LocalDateTime joinedAtOf(
            CrewRunPost post, long memberId, LocalDateTime departedAt) {
        return post.participationOf(memberId)
                .flatMap(CrewRunParticipation::joinedAt)
                .orElse(departedAt);
    }

    /** 참여에 남은 마감 값(거리가 없으면 null, 경과가 없으면 0). */
    private static LiveProgress finalProgressOf(CrewRunParticipation p, LocalDateTime seenAt) {
        return p.finalDistance()
                .map(d -> new LiveProgress(d, p.finalElapsed().orElse(Duration.ZERO), seenAt))
                .orElse(null);
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
