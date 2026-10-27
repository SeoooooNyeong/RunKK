package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/**
 * 투표 응답 한 건(FR-207). 표 crew_poll_response(PK(post_id, member_id), FK(post_id, option_no) →
 * crew_poll_option). 회원당 한 응답이고 마감 전에는 바꿀 수 있다. 응답자는 익명이 아니다.
 */
@Entity
@Table(name = "crew_poll_response")
public class PollResponse {

    @EmbeddedId private Key key;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private PollPost post;

    @Column(name = "option_no", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int optionNo;

    @Column(name = "responded_at", nullable = false)
    private LocalDateTime respondedAt;

    protected PollResponse() {}

    PollResponse(PollPost post, long memberId, int optionNo, LocalDateTime now) {
        this.key = new Key(post.getId(), memberId);
        this.post = post;
        this.optionNo = optionNo;
        this.respondedAt = now;
    }

    void change(int newOptionNo, LocalDateTime now) {
        this.optionNo = newOptionNo;
        this.respondedAt = now;
    }

    public long memberId() {
        return key.memberId;
    }

    public int optionNo() {
        return optionNo;
    }

    public LocalDateTime respondedAt() {
        return respondedAt;
    }

    /** 복합 키(post_id, member_id). */
    @Embeddable
    static class Key implements Serializable {

        @Column(name = "post_id")
        private Long postId;

        @Column(name = "member_id", nullable = false)
        private long memberId;

        protected Key() {}

        Key(Long postId, long memberId) {
            this.postId = postId;
            this.memberId = memberId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other
                    && Objects.equals(postId, other.postId)
                    && memberId == other.memberId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, memberId);
        }
    }
}
