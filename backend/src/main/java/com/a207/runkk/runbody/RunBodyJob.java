package com.a207.runkk.runbody;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 기록 하나의 런바디 분석 작업 상태(설계 05-19 2.2·3.3, D-441·D-443). 분석 결과 본문은 갖지 않고(D-442) 러닝 선택 목록(G-30)에 보일 러닝
 * 점수 하나만 둔다(RunBodyScore, V42). 상태 조회 간격은 5초, 10초, 20초로 늘리고, 실패하면 재제출해 3회째 실패에서 FAILED로 둔다. 연결 실패는
 * 횟수에 세지 않는다(D-450).
 */
@Entity
@Table(name = "runbody_job")
public class RunBodyJob {

    /** 재제출 포함 실패 상한(FR-265 제안 3회). */
    static final int MAX_ATTEMPTS = 3;

    /** 커밋 뒤 비동기 제출이 유실되었을 때 pollDue가 다시 제출하기까지의 여유. */
    static final Duration SUBMIT_GRACE = Duration.ofMinutes(1);

    /** 첫 상태 조회 간격(D-443). */
    static final Duration FIRST_POLL = Duration.ofSeconds(5);

    /** 상태 조회 간격 상한(D-443 5초 → 10초 → 20초). */
    static final Duration MAX_POLL = Duration.ofSeconds(20);

    /** 실패 뒤 재제출까지 기다리는 시간. */
    static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    /** 제출 뒤 이만큼 지나도 분석 중이면 실패로 센다(NFR-053 60초의 5배). */
    static final Duration ANALYZE_LIMIT = Duration.ofMinutes(5);

    /** 연결 실패(횟수에 세지 않음) 뒤 다시 시도하기까지의 시간. */
    static final Duration UNREACHABLE_DELAY = Duration.ofMinutes(1);

    /** 연결 실패만 이어져도 작업을 만든 뒤 이만큼 지나면 FAILED로 닫는다. */
    static final Duration GIVE_UP_AFTER = Duration.ofDays(1);

    /** 출생연도가 없어 요청을 만들지 못한 거부 사유(RunBodyRequestFactory). 출생연도를 넣으면 다시 낸다(MR !91 e2e 1). */
    public static final String BIRTH_YEAR_MISSING = "BIRTH_YEAR_MISSING";

    /** 거부 사유 열 길이(V44). 넘는 사유는 자른다. */
    static final int MAX_REASON_LENGTH = 40;

    @Id
    @Column(name = "record_id", length = 36)
    private String recordId;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RunBodyStatus status;

    @Column(nullable = false)
    private int attempts;

