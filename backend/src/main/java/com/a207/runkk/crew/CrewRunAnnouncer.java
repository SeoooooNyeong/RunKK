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
import java.util.HashMap;
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
                CrewRunLiveSession.open(post.getId(), post.distance(), names, outcome.startAt());
        AfterCommit.run(
                () -> {
                    crewLiveSessionStore.save(session);
                    crewRunMessenger.sendAll(outcome.memberIds(), start);
                });
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
                    crewRunMessenger.sendAll(runners, new CrewRunEnded(postId));
                    crewLiveSessionStore.delete(postId);
                    crewRunLiveService.forget(postId);
                });
    }

    /** 그만둠: 세션 표시 → 다른 참가자에게 중계(커밋 뒤). */
    void quitter(long postId, long memberId) {
        AfterCommit.run(() -> crewRunLiveService.markQuit(memberId, postId));
    }

    private Map<Long, DisplayName> namesOf(List<Long> memberIds) {
        Map<Long, MemberSummary> summaries = memberQueryService.summaries(memberIds);
        Map<Long, DisplayName> names = new HashMap<>();
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
