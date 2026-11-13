package com.a207.runkk.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.a207.runkk.TestcontainersConfig;
import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.crew.chat.ChatCursor;
import com.a207.runkk.crew.chat.ChatDraft;
import com.a207.runkk.crew.chat.ChatPage;
import com.a207.runkk.crew.chat.ChatPostResult;
import com.a207.runkk.crew.chat.CrewChatService;
import com.a207.runkk.crew.chat.CrewChatSignal;
import com.a207.runkk.crew.chat.ReactionKind;
import com.a207.runkk.crew.chat.live.CrewChatPosted;
import com.a207.runkk.crew.chat.live.CrewChatRemoved;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 14장 통합 T-27 크루 채팅 스레드(SQ-21) 서버 부분. 실 PostgreSQL·Redis에서 crew 실제(크루원 a·b·c·d, 크루장 L, 떠난 x) →
 * CrewChatService → infra.chat → 커밋 뒤 ChatDeliveryListener → ChatPresenceRegistry ∩ 현재 구성원 →
 * WebSocketMessenger, 방 밖 → NotificationService.notifyCollapsing. TC-6604, TC-6610, TC-6691,
 * TC-6692, TC-6697, TC-6705~TC-6707.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@org.junit.jupiter.api.DisplayName("크루 채팅 스레드 통합(T-27, PostgreSQL)")
class ChatThreadIntegrationTest {

    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final long WAIT_MILLIS = 5_000;

    /** 겹침 시험에서 다른 트랜잭션이 행 잠금을 붙잡는 시간. 이 사이 시험 스레드가 잠금 앞에서 기다린다. */
    private static final long HOLD_MILLIS = 500;

    @MockitoSpyBean WebSocketMessenger messenger;
    @MockitoSpyBean NotificationService notifications;
    @Autowired CrewChatService chat;
    @Autowired ChatPresenceRegistry presence;
    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired MemberRepository members;
    @Autowired DomainEventPublisher events;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private long leader;
    private long a;
    private long b;
    private long c;
    private long d;
    private long x;
    private long crewId;

    private long member() {
        String name = "채팅러너" + NAMES.incrementAndGet();
        return tx.execute(s -> members.save(MemberFixtures.member(name)).getId());
    }

    @BeforeEach
    void crewWithRoom() {
        leader = member();
        crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "채팅통합" + NAMES.incrementAndGet(),
                                        "함께",
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        a = member();
        b = member();
        c = member();
        d = member();
        x = member();
        for (long m : List.of(a, b, c, d, x)) {
            membership.apply(m, JoinTarget.byCrew(crewId));
        }
        membership.leave(x, crewId);
        for (long m : List.of(a, b, c, x)) {
            presence.enter(crewId, m, "session-" + m);
        }
        clearInvocations(messenger, notifications);
    }

    private static ChatDraft draft(String content) {
        return new ChatDraft(UUID.randomUUID().toString(), content);
    }

    /** 방 밖 알림은 실행기에서 비동기로 저장된다. */
    private void awaitRows(long recipient, int expected) {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (chatRows(recipient) != expected && System.currentTimeMillis() < deadline) {
            LockSupport.parkNanos(20_000_000L);
        }
        assertThat(chatRows(recipient)).isEqualTo(expected);
    }

