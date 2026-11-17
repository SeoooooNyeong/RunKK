package com.a207.runkk.crew;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 크루 공지 알림은 앱에 공지를 볼 화면이 없어 알림 내용에 공지 본문을 싣는다(DV-14). */
class CrewNewsNotifierTest {
    private static final long CREW = 5;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 9, 0);
    private final CrewMembershipReader members = mock(CrewMembershipReader.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final CrewNewsNotifier notifier = new CrewNewsNotifier(members, notifications);

    private NotificationRequest sentFor(CrewPost post) {
        when(members.activeMemberIds(CREW)).thenReturn(List.of(1L, 2L));
        notifier.announce(post, 1);
        ArgumentCaptor<NotificationRequest> sent =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).notify(sent.capture());
        return sent.getValue();
    }

    @Test
    void noticeNotificationCarriesTheNoticeBody() {
        NoticePost notice =
                new NoticePost(CREW, 1, new NoticeDraft("  토요일 한강 집합은\n7시예요  ", false), NOW);
        NotificationRequest request = sentFor(notice);
        assertThat(request.type()).isEqualTo(NotificationType.CREW_NOTICE);
        assertThat(request.recipientId()).isEqualTo(2);
        assertThat(request.content()).isEqualTo("토요일 한강 집합은 7시예요");
        assertThat(request.target().screen()).isEqualTo(Screen.CREW_NOTICE);
    }

    @Test
    void longNoticeIsCutWithEllipsisWithoutSplittingCharacters() {
        String body = "😀".repeat(150) + "가".repeat(100);
        NotificationRequest request =
                sentFor(new NoticePost(CREW, 1, new NoticeDraft(body, false), NOW));
        assertThat(request.content().codePointCount(0, request.content().length()))
                .isEqualTo(CrewNewsNotifier.NOTICE_PREVIEW + 1);
        assertThat(request.content()).startsWith("😀".repeat(150)).endsWith("…");
    }
}
