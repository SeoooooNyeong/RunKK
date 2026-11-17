package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewRelation;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.member.MemberWithdrawn;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

/** 설계 6.1.7a CrewChatService, 6.2 A-25. TC-6108~TC-6112, TC-6116~TC-6121, 신고용 연산(D-135). */
@org.junit.jupiter.api.DisplayName("crew.chat CrewChatService")
class CrewChatServiceTest {

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
    private final AvatarQuery avatarQuery = mock(AvatarQuery.class);
    private final AuditService audit = mock(AuditService.class);
    private final List<DomainEvent> events = new ArrayList<>();
    private CrewChatService service;

    @BeforeEach
    void setUp() {
        Map<Long, CrewRelation> relations =
                Map.of(
                        1L, CrewRelation.LEADER,
                        2L, CrewRelation.MANAGER,
                        3L, CrewRelation.MEMBER,
                        4L, CrewRelation.MEMBER);
        when(membership.relationOf(anyLong(), eq(CREW)))
                .thenAnswer(
                        inv -> relations.getOrDefault(inv.getArgument(0), CrewRelation.OUTSIDER));
        when(membership.activeMemberIds(CREW)).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(memberQuery.summaries(anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, MemberSummary> out = new HashMap<>();
                            for (Long id : inv.<Collection<Long>>getArgument(0)) {
                                out.put(
                                        id,
                                        new MemberSummary(
                                                id, DisplayName.of("러너" + id), null, null));
                            }
                            return out;
                        });
        service = serviceWith(messages);
    }

