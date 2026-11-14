package com.a207.runkk.crew.chat;

import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewRole;
import com.a207.runkk.crew.chat.live.CrewChatPosted;
import com.a207.runkk.crew.chat.live.CrewChatReactionChanged;
import com.a207.runkk.crew.chat.live.CrewChatRemoved;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커밋 뒤 채팅 전송(설계 6.1.7a, 5.7.2 채팅 수신 규칙, D-101, D-102). 받는 사람은 방 안(ChatRoomPresence) ∩ 현재 구성원이고 크루를
 * 떠난 회원은 방 상태가 남아 있어도 받지 못한다. 새 메시지는 요청 스레드에서 곧바로 방 안에 보낸다(NFR-040). 이때 읽기는 막 커밋한 트랜잭션의 영속성 문맥과 연결을
 * 그대로 쓰고 연결을 더 빌리지 않는다. 방 밖 크루원(구성원 − 방 안 − 작성자)의 CREW_CHAT 묶음 알림은 수신자마다 DB 쓰기와 FCM 호출이 따르므로 애플리케이션
 * 실행기(applicationTaskExecutor)로 넘겨 수신자마다 새 트랜잭션에서 보낸다(요청 스레드가 연결 둘을 쥐지 않고 보내기 응답도 늦어지지 않는다, 리뷰
 * H1·H2). 러닝 완료 시스템 메시지(RUN_FINISHED, W5)는 방 안에만 보내고 방 밖 알림은 없다. 수신 한 건의 실패는 로그만 남기고 이미 커밋된 요청과 다른
 * 수신자를 막지 않는다. 연결이 없던 회원은 앱이 재연결 뒤 ?after=로 메운다.
 */
