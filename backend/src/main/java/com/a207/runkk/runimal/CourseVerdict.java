package com.a207.runkk.runimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 기록 하나의 코스 동일성 판정 결과(설계 6.1.8, FR-166). 비동기 판정이라 결과를 남겨 축 비율 계산이 다시 판정하지 않게 한다. */
@Entity
@Table(name = "course_verdict")
public class CourseVerdict {

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "same_course", nullable = false)
    private boolean sameCourse;

    @Column(name = "judged_at", nullable = false)
    private LocalDateTime judgedAt;

    protected CourseVerdict() {}

    CourseVerdict(String recordId, long memberId, boolean sameCourse, LocalDateTime judgedAt) {
        this.recordId = recordId;
        this.memberId = memberId;
        this.sameCourse = sameCourse;
        this.judgedAt = judgedAt;
    }

    public String getRecordId() {
        return recordId;
    }

    public long getMemberId() {
        return memberId;
    }

    public boolean isSameCourse() {
        return sameCourse;
    }
}
