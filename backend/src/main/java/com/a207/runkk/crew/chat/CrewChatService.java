package com.a207.runkk.crew.chat;

import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewMembershipReader;
import com.a207.runkk.crew.CrewRelation;
import com.a207.runkk.crew.chat.live.ChatRemovalReason;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 크루 채팅 유스케이스(보내기·조회·삭제·리액션)를 조정한다(설계 6.1.7a, D-101). 크루를 떠난 회원의 접근은 읽을 때마다 relationOf로 막는다. 실시간
 * 전송과 방 밖 알림은 커밋 뒤 ChatDeliveryListener가 하고, 이 서비스는 도메인 사건만 발행한다(AfterCommit 사본 없음, D-132). 회원당 초당
 * 5건 제한은 web.chat ChatRateGuard가 서비스 호출 전에 건다.
 */
@Service
public class CrewChatService {

    /** 신고 미리보기 글자 수(코드 포인트). */
    static final int PREVIEW_LENGTH = 100;

    private final CrewChatMessageRepository messages;
    private final ChatReactionRepository reactions;
    private final CrewMembershipReader membership;
    private final MemberQueryService memberQuery;
    private final AuditService audit;
    private final DomainEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public CrewChatService(
            CrewChatMessageRepository messages,
            ChatReactionRepository reactions,
            CrewMembershipReader membership,
            MemberQueryService memberQuery,
            AuditService audit,
            DomainEventPublisher events,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.messages = messages;
        this.reactions = reactions;
        this.membership = membership;
        this.memberQuery = memberQuery;
        this.audit = audit;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 보내기(FR-208). 크루 밖이면 NOT_CREW_MEMBER. 같은 작성자의 같은 clientMessageId가 있으면 그 메시지를 created 거짓으로(멱등).
     * 동시 재전송이 고유 색인(uq_crew_chat_client)에 걸리면 그 트랜잭션을 버리고 새 트랜잭션에서 다시 읽어 같은 결과를 준다(5.3). 저장하면
     * ChatMessagePosted를 발행한다(커밋 뒤 전송·알림).
     */
    public ChatPostResult post(long memberId, long crewId, ChatDraft draft) {
        try {
            return transactions.execute(status -> postOnce(memberId, crewId, draft));
        } catch (DataIntegrityViolationException e) {
            ChatPostResult again =
                    transactions.execute(
                            status ->
                                    messages.findByClientMessageId(
                                                    memberId, draft.clientMessageId())
                                            .map(m -> resent(m, crewId, memberId))
                                            .orElse(null));
            if (again == null) {
                throw e;
            }
            return again;
        }
    }

    private ChatPostResult postOnce(long memberId, long crewId, ChatDraft draft) {
        requireMember(memberId, crewId);
        Optional<CrewChatMessage> existing =
                messages.findByClientMessageId(memberId, draft.clientMessageId());
        if (existing.isPresent()) {
            return resent(existing.get(), crewId, memberId);
        }
        CrewChatMessage saved =
                messages.save(CrewChatMessage.write(crewId, memberId, draft, now()));
        events.publish(new ChatMessagePosted(crewId, saved.id(), memberId, now()));
        return new ChatPostResult(lineOf(saved, memberId), true);
    }

    /**
     * 재전송 응답: 처음 만든 메시지를 created 거짓으로 준다. 같은 clientMessageId가 다른 크루의 메시지에 쓰였으면 앱 오류라 400으로 거부한다 (다른
     * 크루 메시지를 돌려주거나 새 메시지를 조용히 버리지 않는다, 리뷰 M3).
     */
    private ChatPostResult resent(CrewChatMessage existing, long crewId, long memberId) {
        if (existing.crewId() != crewId) {
            throw new IllegalArgumentException("clientMessageId가 다른 크루의 메시지에 쓰였다");
        }
        return new ChatPostResult(lineOf(existing, memberId), false);
    }

    /** 본인 메시지 한 줄(보내기 응답). 방금 쓴 메시지라 리액션은 없을 수 있어 한 번 읽는다. */
    private ChatPage.Line lineOf(CrewChatMessage m, long memberId) {
        return lines(List.of(m), memberId, CrewRelation.MEMBER).get(0);
    }

    /**
     * 지난 메시지(FR-209, 6.2 A-25). 크루 밖·떠난 회원은 NOT_CREW_MEMBER. 보이는 메시지만 커서로 size + 1 읽어 hasMore를 정하고
     * 늘 오래된 → 새 순으로 준다. 가입 전 메시지도 보인다. 작성자 이름·리액션은 묶어서 한 번씩 읽는다(N+1 없음).
     */
    @Transactional(readOnly = true)
    public ChatPage history(long memberId, long crewId, ChatCursor cursor) {
        CrewRelation relation = requireMember(memberId, crewId);
        List<CrewChatMessage> rows = messages.findPage(crewId, cursor);
        boolean hasMore = rows.size() > cursor.size();
        List<CrewChatMessage> page =
                new ArrayList<>(rows.subList(0, Math.min(rows.size(), cursor.size())));
        if (cursor.after() == null) {
            Collections.reverse(page);
        }
        return new ChatPage(lines(page, memberId, relation), hasMore);
    }

    /**
     * 삭제(FR-213). 없거나 이미 삭제·숨김이면 CHAT_MESSAGE_NOT_FOUND(D-126). 크루를 떠난·강퇴된 회원은 본인 글도
     * NOT_CREW_MEMBER. 작성자 본인 또는 크루장·운영진만 지우고, 남의 메시지면 감사 CHAT_FORCE_DELETE(행위자 역할
     * CREW_LEADER·CREW_MANAGER)를 남긴다. 커밋 뒤 방 안에 CrewChatRemoved(DELETED).
     */
    @Transactional
    public void delete(long memberId, long messageId) {
        CrewChatMessage message =
                messages.findByIdForUpdate(messageId)
                        .filter(CrewChatMessage::isVisible)
                        .orElseThrow(
                                () -> new BusinessRuleException(ReasonCode.CHAT_MESSAGE_NOT_FOUND));
        CrewRelation relation = requireMember(memberId, message.crewId());
        message.delete(memberId, relation);
        messages.save(message);
        if (!message.isAuthor(memberId)) {
            audit.record(
                    new AuditEntry(
                            String.valueOf(memberId),
                            relation == CrewRelation.LEADER
                                    ? ActorRole.CREW_LEADER
                                    : ActorRole.CREW_MANAGER,
                            AuditAction.CHAT_FORCE_DELETE,
                            String.valueOf(messageId),
                            message.authorId() == null ? null : String.valueOf(message.authorId()),
                            null));
        }
        events.publish(
                new ChatMessageRemoved(
                        message.crewId(), messageId, ChatRemovalReason.DELETED, now()));
    }

    /** 리액션 달기(FR-210). 이미 있으면 그대로(멱등). 바뀌었을 때만 커밋 뒤 방 안에 CrewChatReactionChanged. */
    @Transactional
    public ReactionView react(long memberId, long messageId, ReactionKind kind) {
        CrewChatMessage message = requireVisible(messageId);
        requireMember(memberId, message.crewId());
        if (reactions.add(ChatReaction.of(messageId, memberId, kind, now()))) {
            events.publish(new ChatReactionsChanged(message.crewId(), messageId, now()));
        }
        return viewOf(messageId, memberId);
    }

    /** 리액션 떼기(FR-210). 없으면 그대로. 바뀌었을 때만 커밋 뒤 방 안에 CrewChatReactionChanged. */
    @Transactional
    public ReactionView unreact(long memberId, long messageId, ReactionKind kind) {
        CrewChatMessage message = requireVisible(messageId);
        requireMember(memberId, message.crewId());
        if (reactions.remove(messageId, memberId, kind)) {
            events.publish(new ChatReactionsChanged(message.crewId(), messageId, now()));
        }
        return viewOf(messageId, memberId);
    }

    /** 채팅방 입장 확인(realtime이 ChatPresence(present 참)를 받을 때, N11). 크루 밖이면 NOT_CREW_MEMBER. */
    @Transactional(readOnly = true)
    public void checkRoomAccess(long memberId, long crewId) {
        requireMember(memberId, crewId);
    }

    /** 신고용 조회(report.target만, D-135). 없거나 삭제됐으면 빈 값, 숨김이어도 돌려준다. */
    @Transactional(readOnly = true)
    public Optional<ReportedChatView> reportedView(long messageId) {
        return messages.findById(messageId)
                .filter(m -> !m.isDeleted())
                .map(
                        m ->
                                new ReportedChatView(
                                        m.id(),
                                        m.crewId(),
                                        m.authorId(),
                                        preview(m.content()),
                                        m.isHidden()));
    }

    /**
     * 신고 숨김(report.target만, FR-215). 처음 숨길 때만 커밋 뒤 방 안에 CrewChatRemoved(HIDDEN). 없거나 삭제됐으면 그대로. 행을
     * 잠그고 읽어 겹친 삭제를 덮어쓰지 않는다(잠금 순서 report_case → 메시지 행, A-24).
     */
    @Transactional
    public void hideReported(long messageId) {
        messages.findByIdForUpdate(messageId)
                .filter(CrewChatMessage::isVisible)
                .ifPresent(
                        m -> {
                            m.hide();
                            messages.save(m);
                            events.publish(
                                    new ChatMessageRemoved(
                                            m.crewId(), m.id(), ChatRemovalReason.HIDDEN, now()));
                        });
    }

    /** 신고 숨김 해제(report.target만, FR-216). 행을 잠그고 읽어 겹친 삭제를 덮어쓰지 않는다. */
    @Transactional
    public void unhideReported(long messageId) {
        messages.findByIdForUpdate(messageId)
                .ifPresent(
                        m -> {
                            m.unhide();
                            messages.save(m);
                        });
    }

    private CrewRelation requireMember(long memberId, long crewId) {
        CrewRelation relation = membership.relationOf(memberId, crewId);
        if (relation == CrewRelation.OUTSIDER) {
            throw new BusinessRuleException(ReasonCode.NOT_CREW_MEMBER);
        }
        return relation;
    }

    private CrewChatMessage requireVisible(long messageId) {
        return messages.findById(messageId)
                .filter(CrewChatMessage::isVisible)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.CHAT_MESSAGE_NOT_FOUND));
    }

    private ReactionView viewOf(long messageId, long memberId) {
        List<Long> ids = List.of(messageId);
        return new ReactionView(
                messageId,
                ordered(reactions.countsOf(ids).getOrDefault(messageId, Map.of())),
                reactions.mineOf(memberId, ids).getOrDefault(messageId, Set.of()));
    }

    private List<ChatPage.Line> lines(
            List<CrewChatMessage> page, long memberId, CrewRelation relation) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> ids = page.stream().map(CrewChatMessage::id).toList();
        Map<Long, Map<ReactionKind, Integer>> counts = reactions.countsOf(ids);
        Map<Long, Set<ReactionKind>> mine = reactions.mineOf(memberId, ids);
        Map<Long, MemberSummary> names =
                memberQuery.summaries(
                        page.stream()
                                .map(CrewChatMessage::authorId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList());
        return page.stream()
                .map(
                        m -> {
                            boolean own = m.isAuthor(memberId);
                            return new ChatPage.Line(
                                    m.id(),
                                    m.authorId(),
                                    authorName(m.authorId(), names),
                                    m.content(),
                                    m.sentAtIn(clock.getZone()),
                                    ordered(counts.getOrDefault(m.id(), Map.of())),
                                    mine.getOrDefault(m.id(), Set.of()),
                                    own,
                                    CrewChatMessage.mayDelete(own, relation),
                                    own ? m.clientMessageId() : null);
                        })
                .toList();
    }

    /** 작성자 표시명. 탈퇴(작성자 없음)거나 회원을 찾지 못하면 "탈퇴한 사용자". */
    static String authorName(Long authorId, Map<Long, MemberSummary> names) {
        if (authorId == null || !names.containsKey(authorId)) {
            return DisplayName.WITHDRAWN.value();
        }
        return names.get(authorId).displayName().value();
    }

    /** 0인 종류를 빼고 종류 선언 순서로 둔다. */
    static Map<ReactionKind, Integer> ordered(Map<ReactionKind, Integer> counts) {
        Map<ReactionKind, Integer> out = new EnumMap<>(ReactionKind.class);
        counts.forEach(
                (kind, n) -> {
                    if (n != null && n > 0) {
                        out.put(kind, n);
                    }
                });
        return out;
    }

    /** 앞 100자(코드 포인트 경계에서 자른다, D-143). */
    static String preview(String content) {
        int count = content.codePointCount(0, content.length());
        if (count <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, PREVIEW_LENGTH));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
