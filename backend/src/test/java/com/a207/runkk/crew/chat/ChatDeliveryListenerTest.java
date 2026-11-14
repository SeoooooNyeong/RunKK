package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewRole;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import com.a207.runkk.crew.chat.live.CrewChatPosted;
import com.a207.runkk.crew.chat.live.CrewChatReactionChanged;
import com.a207.runkk.crew.chat.live.CrewChatRemoved;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 커밋 뒤 채팅 전송(설계 6.1.7a ChatDeliveryListener, 5.7.2 채팅 수신 규칙, D-101, D-102). TC-6116 받는 사람 계산. */
@org.junit.jupiter.api.DisplayName("crew.chat ChatDeliveryListener")
class ChatDeliveryListenerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 9, 0);
    private static final long CREW = 7;
    private static final String U1 = "00000000-0000-0000-0000-000000000001";

    private final InMemoryChatRepositories.Messages messages =
            new InMemoryChatRepositories.Messages();
    private final InMemoryChatRepositories.Reactions reactions =
            new InMemoryChatRepositories.Reactions();
    private final CrewMembershipReader membership = mock(CrewMembershipReader.class);
    private final MemberQueryService memberQuery = mock(MemberQueryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AvatarQuery avatarQuery = mock(AvatarQuery.class);
    private final List<Sent> sent = new ArrayList<>();
    private Set<Long> present = Set.of(1L, 3L, 9L);
    private boolean messengerFails;
    private ChatDeliveryListener listener;

    record Sent(Set<Long> to, CrewChatSignal signal) {}

    @BeforeEach
    void setUp() {
        when(membership.activeMemberIds(CREW)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(membership.rolesIn(CREW, List.of(3L))).thenReturn(Map.of(3L, CrewRole.LEADER));
        when(memberQuery.summaries(anyCollection()))
                .thenReturn(Map.of(3L, new MemberSummary(3, DisplayName.of("새벽곰"), null, null)));
        CrewChatMessenger messenger =
                (memberIds, signal) -> {
                    if (messengerFails) {
                        throw new IllegalStateException("socket down");
                    }
                    sent.add(new Sent(Set.copyOf(memberIds), signal));
                };
        listener =
                new ChatDeliveryListener(
                        messages,
                        reactions,
                        membership,
                        memberQuery,
                        new ChatAuthorFaces(memberQuery, avatarQuery),
                        crewId -> present,
                        messenger,
                        notifications,
                        NoTransactions.manager(),
                        Runnable::run,
                        Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    private long posted(long author, String content) {
        return messages.save(CrewChatMessage.write(CREW, author, new ChatDraft(U1, content), NOW))
                .id();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6116 방 안 ∩ 현재 구성원 {1, 3}에 CrewChatPosted, 방 밖 − 작성자 {2, 4}에 CREW_CHAT 묶음 알림, 떠난 9는"
                    + " 0")
    void posted_routesToRoomAndOutside() {
        long id = posted(3, "내일 7시 출발");

        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).to()).containsExactlyInAnyOrder(1L, 3L);
        CrewChatPosted signal = (CrewChatPosted) sent.get(0).signal();
        assertThat(signal)
                .isEqualTo(
                        new CrewChatPosted(
                                CREW,
                                id,
                                3L,
                                "새벽곰",
                                "내일 7시 출발",
                                OffsetDateTime.of(NOW, ZoneOffset.ofHours(9)),
                                U1,
                                ChatMessageKind.TEXT,
                                "LEADER",
                                null,
                                null,
                                null));
        assertThat(signal.type()).isEqualTo("CrewChatPosted");

        ArgumentCaptor<NotificationRequest> req =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(2)).notifyCollapsing(req.capture());
        assertThat(req.getAllValues().stream().map(NotificationRequest::recipientId))
                .containsExactlyInAnyOrder(2L, 4L);
        NotificationRequest first = req.getAllValues().get(0);
        assertThat(first.type()).isEqualTo(NotificationType.CREW_CHAT);
        assertThat(first.target()).isEqualTo(new NavigationTarget(Screen.CREW_CHAT, "7"));
        assertThat(first.content()).isEqualTo("새벽곰: 내일 7시 출발");
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "W5 러닝 완료 시스템 메시지는 방 안에 RUN_FINISHED·거리로 보내고 방 밖 CREW_CHAT 알림은 보내지 않는다")
    void runFinished_roomOnly() {
        long id =
                messages.save(
                                CrewChatMessage.runFinished(
                                        CREW, 3, "rec-1", "새벽곰", Distance.ofMeters(5_021), NOW))
                        .id();

        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));

        assertThat(sent).hasSize(1);
        CrewChatPosted signal = (CrewChatPosted) sent.get(0).signal();
        assertThat(signal.messageKind()).isEqualTo(ChatMessageKind.RUN_FINISHED);
        assertThat(signal.runDistanceMeters()).isEqualTo(5_021L);
        assertThat(signal.content()).isEqualTo("새벽곰님이 5.02km를 달렸어요");
        assertThat(signal.authorRole()).isEqualTo("LEADER");
        verify(notifications, never()).notifyCollapsing(any());
    }

    private void authorFace(long author, String faceKey, Gender gender) {
        when(memberQuery.disclosureOf(author, Set.of(author)))
                .thenReturn(Map.of(author, DisclosureLevel.SELF));
        when(avatarQuery.faceKeysOf(Set.of(author)))
                .thenReturn(faceKey == null ? Map.of() : Map.of(author, faceKey));
        when(memberQuery.shownGendersOf(Set.of(author))).thenReturn(Map.of(author, gender));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("새 메시지 알림에 작성자 얼굴이 실린다(authorFaceKey, authorShownGender)")
    void 새_메시지_알림에_작성자_얼굴이_실린다() {
        authorFace(3, "head:3:ab12", Gender.FEMALE);
        long id = posted(3, "내일 7시 출발");

        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));

        CrewChatPosted signal = (CrewChatPosted) sent.get(0).signal();
        assertThat(signal.authorFaceKey()).isEqualTo("head:3:ab12");
        assertThat(signal.authorShownGender()).isEqualTo("FEMALE");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("러닝 완료 메시지도 작성자 얼굴을 싣는다. 얼굴을 안 골랐으면 표시 성별만")
    void 러닝완료_메시지도_작성자_얼굴() {
        authorFace(3, null, Gender.MALE);
        long id =
                messages.save(
                                CrewChatMessage.runFinished(
                                        CREW, 3, "rec-2", "새벽곰", Distance.ofMeters(5_021), NOW))
                        .id();

        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));

        CrewChatPosted signal = (CrewChatPosted) sent.get(0).signal();
        assertThat(signal.messageKind()).isEqualTo(ChatMessageKind.RUN_FINISHED);
        assertThat(signal.authorFaceKey()).isNull();
        assertThat(signal.authorShownGender()).isEqualTo("MALE");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("폴링 대체(방 안 빈 집합): 전송 0, 작성자를 뺀 크루원 모두에게 알림(F.3.2)")
    void posted_pollingMode_notifiesEveryoneButAuthor() {
        present = Set.of();
        long id = posted(3, "안녕");
        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));
        assertThat(sent).isEmpty();
        verify(notifications, times(3)).notifyCollapsing(any());
        verify(notifications, never()).notifyCollapsing(argThat(r -> r.recipientId() == 3L));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("알림 미리보기는 100자(코드 포인트)까지, 소켓 실패·알림 한 건 실패는 나머지를 막지 않는다")
    void posted_failuresAreIsolated() {
        long id = posted(3, "가".repeat(300));
        messengerFails = true;
        doThrow(new IllegalStateException("db"))
                .when(notifications)
                .notifyCollapsing(argThat(r -> r.recipientId() == 2L));

        assertThatCode(() -> listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW)))
                .doesNotThrowAnyException();

        ArgumentCaptor<NotificationRequest> req =
                ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(2)).notifyCollapsing(req.capture());
        assertThat(req.getAllValues().get(1).content()).isEqualTo("새벽곰: " + "가".repeat(100));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("커밋 뒤 메시지가 이미 없으면(지워짐) 보내지 않는다")
    void posted_missingMessage_skips() {
        listener.onPosted(new ChatMessagePosted(CREW, 999, 3, NOW));
        assertThat(sent).isEmpty();
        verify(notifications, never()).notifyCollapsing(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("커밋 뒤 이미 지워졌거나 숨겨진 메시지는 보내지 않는다")
    void posted_invisibleMessage_skips() {
        long id = posted(3, "곧 지워짐");
        messages.rows.get(id).hide();
        listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW));
        assertThat(sent).isEmpty();
        verify(notifications, never()).notifyCollapsing(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("조회가 실패해도 예외를 던지지 않는다(이미 커밋된 요청을 500으로 만들지 않는다)")
    void readFailure_isSwallowed() {
        when(membership.activeMemberIds(CREW)).thenThrow(new IllegalStateException("db"));
        long id = posted(3, "안녕");
        assertThatCode(() -> listener.onPosted(new ChatMessagePosted(CREW, id, 3, NOW)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                listener.onRemoved(
                                        new ChatMessageRemoved(
                                                CREW, id, ChatRemovalReason.DELETED, NOW)))
                .doesNotThrowAnyException();
        assertThatCode(() -> listener.onReactionsChanged(new ChatReactionsChanged(CREW, id, NOW)))
                .doesNotThrowAnyException();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("삭제·숨김은 방 안 크루원에게 CrewChatRemoved(사유)")
    void removed() {
        listener.onRemoved(new ChatMessageRemoved(CREW, 5, ChatRemovalReason.HIDDEN, NOW));
        assertThat(sent)
                .containsExactly(
                        new Sent(
                                Set.of(1L, 3L),
                                new CrewChatRemoved(CREW, 5, ChatRemovalReason.HIDDEN)));
        verify(notifications, never()).notifyCollapsing(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("리액션 변경은 방 안에 수만 보낸다(누가 달았는지 없음, 0인 종류는 빠짐)")
    void reactionsChanged() {
        long id = posted(3, "안녕");
        reactions.add(ChatReaction.of(id, 1, ReactionKind.LIKE, NOW));
        reactions.add(ChatReaction.of(id, 2, ReactionKind.LIKE, NOW));

        listener.onReactionsChanged(new ChatReactionsChanged(CREW, id, NOW));

        assertThat(sent).hasSize(1);
        CrewChatReactionChanged signal = (CrewChatReactionChanged) sent.get(0).signal();
        assertThat(signal.reactions()).containsExactlyEntriesOf(Map.of(ReactionKind.LIKE, 2));
        assertThat(signal.messageId()).isEqualTo(id);
        assertThat(sent.get(0).to()).containsExactlyInAnyOrder(1L, 3L);
    }
}
