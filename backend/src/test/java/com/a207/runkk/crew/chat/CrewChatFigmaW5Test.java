package com.a207.runkk.crew.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.NoTransactions;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewRelation;
import com.a207.runkk.crew.CrewRole;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 피그마 맞춤 W5(S15P31A207-296, 사용자 결정 C7): 작성자 직책, 반응 CHECK, 반응한 사람 목록, 러닝 완료 시스템 메시지. 계약은
 * docs/sw/maintenance/2026-11-09_피그마맞춤-성/contract-W5.md.
 */
@org.junit.jupiter.api.DisplayName("crew.chat 피그마 W5 — 직책·반응한 사람·시스템 메시지")
class CrewChatFigmaW5Test {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 19, 40);
    private static final long CREW = 7;

    private final InMemoryChatRepositories.Messages messages =
            new InMemoryChatRepositories.Messages();
    private final InMemoryChatRepositories.Reactions reactions =
            new InMemoryChatRepositories.Reactions();
    private final CrewMembershipReader membership = mock(CrewMembershipReader.class);
    private final MemberQueryService memberQuery = mock(MemberQueryService.class);
    private final List<DomainEvent> events = new ArrayList<>();
    private final Map<Long, CrewRelation> relations = new HashMap<>();
    private CrewChatService service;

    @BeforeEach
    void setUp() {
        relations.put(1L, CrewRelation.LEADER);
        relations.put(2L, CrewRelation.MANAGER);
        relations.put(3L, CrewRelation.MEMBER);
        relations.put(4L, CrewRelation.MEMBER);
        when(membership.relationOf(anyLong(), eq(CREW)))
                .thenAnswer(
                        inv -> relations.getOrDefault(inv.getArgument(0), CrewRelation.OUTSIDER));
        when(membership.rolesIn(eq(CREW), anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, CrewRole> out = new HashMap<>();
                            for (Long id : inv.<Collection<Long>>getArgument(1)) {
                                CrewRelation r = relations.get(id);
                                if (r != null && r != CrewRelation.OUTSIDER) {
                                    out.put(id, CrewRole.valueOf(r.name()));
                                }
                            }
                            return out;
                        });
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
        service =
                new CrewChatService(
                        messages,
                        reactions,
                        membership,
                        memberQuery,
                        mock(AuditService.class),
                        events::add,
                        NoTransactions.manager(),
                        Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    private long write(long author, String content) {
        return service.post(author, CREW, new ChatDraft(UUID.randomUUID().toString(), content))
                .message()
                .messageId();
    }

    private static ReasonCode reasonOf(Runnable r) {
        try {
            r.run();
        } catch (BusinessRuleException e) {
            return e.reason();
        }
        return null;
    }

    private ChatPage.Line lineOf(long messageId) {
        return service.history(3, CREW, ChatCursor.of(null, null, null)).messages().stream()
                .filter(l -> l.messageId() == messageId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("반응 종류는 LIKE·HEART·CHECK, 메시지 종류는 TEXT·RUN_FINISHED")
    void enums() {
        assertThat(ReactionKind.values())
                .containsExactly(ReactionKind.LIKE, ReactionKind.HEART, ReactionKind.CHECK);
        assertThat(ChatMessageKind.values())
                .containsExactly(ChatMessageKind.TEXT, ChatMessageKind.RUN_FINISHED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "메시지 줄에 작성자의 지금 직책을 싣고, 크루를 떠났거나 탈퇴한 작성자는 null, 보통 메시지는 TEXT·거리 null")
    void authorRole() {
        long leader = write(1, "크루장 공지");
        long manager = write(2, "운영진");
        long member = write(3, "크루원");
        long leaver = write(4, "곧 떠나요");
        relations.remove(4L);
        long withdrawn = write(3, "탈퇴 예정");
        messages.rows.get(withdrawn).anonymizeAuthor();

        assertThat(lineOf(leader).authorRole()).isEqualTo(CrewRole.LEADER);
        assertThat(lineOf(manager).authorRole()).isEqualTo(CrewRole.MANAGER);
        assertThat(lineOf(member).authorRole()).isEqualTo(CrewRole.MEMBER);
        assertThat(lineOf(leaver).authorRole()).isNull();
        assertThat(lineOf(withdrawn).authorRole()).isNull();
        assertThat(lineOf(member).kind()).isEqualTo(ChatMessageKind.TEXT);
        assertThat(lineOf(member).runDistanceMeters()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("CHECK 반응을 달고 떼고, 수와 내 반응에 CHECK가 보인다")
    void checkReaction() {
        long id = write(1, "출석 체크");
        ReactionView v = service.react(3, id, ReactionKind.CHECK);
        assertThat(v.reactions()).containsEntry(ReactionKind.CHECK, 1);
        assertThat(v.myReactions()).containsExactly(ReactionKind.CHECK);
        assertThat(service.unreact(3, id, ReactionKind.CHECK).reactions()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("반응한 사람: 반응 한 건이 한 줄, 먼저 단 순서, 종류 필터, 본인 표시")
    void reactors() {
        long id = write(1, "내일 7시");
        reactions.add(ChatReaction.of(id, 4, ReactionKind.LIKE, NOW.minusMinutes(3)));
        reactions.add(ChatReaction.of(id, 3, ReactionKind.CHECK, NOW.minusMinutes(2)));
        reactions.add(ChatReaction.of(id, 2, ReactionKind.LIKE, NOW.minusMinutes(2)));
        reactions.add(ChatReaction.of(id, 3, ReactionKind.LIKE, NOW.minusMinutes(1)));

        List<ChatReactor> all = service.reactors(3, id, Optional.empty());
        assertThat(all)
                .extracting(ChatReactor::memberId, ChatReactor::kind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4L, ReactionKind.LIKE),
                        org.assertj.core.groups.Tuple.tuple(2L, ReactionKind.LIKE),
                        org.assertj.core.groups.Tuple.tuple(3L, ReactionKind.CHECK),
                        org.assertj.core.groups.Tuple.tuple(3L, ReactionKind.LIKE));
        assertThat(all.get(0).displayName()).isEqualTo("러너4");
        assertThat(all.get(0).reactedAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3_600);
        assertThat(all)
                .filteredOn(ChatReactor::me)
                .extracting(ChatReactor::memberId)
                .containsOnly(3L);

        assertThat(service.reactors(3, id, Optional.of(ReactionKind.CHECK)))
                .extracting(ChatReactor::memberId)
                .containsExactly(3L);
        assertThat(service.reactors(3, id, Optional.of(ReactionKind.HEART))).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "반응한 사람: 크루 밖은 NOT_CREW_MEMBER, 지운 메시지는 CHAT_MESSAGE_NOT_FOUND")
    void reactors_guards() {
        long id = write(1, "공지");
        assertThat(reasonOf(() -> service.reactors(9, id, Optional.empty())))
                .isEqualTo(ReasonCode.NOT_CREW_MEMBER);
        service.delete(1, id);
        assertThat(reasonOf(() -> service.reactors(3, id, Optional.empty())))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
        assertThat(reasonOf(() -> service.reactors(3, 999, Optional.empty())))
                .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "러닝 완료: 러닝한 크루원 이름으로 RUN_FINISHED를 남기고 거리는 미터, 문장은 소수 둘째 자리 버림, 사건 발행")
    void runFinished() {
        Optional<Long> id =
                service.postRunFinished(
                        CREW, 4, "rec-1", Distance.ofMeters(5_029), NOW.minusMinutes(30));

        assertThat(id).isPresent();
        ChatPage.Line line = lineOf(id.get());
        assertThat(line.kind()).isEqualTo(ChatMessageKind.RUN_FINISHED);
        assertThat(line.authorId()).isEqualTo(4L);
        assertThat(line.authorName()).isEqualTo("러너4");
        assertThat(line.authorRole()).isEqualTo(CrewRole.MEMBER);
        assertThat(line.runDistanceMeters()).isEqualTo(5_029L);
        assertThat(line.content()).isEqualTo("러너4님이 5.02km를 달렸어요");
        assertThat(line.mine()).as("열람자 3에게는 남의 것").isFalse();
        assertThat(events).filteredOn(ChatMessagePosted.class::isInstance).hasSize(1);
        ChatPage.Line own =
                service.history(4, CREW, ChatCursor.of(null, null, null)).messages().get(0);
        assertThat(own.mine()).isTrue();
        assertThat(own.canDelete()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("러닝 완료: 같은 기록은 한 번만, 크루를 떠난 회원·시작이 24시간보다 오래된 기록은 남기지 않는다")
    void runFinished_skips() {
        assertThat(
                        service.postRunFinished(
                                CREW, 4, "rec-1", Distance.ofMeters(5_000), NOW.minusHours(1)))
                .isPresent();
        assertThat(
                        service.postRunFinished(
                                CREW, 4, "rec-1", Distance.ofMeters(5_000), NOW.minusHours(1)))
                .isEmpty();
        assertThat(
                        service.postRunFinished(
                                CREW, 9, "rec-2", Distance.ofMeters(5_000), NOW.minusHours(1)))
                .isEmpty();
        assertThat(
                        service.postRunFinished(
                                CREW, 3, "rec-3", Distance.ofMeters(5_000), NOW.minusHours(25)))
                .isEmpty();
        assertThat(messages.rows).hasSize(1);
        assertThat(events).filteredOn(ChatMessagePosted.class::isInstance).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴: 그 회원의 러닝 완료 메시지는 목록에서 빠지고 보통 메시지는 작성자만 비운다")
    void withdrawal_hidesRunFinished() {
        long text = write(4, "안녕");
        long run =
                service.postRunFinished(
                                CREW, 4, "rec-1", Distance.ofMeters(3_000), NOW.minusHours(1))
                        .orElseThrow();
        messages.anonymizeAuthor(4);

        List<Long> ids =
                service.history(3, CREW, ChatCursor.of(null, null, null)).messages().stream()
                        .map(ChatPage.Line::messageId)
                        .toList();
        assertThat(ids).contains(text).doesNotContain(run);
        assertThat(lineOf(text).authorId()).isNull();
    }
}