    /** 이번 제출 뒤 "분석 중" 응답을 받은 횟수. 조회 간격을 늘리는 데 쓴다. */
    @Column(nullable = false)
    private int polls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "next_poll_at", nullable = false)
    private LocalDateTime nextPollAt;

    /** 바로 실패로 닫은 거부 사유(BIRTH_YEAR_MISSING, REJECTED_413 등). 그 밖에는 null(V44). */
    @Column(name = "failure_reason", length = MAX_REASON_LENGTH)
    private String failureReason;

    /** 러닝 점수(0~100). DONE이고 ai 결과를 읽었을 때만 있다. */
    @Column(name = "overall_score")
    private Integer overallScore;

    @Version private Long version;

    protected RunBodyJob() {}

    /** 러닝 저장과 같은 트랜잭션에서 만든다. 커밋 뒤 제출이 유실되면 SUBMIT_GRACE 뒤 pollDue가 제출한다. */
    public static RunBodyJob pending(String recordId, long memberId, LocalDateTime now) {
        if (recordId == null || recordId.isBlank() || recordId.length() > 36) {
            throw new IllegalArgumentException("기록 ID 오류");
        }
        if (memberId <= 0 || now == null) {
            throw new IllegalArgumentException("런바디 작업 입력 오류");
        }
        RunBodyJob job = new RunBodyJob();
        job.recordId = recordId;
        job.memberId = memberId;
        job.status = RunBodyStatus.PENDING;
        job.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        job.nextPollAt = job.createdAt.plus(SUBMIT_GRACE);
        return job;
    }

    /** ai가 202로 받았다. 5초 뒤 첫 상태 조회. */
    public void submitted(LocalDateTime now) {
        requireStatus(RunBodyStatus.PENDING);
        status = RunBodyStatus.ANALYZING;
        polls = 0;
        submittedAt = now;
        nextPollAt = now.plus(FIRST_POLL);
    }

    /** 아직 분석 중. 조회 간격을 두 배로 늘리고(상한 20초), 제출 뒤 ANALYZE_LIMIT가 지났으면 실패로 센다. */
    public void stillAnalyzing(LocalDateTime now) {
        requireStatus(RunBodyStatus.ANALYZING);
        if (submittedAt != null && !now.isBefore(submittedAt.plus(ANALYZE_LIMIT))) {
            failed(now);
            return;
        }
        polls++;
        Duration interval = FIRST_POLL.multipliedBy(1L << Math.min(polls, 4));
        nextPollAt = now.plus(interval.compareTo(MAX_POLL) > 0 ? MAX_POLL : interval);
    }

    /** 분석 완료. 제출 응답이 이미 완료인 경우(같은 세션 재제출)도 받는다. */
    public void done() {
        if (status == RunBodyStatus.FAILED) {
            throw new IllegalStateException("실패로 닫힌 작업");
        }
        status = RunBodyStatus.DONE;
    }

    /** 제출·분석 실패(ai 503·시간 초과·분석 실패). 3회째면 FAILED, 아니면 RETRY_DELAY 뒤 재제출한다. */
    public void failed(LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        attempts++;
        if (attempts >= MAX_ATTEMPTS) {
            status = RunBodyStatus.FAILED;
            return;
        }
        status = RunBodyStatus.PENDING;
        polls = 0;
        nextPollAt = now.plus(RETRY_DELAY);
    }

    /**
     * 러닝 점수를 남긴다(G-30). DONE일 때만 받는다. 점수가 없으면(점수 있는 축 없음) 아무것도 하지 않는다. 같은 ai 결과에서 셈하므로 이미 있으면 덮어써도
     * 같다.
     */
    public void scored(Integer score) {
        requireStatus(RunBodyStatus.DONE);
        if (score == null) {
            return;
        }
        if (score < RunBodyScore.MIN || score > RunBodyScore.MAX) {
            throw new IllegalArgumentException("러닝 점수는 0~100: " + score);
        }
        overallScore = score;
    }

    /** ai가 받지 않는 요청(413 TOO_LARGE·422·409, 요청을 만들 수 없음). 재제출하지 않는다(D-450). */
    public void rejected() {
        rejected(null);
    }

    /** {@link #rejected()}와 같고 사유를 남긴다({@value #MAX_REASON_LENGTH}자까지, MR !91 e2e 1). */
    public void rejected(String reason) {
        if (status == RunBodyStatus.DONE) {
            return;
        }
        status = RunBodyStatus.FAILED;
        failureReason =
                reason == null || reason.length() <= MAX_REASON_LENGTH
                        ? reason
                        : reason.substring(0, MAX_REASON_LENGTH);
    }

    /**
     * 출생연도가 없어 실패로 닫은 작업을 회원이 출생연도를 넣은 뒤 다시 낸다: PENDING, 횟수·사유 초기화, 바로 다음 pollDue가 제출한다(MR !91 e2e
     * 1). 그런 작업이 아니면 거짓.
     */
    public boolean retryAfterBirthYear(LocalDateTime now) {
        if (status != RunBodyStatus.FAILED || !BIRTH_YEAR_MISSING.equals(failureReason)) {
            return false;
        }
        status = RunBodyStatus.PENDING;
        attempts = 0;
        polls = 0;
        failureReason = null;
        nextPollAt = now;
        return true;
    }

    /** ai에 닿지 못했다(연결 실패). 횟수에 세지 않고 미룬다. 작업을 만든 지 하루가 지나면 FAILED로 닫는다. */
    public void unreachable(LocalDateTime now) {
        if (isClosed()) {
            return;
        }
        if (!now.isBefore(createdAt.plus(GIVE_UP_AFTER))) {
            status = RunBodyStatus.FAILED;
            return;
        }
        nextPollAt = now.plus(UNREACHABLE_DELAY);
    }

    /** pollDue가 지금 다룰 작업인가: 제출 대기·분석 중이고 다음 조회 시각이 지났다. */
    public boolean duePoll(LocalDateTime now) {
        return !isClosed() && !nextPollAt.isAfter(now);
    }

    public boolean ownedBy(long memberId) {
        return this.memberId == memberId;
    }

    private boolean isClosed() {
        return status == RunBodyStatus.DONE || status == RunBodyStatus.FAILED;
    }

    private void requireStatus(RunBodyStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("런바디 작업 상태 오류: " + status);
        }
    }

    public String recordId() {
        return recordId;
    }

    public long memberId() {
        return memberId;
    }

    public RunBodyStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public LocalDateTime nextPollAt() {
        return nextPollAt;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    /** 거부 사유. 없으면 null. */
    public String failureReason() {
        return failureReason;
    }

    /** 러닝 점수. 아직 없으면 null. */
    public Integer score() {
        return overallScore;
    }
}
