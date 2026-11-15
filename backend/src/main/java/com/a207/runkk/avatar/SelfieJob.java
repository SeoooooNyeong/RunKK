package com.a207.runkk.avatar;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 사진 원본 대신 회원별 요청 해시와 처리 이력만 보관한다. */
@Entity
@Table(name = "selfie_job")
public class SelfieJob {
    @Id
    @Column(length = 64)
    private String requestId;

    @Column(nullable = false)
    private long memberId;

    @Column(length = 64)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FaceJob.Status status;

    @Column(length = 64)
    private String headId;

    @Column(length = 100)
    private String errorCode;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    @Column(nullable = false)
    private boolean finished;

    @Version private Long version;

    protected SelfieJob() {}

    SelfieJob(String requestId, long memberId, LocalDateTime now) {
        this.requestId = requestId;
        this.memberId = memberId;
        retryAt(now);
    }

    boolean submissionInProgress(LocalDateTime now) {
        return jobId == null && !finished && attemptedAt.plus(Duration.ofSeconds(30)).isAfter(now);
    }

    void retryAt(LocalDateTime now) {
        attemptedAt = now.truncatedTo(ChronoUnit.MICROS);
        finished = false;
    }

    void accepted(FaceJob job) {
        jobId = job.jobId();
        status = job.status();
        headId = job.headId();
        errorCode = job.error() == null ? null : job.error().code();
        errorMessage = job.error() == null ? null : job.error().message();
    }

    void finish() {
        finished = true;
    }

    /** 결과가 불명확하게 실패로 끝낸 작업을 ai가 뒤에 성공으로 회수했다. 다시 진행 중으로 두고 결과를 받아 끝낸다(DV-9). */
    void reopen() {
        finished = false;
    }

    void missing() {
        jobId = null;
        status = null;
        headId = null;
        errorCode = null;
        errorMessage = null;
        finished = true;
    }

    FaceJob view() {
        return new FaceJob(
                jobId,
                status,
                headId,
                errorCode == null ? null : new FaceJob.Error(errorCode, errorMessage));
    }

    public String requestId() {
        return requestId;
    }

    public long memberId() {
        return memberId;
    }

    public String jobId() {
        return jobId;
    }

    public boolean finished() {
        return finished;
    }

    LocalDateTime attemptedAt() {
        return attemptedAt;
    }

    String errorCode() {
        return errorCode;
    }
}
