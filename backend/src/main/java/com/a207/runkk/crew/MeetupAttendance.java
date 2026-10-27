package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 대면 모임 참석 응답 한 건(설계 6.1.7, FR-145, D-99). 표 crew_meetup_attendance(PK(post_id, member_id), 글 삭제 시
 * CASCADE). MeetupPost가 합성으로 가진다.
 */
@Entity
@Table(name = "crew_meetup_attendance")
public class MeetupAttendance {

    @EmbeddedId private Key key;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MeetupPost post;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer", nullable = false, length = 10)
    private AttendanceAnswer answer;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    protected MeetupAttendance() {}

    MeetupAttendance(MeetupPost post, long memberId, AttendanceAnswer answer, LocalDateTime now) {
        this.key = new Key(null, memberId);
        this.post = post;
        this.answer = answer;
        this.answeredAt = now;
    }

    void change(AttendanceAnswer newAnswer, LocalDateTime now) {
        this.answer = newAnswer;
        this.answeredAt = now;
    }

    public long memberId() {
        return key.memberId;
    }

    public AttendanceAnswer answer() {
        return answer;
    }

    public LocalDateTime answeredAt() {
        return answeredAt;
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
                    && java.util.Objects.equals(postId, other.postId)
                    && memberId == other.memberId;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(postId, memberId);
        }
    }
}
