package com.a207.runkk.crew;

import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * 크루 소식 알림의 받는 사람과 종류를 정한다(설계 6.1.7, FR-218, D-106). 알림함 행은 글 저장과 같은 트랜잭션에 쓰고 푸시는
 * NotificationService가 커밋 뒤에 보낸다(롤백되면 알림도 없다). 모든 알림은 PushDecision으로 걸러진다(FR-175).
 */
@Component
public class CrewNewsNotifier {

    static final String NOTICE_TEXT = "새 공지가 올라왔어요";
    static final String POLL_TEXT = "새 투표가 열렸어요";
    static final String SCHEDULE_TEXT = "새 모임 일정이 올라왔어요";
    static final String CLOSING_TEXT = "투표 마감이 1시간 남았어요";

    private final CrewMembershipReader membershipReader;
    private final NotificationService notificationService;

    CrewNewsNotifier(
            CrewMembershipReader membershipReader, NotificationService notificationService) {
        this.membershipReader = membershipReader;
        this.notificationService = notificationService;
    }

    /**
     * 작성자를 뺀 현재 크루원에게 글 종류에 맞는 알림: NOTICE → CREW_NOTICE, POLL → CREW_POLL, MEETUP → CREW_SCHEDULE.
     * 응원·게시글은 알리지 않고 크루런은 기존 CREW_RUN_RECRUIT 경로(FR-193)라 여기서 보내지 않는다.
     */
    public void announce(CrewPost post, long authorId) {
        NotificationType type =
                switch (post.kind()) {
                    case NOTICE -> NotificationType.CREW_NOTICE;
                    case POLL -> NotificationType.CREW_POLL;
                    case MEETUP -> NotificationType.CREW_SCHEDULE;
                    case CHEER, CREW_RUN, BOARD -> null;
                };
        if (type == null) {
            return;
        }
        String text =
                switch (type) {
                    case CREW_NOTICE -> NOTICE_TEXT;
                    case CREW_POLL -> POLL_TEXT;
                    default -> SCHEDULE_TEXT;
                };
        NavigationTarget target =
                new NavigationTarget(type.defaultScreen(), String.valueOf(post.getId()));
        membershipReader.activeMemberIds(post.crewId()).stream()
                .filter(id -> id != authorId)
                .forEach(
                        id ->
                                notificationService.notify(
                                        new NotificationRequest(id, type, text, target)));
    }

    /** 미응답 현재 크루원에게 POLL_CLOSING_SOON(FR-218). */
    public void remindClosing(PollPost poll, Collection<Long> recipients) {
        NavigationTarget target =
                new NavigationTarget(
                        NotificationType.POLL_CLOSING_SOON.defaultScreen(),
                        String.valueOf(poll.getId()));
        recipients.forEach(
                id ->
                        notificationService.notify(
                                new NotificationRequest(
                                        id,
                                        NotificationType.POLL_CLOSING_SOON,
                                        CLOSING_TEXT,
                                        target)));
    }
}
