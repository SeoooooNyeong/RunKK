package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.time.LocalDateTime;

/**
 * 일시와 참석 응답을 가진 크루 일정의 공통 규칙(설계 6.1.7, D-99). 하위 클래스는 CrewRunPost(크루런)와 MeetupPost(대면 모임)이고 참석 응답은
 * 다형성이다: 크루런은 크루런 참가 규칙, 대면 모임은 정원 규칙(6.4). 대면 모임을 크루런 하위로 두지 않는다(대기실·출발·중계 상태를 물려받지 않게, 1.3.7).
 */
@Entity
public abstract class SchedulePost extends CrewPost {

    /** 일정일시(크루런은 모집희망일시). */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    protected SchedulePost() {}

    /**
     * 일정 글의 본문과 일시.
     *
     * @param body 본문
     * @param scheduledAt 일정일시
     */
    protected record Slot(String body, LocalDateTime scheduledAt) {}

    protected SchedulePost(long crewId, long authorId, Slot slot, LocalDateTime now) {
        super(crewId, authorId, slot.body(), now);
        this.scheduledAt = slot.scheduledAt();
    }

    /** 참석·불참·미정 응답. 있으면 바꾸고 없으면 더한다(FR-145). */
    public abstract AttendanceView answer(
            long memberId, AttendanceAnswer answer, LocalDateTime now);

    /** 참석·불참·미정 인원. 목록(FR-133)과 응답 결과가 같은 수를 쓴다. */
    public abstract AttendanceCounts counts();

    /** 삭제된 일정에는 응답할 수 없다(서비스가 먼저 POST_NOT_FOUND로 거른다. 여기서는 상태 위반, 13.1). */
    protected void requireNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("삭제된 일정");
        }
    }

    public LocalDateTime scheduledAt() {
        return scheduledAt;
    }
}