@Component
class ChatDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(ChatDeliveryListener.class);

    private final CrewChatMessageRepository messages;
    private final ChatReactionRepository reactions;
    private final CrewMembershipReader membership;
    private final MemberQueryService memberQuery;
    private final ChatAuthorFaces authorFaces;
    private final ChatRoomPresence presence;
    private final CrewChatMessenger messenger;
    private final NotificationService notifications;
    private final TransactionTemplate newTransaction;
    private final Executor notifier;
    private final Clock clock;

    ChatDeliveryListener(
            CrewChatMessageRepository messages,
            ChatReactionRepository reactions,
            CrewMembershipReader membership,
            MemberQueryService memberQuery,
            ChatAuthorFaces authorFaces,
            ChatRoomPresence presence,
            CrewChatMessenger messenger,
            NotificationService notifications,
            PlatformTransactionManager transactionManager,
            @Qualifier("applicationTaskExecutor") Executor notifier,
            Clock clock) {
        this.messages = messages;
        this.reactions = reactions;
        this.membership = membership;
        this.memberQuery = memberQuery;
        this.authorFaces = authorFaces;
        this.presence = presence;
        this.messenger = messenger;
        this.notifications = notifications;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.notifier = notifier;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPosted(ChatMessagePosted e) {
        try {
            Optional<CrewChatMessage> found =
                    messages.findById(e.messageId()).filter(CrewChatMessage::isVisible);
            if (found.isEmpty()) {
                return;
            }
            CrewChatMessage message = found.get();
            Set<Long> active = new HashSet<>(membership.activeMemberIds(e.crewId()));
            Set<Long> inRoom = inRoom(e.crewId(), active);
            String authorName =
                    CrewChatService.authorName(
                            message.authorId(),
                            message.authorId() == null
                                    ? Map.of()
                                    : memberQuery.summaries(List.of(message.authorId())));
            ChatAuthorFaces.Face face = authorFace(message.authorId());
            send(
                    inRoom,
                    new CrewChatPosted(
                            e.crewId(),
                            message.id(),
                            message.authorId(),
                            authorName,
                            message.content(),
                            message.sentAtIn(clock.getZone()),
                            message.clientMessageId(),
                            message.kind(),
                            roleName(e.crewId(), message.authorId()),
                            message.runDistanceMeters(),
                            face.faceKey(),
                            face.shownGender()));
            if (message.isSystem()) {
                return; // 러닝 완료 시스템 메시지는 방 밖 푸시를 보내지 않는다(W5 결정)
            }
            active.removeAll(inRoom);
            active.remove(e.authorId());
            notifyOutside(
                    List.copyOf(active),
                    e.crewId(),
                    authorName + ": " + CrewChatService.preview(message.content()));
        } catch (RuntimeException failure) {
            log.error(
                    "chat_delivery_failed event=posted messageId={} error={}",
                    e.messageId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRemoved(ChatMessageRemoved e) {
        try {
            send(
                    inRoom(e.crewId(), new HashSet<>(membership.activeMemberIds(e.crewId()))),
                    new CrewChatRemoved(e.crewId(), e.messageId(), e.reason()));
        } catch (RuntimeException failure) {
            log.error(
                    "chat_delivery_failed event=removed messageId={} error={}",
                    e.messageId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReactionsChanged(ChatReactionsChanged e) {
        try {
            Map<ReactionKind, Integer> counts =
                    reactions
                            .countsOf(List.of(e.messageId()))
                            .getOrDefault(e.messageId(), Map.of());
            send(
                    inRoom(e.crewId(), new HashSet<>(membership.activeMemberIds(e.crewId()))),
                    new CrewChatReactionChanged(
                            e.crewId(), e.messageId(), CrewChatService.ordered(counts)));
        } catch (RuntimeException failure) {
            log.error(
                    "chat_delivery_failed event=reactions messageId={} error={}",
                    e.messageId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    /** 작성자의 지금 크루 역할 이름(크루장 배지, W5). 탈퇴했거나 크루를 떠났으면 null. */
    private String roleName(long crewId, Long authorId) {
        if (authorId == null) {
            return null;
        }
        CrewRole role = membership.rolesIn(crewId, List.of(authorId)).get(authorId);
        return role == null ? null : role.name();
    }

    /**
     * 작성자 얼굴(설계 5.31 [임시:D-성-229]). 받는 사람마다 따로 만들지 않도록 작성자 본인 기준으로 한 번 읽는다(탈퇴가 아니면 누구에게나 같은 값이다).
     * 탈퇴 작성자는 둘 다 null.
     */
    private ChatAuthorFaces.Face authorFace(Long authorId) {
        if (authorId == null) {
            return ChatAuthorFaces.Face.NONE;
        }
        return authorFaces
                .of(authorId, List.of(authorId))
                .getOrDefault(authorId, ChatAuthorFaces.Face.NONE);
    }

    private Set<Long> inRoom(long crewId, Set<Long> active) {
        Set<Long> present = new HashSet<>(presence.presentMembers(crewId));
        present.retainAll(active);
        return present;
    }

    private void send(Set<Long> to, CrewChatSignal signal) {
        if (to.isEmpty()) {
            return;
        }
        try {
            messenger.send(to, signal);
        } catch (RuntimeException failure) {
            log.warn(
                    "chat_send_failed type={} error={}",
                    signal.type(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    /** 방 밖 크루원 알림을 실행기로 넘긴다. 실행기가 받지 못하면 알림 없이 로그만 남긴다(메시지는 ?after=로 읽힌다). */
    private void notifyOutside(List<Long> recipients, long crewId, String content) {
        if (recipients.isEmpty()) {
            return;
        }
        NavigationTarget target = new NavigationTarget(Screen.CREW_CHAT, String.valueOf(crewId));
        try {
            notifier.execute(
                    () ->
                            recipients.forEach(
                                    recipient ->
                                            notify(
                                                    new NotificationRequest(
                                                            recipient,
                                                            NotificationType.CREW_CHAT,
                                                            content,
                                                            target))));
        } catch (RejectedExecutionException rejected) {
            log.warn("chat_notify_rejected crewId={} recipients={}", crewId, recipients.size());
        }
    }

    private void notify(NotificationRequest request) {
        try {
            newTransaction.executeWithoutResult(status -> notifications.notifyCollapsing(request));
        } catch (RuntimeException failure) {
            log.warn(
                    "chat_notify_failed recipient={} error={}",
                    request.recipientId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }
}