    private CrewChatService serviceWith(CrewChatMessageRepository repository) {
        return new CrewChatService(
                repository,
                reactions,
                membership,
                memberQuery,
                new ChatAuthorFaces(memberQuery, avatarQuery),
                audit,
                events::add,
                NoTransactions.manager(),
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    private static String uuid(long n) {
        return String.format("00000000-0000-0000-0000-%012d", n);
    }

    /** 크루 7에 작성자를 돌려 가며(1~4) 메시지 n개를 넣는다(ID 1~n). */
    private void seed(int n) {
        for (int i = 1; i <= n; i++) {
            long author = (i % 4) + 1;
            messages.save(
                    CrewChatMessage.write(
                            CREW, author, new ChatDraft(uuid(i), "메시지 " + i), NOW.plusSeconds(i)));
        }
    }

    private static ReasonCode reasonOf(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException e) {
            return e.reason();
        }
        return null;
    }

    private static List<Long> ids(ChatPage page) {
        return page.messages().stream().map(ChatPage.Line::messageId).toList();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6108 크루 밖·크루를 떠난 회원의 조회는 NOT_CREW_MEMBER")
    void history_outsider() {
        seed(10);
        assertThat(reasonOf(() -> service.history(9, CREW, ChatCursor.of(null, null, null))))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        verify(memberQuery, never()).summaries(anyCollection());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6109 before 커서: 보이는 것만 오래된 → 새 순, hasMore")
    void history_before() {
        seed(120);
        messages.rows.get(90L).delete(1, CrewRelation.LEADER);
        messages.rows.get(95L).hide();

        ChatPage page = service.history(3, CREW, ChatCursor.of(100L, null, null));
        assertThat(page.messages()).hasSize(50);
        assertThat(ids(page).get(0)).isEqualTo(48L);
        assertThat(ids(page).get(49)).isEqualTo(99L);
        assertThat(ids(page)).doesNotContain(90L, 95L).isSorted();
        assertThat(page.hasMore()).isTrue();

        ChatPage twenty = service.history(3, CREW, ChatCursor.of(100L, null, 20));
        assertThat(ids(twenty)).hasSize(20).startsWith(78L).endsWith(99L).doesNotContain(90L, 95L);
        assertThat(twenty.hasMore()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6110 after 커서(재연결 뒤 메우기): 116~120 오름차순, hasMore 거짓")
    void history_after() {
        seed(120);
        ChatPage page = service.history(3, CREW, ChatCursor.of(null, 115L, 20));
        assertThat(ids(page)).containsExactly(116L, 117L, 118L, 119L, 120L);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6111 커서 없음: 최신 20개, mine·canDelete·clientMessageId(본인만), 탈퇴 작성자는 \"탈퇴한 사용자\", 이름 조회"
                    + " 1회")
    void history_latest() {
        seed(120);
        messages.rows.get(120L).anonymizeAuthor();
        reactions.add(ChatReaction.of(119, 1, ReactionKind.LIKE, NOW));
        reactions.add(ChatReaction.of(119, 3, ReactionKind.LIKE, NOW));
        reactions.add(ChatReaction.of(119, 3, ReactionKind.HEART, NOW));

        ChatPage page = service.history(3, CREW, ChatCursor.of(null, null, 20));
        assertThat(ids(page)).hasSize(20).startsWith(101L).endsWith(120L);
        assertThat(page.hasMore()).isTrue();
        verify(memberQuery, times(1)).summaries(anyCollection());

        Map<Long, ChatPage.Line> byId = new HashMap<>();
        page.messages().forEach(l -> byId.put(l.messageId(), l));
        ChatPage.Line mine = byId.get(102L); // 102 % 4 + 1 = 3
        assertThat(mine.mine()).isTrue();
        assertThat(mine.canDelete()).isTrue();
        assertThat(mine.clientMessageId()).isEqualTo(uuid(102));
        assertThat(mine.authorName()).isEqualTo("러너3");
        assertThat(mine.sentAt())
                .isEqualTo(OffsetDateTime.of(NOW.plusSeconds(102), ZoneOffset.ofHours(9)));
        ChatPage.Line others = byId.get(101L); // 작성자 2
        assertThat(others.mine()).isFalse();
        assertThat(others.canDelete()).isFalse();
        assertThat(others.clientMessageId()).isNull();
        ChatPage.Line withdrawn = byId.get(120L);
        assertThat(withdrawn.authorId()).isNull();
        assertThat(withdrawn.authorName()).isEqualTo("탈퇴한 사용자");
        ChatPage.Line reacted = byId.get(119L);
        assertThat(reacted.reactions())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(ReactionKind.LIKE, 2, ReactionKind.HEART, 1));
        assertThat(reacted.myReactions())
                .containsExactlyInAnyOrder(ReactionKind.LIKE, ReactionKind.HEART);
        assertThat(byId.get(118L).reactions()).isEmpty();

        ChatPage leaderView = service.history(1, CREW, ChatCursor.of(null, null, 20));
        assertThat(leaderView.messages()).allMatch(ChatPage.Line::canDelete);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6112 size 100·101은 100개, 0은 50개(hasMore 참)")
    void history_sizeBounds() {
        seed(150);
        assertThat(service.history(3, CREW, ChatCursor.of(null, null, 100)).messages())
                .hasSize(100);
        assertThat(service.history(3, CREW, ChatCursor.of(null, null, 101)).messages())
                .hasSize(100);
        ChatPage zero = service.history(3, CREW, ChatCursor.of(null, null, 0));
        assertThat(zero.messages()).hasSize(50);
        assertThat(zero.hasMore()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6116 보내기: created 참, 저장 1, ChatMessagePosted 1(전송은 커밋 뒤 수신자가 한다)")
    void post_created() {
        ChatPostResult result = service.post(3, CREW, new ChatDraft(U1, "안녕"));
        assertThat(result.created()).isTrue();
        assertThat(result.message().content()).isEqualTo("안녕");
        assertThat(result.message().mine()).isTrue();
        assertThat(result.message().clientMessageId()).isEqualTo(U1);
        assertThat(result.message().authorName()).isEqualTo("러너3");
        assertThat(messages.rows).hasSize(1);
        assertThat(events)
                .containsExactly(new ChatMessagePosted(CREW, result.message().messageId(), 3, NOW));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6117 같은 작성자·같은 clientMessageId 재전송은 created 거짓·같은 메시지, 다른 작성자는 새 메시지")
    void post_idempotent() {
        ChatPostResult first = service.post(3, CREW, new ChatDraft(U1, "안녕"));
        ChatPostResult again = service.post(3, CREW, new ChatDraft(U1, "안녕"));
        assertThat(again.created()).isFalse();
        assertThat(again.message().messageId()).isEqualTo(first.message().messageId());
        assertThat(messages.saves).isEqualTo(1);
        assertThat(events).hasSize(1);

        ChatPostResult other = service.post(4, CREW, new ChatDraft(U1, "안녕"));
        assertThat(other.created()).isTrue();
        assertThat(other.message().messageId()).isNotEqualTo(first.message().messageId());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("같은 clientMessageId를 다른 크루에 쓰면 400(다른 크루 메시지를 돌려주지 않는다)")
    void post_sameClientIdOtherCrew_isRejected() {
        when(membership.relationOf(3L, 8L)).thenReturn(CrewRelation.MEMBER);
        service.post(3, CREW, new ChatDraft(U1, "크루 7"));
        assertThatThrownBy(() -> service.post(3, 8, new ChatDraft(U1, "크루 8")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(messages.rows).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("동시 재전송이 고유 색인에 걸리면 다시 읽어 처음 메시지를 created 거짓으로 돌려준다(5.3)")
    void post_uniqueConflict_rereads() {
        InMemoryChatRepositories.Messages racing =
                new InMemoryChatRepositories.Messages() {
                    @Override
                    public CrewChatMessage save(CrewChatMessage message) {
                        if (message.id() == null && rows.isEmpty()) {
                            super.save(
                                    CrewChatMessage.write(
                                            CREW, 3, new ChatDraft(U1, "먼저 온 요청"), NOW));
                            throw new DataIntegrityViolationException("uq_crew_chat_client");
                        }
                        return super.save(message);
                    }
                };
        ChatPostResult result = serviceWith(racing).post(3, CREW, new ChatDraft(U1, "안녕"));
        assertThat(result.created()).isFalse();
        assertThat(result.message().content()).isEqualTo("먼저 온 요청");
        assertThat(racing.rows).hasSize(1);
        assertThat(events).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("고유 색인이 아닌 무결성 위반은 그대로 던진다")
    void post_otherIntegrityFailure_propagates() {
        InMemoryChatRepositories.Messages failing =
                new InMemoryChatRepositories.Messages() {
                    @Override
                    public CrewChatMessage save(CrewChatMessage message) {
                        throw new DataIntegrityViolationException("fk_crew");
                    }
                };
        assertThatThrownBy(() -> serviceWith(failing).post(3, CREW, new ChatDraft(U1, "안녕")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6118 크루 밖 보내기·방 입장은 NOT_CREW_MEMBER(저장 0), 크루원 입장은 통과")
    void post_outsider_andRoomAccess() {
        assertThat(reasonOf(() -> service.post(9, CREW, new ChatDraft(U1, "안녕"))))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        assertThat(messages.rows).isEmpty();
        assertThat(reasonOf(() -> service.checkRoomAccess(9, CREW)))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        assertThatCode(() -> service.checkRoomAccess(3, CREW)).doesNotThrowAnyException();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("빈 메시지·501자는 저장·사건 0")
    void post_invalidContent() {
        assertThat(reasonOf(() -> service.post(3, CREW, new ChatDraft(U1, "  "))))
                .isEqualTo(ReasonCode.CHAT_EMPTY);
        assertThat(reasonOf(() -> service.post(3, CREW, new ChatDraft(U1, "a".repeat(501)))))
                .isEqualTo(ReasonCode.BODY_TOO_LONG);
        assertThat(messages.rows).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6119 삭제: 숨김·없음은 CHAT_MESSAGE_NOT_FOUND, 운영진이 남의 것은 감사"
                    + " CHAT_FORCE_DELETE(CREW_MANAGER), 본인은 감사 0")
    void delete() {
        long hidden = service.post(3, CREW, new ChatDraft(uuid(1), "숨김")).message().messageId();
        messages.rows.get(hidden).hide();
        assertThat(reasonOf(() -> service.delete(3, hidden)))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
        assertThat(messages.rows.get(hidden).isDeleted()).isFalse();
        events.clear();

        long fours = service.post(4, CREW, new ChatDraft(uuid(2), "4의 글")).message().messageId();
        events.clear();
        service.delete(2, fours);
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.CHAT_FORCE_DELETE);
        assertThat(entry.getValue().actorRole()).isEqualTo(ActorRole.CREW_MANAGER);
        assertThat(entry.getValue().actorId()).isEqualTo("2");
        assertThat(entry.getValue().targetId()).isEqualTo(String.valueOf(fours));
        assertThat(events)
                .containsExactly(
                        new ChatMessageRemoved(CREW, fours, ChatRemovalReason.DELETED, NOW));

        long threes = service.post(3, CREW, new ChatDraft(uuid(3), "3의 글")).message().messageId();
        events.clear();
        service.delete(3, threes);
        verify(audit, times(1)).record(org.mockito.ArgumentMatchers.any());
        assertThat(events).hasSize(1);

        long leaders = service.post(1, CREW, new ChatDraft(uuid(4), "크루장 글")).message().messageId();
        assertThatThrownBy(() -> service.delete(4, leaders))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(reasonOf(() -> service.delete(3, 999)))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
        assertThat(reasonOf(() -> service.delete(3, fours)))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루를 떠난·강퇴된 작성자는 자기 메시지도 못 지운다(NOT_CREW_MEMBER, 사건 0)")
    void delete_byFormerMember_isRejected() {
        long mine = service.post(3, CREW, new ChatDraft(uuid(5), "남긴 글")).message().messageId();
        events.clear();
        when(membership.relationOf(3L, CREW)).thenReturn(CrewRelation.OUTSIDER);

        assertThat(reasonOf(() -> service.delete(3, mine))).isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        assertThat(messages.rows.get(mine).isDeleted()).isFalse();
        assertThat(events).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("크루장이 남의 메시지를 지우면 감사 행위자 역할 CREW_LEADER")
    void delete_byLeader_auditsLeaderRole() {
        long fours = service.post(4, CREW, new ChatDraft(uuid(2), "4의 글")).message().messageId();
        service.delete(1, fours);
        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue().actorRole()).isEqualTo(ActorRole.CREW_LEADER);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6120 리액션: 두 번 달아도 1(사건 1), 없는 리액션 떼기는 그대로(사건 0), 크루 밖 NOT_CREW_MEMBER, 없는 메시지"
                    + " NOT_FOUND")
    void react() {
        long id = service.post(3, CREW, new ChatDraft(U1, "안녕")).message().messageId();
        events.clear();

        ReactionView first = service.react(3, id, ReactionKind.LIKE);
        ReactionView second = service.react(3, id, ReactionKind.LIKE);
        assertThat(first.reactions()).containsExactlyEntriesOf(Map.of(ReactionKind.LIKE, 1));
        assertThat(second.reactions()).containsExactlyEntriesOf(Map.of(ReactionKind.LIKE, 1));
        assertThat(second.myReactions()).containsExactly(ReactionKind.LIKE);
        assertThat(events).containsExactly(new ChatReactionsChanged(CREW, id, NOW));

        ReactionView unchanged = service.unreact(3, id, ReactionKind.HEART);
        assertThat(unchanged.reactions()).containsExactlyEntriesOf(Map.of(ReactionKind.LIKE, 1));
        assertThat(events).hasSize(1);

        service.react(4, id, ReactionKind.LIKE);
        ReactionView afterUnreact = service.unreact(3, id, ReactionKind.LIKE);
        assertThat(afterUnreact.reactions()).containsExactlyEntriesOf(Map.of(ReactionKind.LIKE, 1));
        assertThat(afterUnreact.myReactions()).isEmpty();
        assertThat(events).hasSize(3);

        assertThat(reasonOf(() -> service.react(9, id, ReactionKind.LIKE)))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        assertThat(reasonOf(() -> service.react(3, 999, ReactionKind.LIKE)))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
        assertThat(reasonOf(() -> service.unreact(9, id, ReactionKind.LIKE)))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6121 탈퇴: 그 회원 메시지는 익명화(내용 유지), 그 회원 리액션만 지운다")
    void withdrawal() {
        long a = service.post(3, CREW, new ChatDraft(uuid(1), "첫째")).message().messageId();
        long b = service.post(3, CREW, new ChatDraft(uuid(2), "둘째")).message().messageId();
        reactions.add(ChatReaction.of(a, 3, ReactionKind.LIKE, NOW));
        reactions.add(ChatReaction.of(b, 3, ReactionKind.LIKE, NOW));
        reactions.add(ChatReaction.of(b, 3, ReactionKind.HEART, NOW));
        reactions.add(ChatReaction.of(b, 4, ReactionKind.HEART, NOW));

        new ChatWithdrawalListener(messages, reactions)
                .onMemberWithdrawn(new MemberWithdrawn(3, NOW));

        assertThat(messages.rows.get(a).authorId()).isNull();
        assertThat(messages.rows.get(b).authorId()).isNull();
        assertThat(messages.rows.get(b).content()).isEqualTo("둘째");
        assertThat(reactions.rows).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("신고용 연산(D-135): 숨김도 보이고 삭제는 빈 값, 숨김은 HIDDEN 사건, 해제는 다시 보임")
    void reportOperations() {
        long id = service.post(4, CREW, new ChatDraft(U1, "가".repeat(150))).message().messageId();
        events.clear();
        Optional<ReportedChatView> view = service.reportedView(id);
        assertThat(view).isPresent();
        assertThat(view.get().crewId()).isEqualTo(CREW);
        assertThat(view.get().authorId()).isEqualTo(4L);
        assertThat(view.get().preview()).hasSize(100);
        assertThat(view.get().hidden()).isFalse();

        service.hideReported(id);
        assertThat(messages.rows.get(id).isHidden()).isTrue();
        assertThat(events)
                .containsExactly(new ChatMessageRemoved(CREW, id, ChatRemovalReason.HIDDEN, NOW));
        service.hideReported(id);
        assertThat(events).hasSize(1);
        assertThat(service.reportedView(id).orElseThrow().hidden()).isTrue();

        service.unhideReported(id);
        assertThat(messages.rows.get(id).isVisible()).isTrue();

        messages.rows.get(id).delete(4, CrewRelation.MEMBER);
        assertThat(service.reportedView(id)).isEmpty();
        assertThat(service.reportedView(999)).isEmpty();
        assertThatCode(() -> service.hideReported(999)).doesNotThrowAnyException();
        assertThat(Set.of(events.size())).containsExactly(1);
    }
}
