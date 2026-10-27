package com.a207.runkk.crew.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 채팅 메시지에 회원이 단 리액션 하나(설계 6.1.7a, FR-210). 기본 키 (message_id, member_id, kind)라 같은 리액션은 한 번만 있다(멱등
 * upsert, 5.3).
 */
@Entity
@Table(name = "crew_chat_reaction")
@IdClass(ChatReaction.Key.class)
public class ChatReaction {

    @Id
    @Column(name = "message_id")
    private Long messageId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10)
    private ReactionKind kind;

    @Column(name = "reacted_at", nullable = false, updatable = false)
    private LocalDateTime reactedAt;

    protected ChatReaction() {}

    private ChatReaction(long messageId, long memberId, ReactionKind kind, LocalDateTime at) {
        this.messageId = messageId;
        this.memberId = memberId;
        this.kind = kind;
        this.reactedAt = at;
    }

    public static ChatReaction of(
            long messageId, long memberId, ReactionKind kind, LocalDateTime reactedAt) {
        if (kind == null || reactedAt == null) {
            throw new IllegalArgumentException("리액션 종류·일시가 비었다");
        }
        return new ChatReaction(messageId, memberId, kind, reactedAt);
    }

    public long messageId() {
        return messageId;
    }

    public long memberId() {
        return memberId;
    }

    public ReactionKind kind() {
        return kind;
    }

    public LocalDateTime reactedAt() {
        return reactedAt;
    }

    /** JPA 복합 기본 키 (message_id, member_id, kind). */
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long messageId;
        private Long memberId;
        private ReactionKind kind;

        protected Key() {}

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k
                    && Objects.equals(messageId, k.messageId)
                    && Objects.equals(memberId, k.memberId)
                    && kind == k.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, memberId, kind);
        }
    }
}