    private int chatRows(long recipient) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_id = ? and type = 'CREW_CHAT'",
                Integer.class,
                recipient);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6691·TC-6604 커밋 뒤 방 안 ∩ 현재 구성원 {a, b, c}에 CrewChatPosted 1통, 떠난 x 0, 방 밖 L·d에"
                    + " CREW_CHAT, 재전송은 추가 0")
    void post_deliversToRoomAndNotifiesOutside() {
        ChatDraft first = draft("내일 7시 출발");
        ChatPostResult result = chat.post(a, crewId, first);
        assertThat(result.created()).isTrue();

        verify(messenger, times(1))
                .send(
                        argThat((Collection<Long> ids) -> Set.copyOf(ids).equals(Set.of(a, b, c))),
                        argThat(
                                (CrewChatSignal s) ->
                                        s instanceof CrewChatPosted p
                                                && p.clientMessageId()
                                                        .equals(first.clientMessageId())));
        verify(notifications, timeout(WAIT_MILLIS).times(2)).notifyCollapsing(any());
        verify(notifications)
                .notifyCollapsing(
                        argThat(
                                r ->
                                        r.recipientId() == d
                                                && r.type() == NotificationType.CREW_CHAT));
        awaitRows(d, 1);
        awaitRows(leader, 1);
        assertThat(chatRows(a) + chatRows(b) + chatRows(c) + chatRows(x)).isZero();

        clearInvocations(messenger, notifications);
        ChatPostResult again = chat.post(a, crewId, first);
        assertThat(again.created()).isFalse();
        assertThat(again.message().messageId()).isEqualTo(result.message().messageId());
        verify(messenger, never()).send(any(Collection.class), any(CrewChatSignal.class));
        verify(notifications, never()).notifyCollapsing(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6610 같은 clientMessageId 두 요청이 동시에 와도 행 1, 하나만 created, 같은 messageId, 전송 1회")
    void concurrentResend_isIdempotent() throws Exception {
        ChatDraft same = draft("동시에");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ChatPostResult>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Callable<ChatPostResult> work =
                    () -> {
                        start.await();
                        return chat.post(a, crewId, same);
                    };
            futures.add(pool.submit(work));
        }
        start.countDown();
        ChatPostResult one = futures.get(0).get();
        ChatPostResult two = futures.get(1).get();
        pool.shutdown();

        assertThat(one.message().messageId()).isEqualTo(two.message().messageId());
        assertThat(List.of(one.created(), two.created())).containsExactlyInAnyOrder(true, false);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where client_message_id ="
                                        + " ?",
                                Integer.class,
                                same.clientMessageId()))
                .isEqualTo(1);
        verify(messenger, times(1)).send(any(Collection.class), any(CrewChatSignal.class));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6697 방 밖 d: 3건 뒤 읽지 않은 CREW_CHAT 행 1(마지막 내용), 읽은 뒤 새 메시지는 새 행")
    void outsideMember_getsOneCollapsedRow() {
        chat.post(a, crewId, draft("하나"));
        chat.post(b, crewId, draft("둘"));
        chat.post(c, crewId, draft("셋"));
        verify(notifications, timeout(WAIT_MILLIS).times(6)).notifyCollapsing(any());
        awaitRows(d, 1);
        assertThat(
                        jdbc.queryForObject(
                                "select content from notification where recipient_id = ?"
                                        + " and type = 'CREW_CHAT'",
                                String.class,
                                d))
                .matches(".+: (하나|둘|셋)"); // 알림은 실행기에서 저장되어 묶인 내용의 순서는 보장하지 않는다

        jdbc.update("update notification set is_read = true where recipient_id = ?", d);
        chat.post(a, crewId, draft("넷"));
        awaitRows(d, 2);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6707 커밋 직전에 실패하면 행 0, 전송 0, 알림 0(AFTER_COMMIT)")
    void rollback_sendsNothing() {
        ChatDraft lost = draft("사라질 메시지");
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            chat.post(a, crewId, lost);
                                            throw new IllegalStateException("커밋 직전 실패");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where client_message_id ="
                                        + " ?",
                                Integer.class,
                                lost.clientMessageId()))
                .isZero();
        verify(messenger, never()).send(any(Collection.class), any(CrewChatSignal.class));
        verify(notifications, never()).notifyCollapsing(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-6705 탈퇴: 메시지는 남고 작성자 없음·\"탈퇴한 사용자\", 그 회원 리액션 0, 다른 회원 리액션 그대로")
    void withdrawal_anonymizesAndRemovesReactions() {
        long m1 = chat.post(a, crewId, draft("첫째")).message().messageId();
        chat.post(a, crewId, draft("둘째"));
        chat.react(a, m1, ReactionKind.LIKE);
        chat.react(b, m1, ReactionKind.HEART);

        tx.executeWithoutResult(s -> events.publish(new MemberWithdrawn(a, LocalDateTime.now())));

        ChatPage page = chat.history(b, crewId, ChatCursor.of(null, null, null));
        assertThat(page.messages()).hasSize(2);
        assertThat(page.messages()).allMatch(l -> l.authorId() == null);
        assertThat(page.messages()).allMatch(l -> l.authorName().equals("탈퇴한 사용자"));
        assertThat(page.messages().get(0).reactions()).containsOnlyKeys(ReactionKind.HEART);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-6706 크루를 지우면 메시지·리액션 행이 FK CASCADE로 없어진다")
    void crewDeletion_removesChat() {
        long m1 = chat.post(a, crewId, draft("곧 사라질 크루")).message().messageId();
        chat.react(b, m1, ReactionKind.LIKE);
        crews.delete(leader, crewId);
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_message where crew_id = ?",
                                Integer.class,
                                crewId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from crew_chat_reaction where message_id = ?",
                                Integer.class,
                                m1))
                .isZero();
    }

    /**
     * 다른 트랜잭션이 메시지 행을 FOR UPDATE로 잠그고 change를 실행한 뒤 holdMillis 동안 붙잡았다가 커밋한다. 잠그고 바꾼 뒤 locked가 풀린다.
     */
    private Future<?> holdRow(
            ExecutorService pool, long messageId, String change, CountDownLatch locked) {
        return pool.submit(
                () ->
                        tx.executeWithoutResult(
                                s -> {
                                    jdbc.queryForObject(
                                            "select id from crew_chat_message where id = ? for"
                                                    + " update",
                                            Long.class,
                                            messageId);
                                    jdbc.update(change, messageId);
                                    locked.countDown();
                                    LockSupport.parkNanos(HOLD_MILLIS * 1_000_000L);
                                }));
    }

    private boolean flag(long messageId, String column) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select " + column + " from crew_chat_message where id = ?",
                        Boolean.class,
                        messageId));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("신고 숨김은 행을 잠그고 읽어, 겹친 삭제가 먼저 커밋되면 숨기지 않고 삭제를 되돌리지 않는다")
    void hideReported_waitsForConcurrentDelete() throws Exception {
        long m1 = chat.post(a, crewId, draft("지워질 메시지")).message().messageId();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch locked = new CountDownLatch(1);
            Future<?> deleting =
                    holdRow(
                            pool,
                            m1,
                            "update crew_chat_message set deleted = true where id = ?",
                            locked);
            assertThat(locked.await(WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isTrue();
            clearInvocations(messenger);
            chat.hideReported(m1);
            deleting.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(flag(m1, "deleted")).isTrue();
        assertThat(flag(m1, "hidden")).isFalse();
        verify(messenger, never())
                .send(any(Collection.class), argThat(s -> s instanceof CrewChatRemoved));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "삭제는 행을 잠그고 읽어, 겹친 신고 숨김이 먼저 커밋되면 CHAT_MESSAGE_NOT_FOUND이고 숨김이 남는다")
    void delete_waitsForConcurrentHide() throws Exception {
        long m1 = chat.post(a, crewId, draft("숨겨질 메시지")).message().messageId();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch locked = new CountDownLatch(1);
            Future<?> hiding =
                    holdRow(
                            pool,
                            m1,
                            "update crew_chat_message set hidden = true where id = ?",
                            locked);
            assertThat(locked.await(WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isTrue();
            assertThatThrownBy(() -> chat.delete(a, m1))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.CHAT_MESSAGE_NOT_FOUND);
            hiding.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(flag(m1, "hidden")).isTrue();
        assertThat(flag(m1, "deleted")).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("신고 숨김 해제는 행을 잠그고 읽어, 겹친 삭제가 먼저 커밋되면 삭제를 되돌리지 않는다")
    void unhideReported_waitsForConcurrentDelete() throws Exception {
        long m1 = chat.post(a, crewId, draft("숨김 뒤 지워질 메시지")).message().messageId();
        chat.hideReported(m1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch locked = new CountDownLatch(1);
            Future<?> deleting =
                    holdRow(
                            pool,
                            m1,
                            "update crew_chat_message set deleted = true where id = ?",
                            locked);
            assertThat(locked.await(WAIT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isTrue();
            chat.unhideReported(m1);
            deleting.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(flag(m1, "deleted")).isTrue();
        assertThat(flag(m1, "hidden")).isFalse();
    }
}
