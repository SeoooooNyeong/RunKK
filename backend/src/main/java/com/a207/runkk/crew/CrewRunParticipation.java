package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 크루런 참가자 한 명의 응답·준비·결과(설계 6.1.7, 상태도 2.4.6, 자료사전 모집참여). CrewRunPost가 합성으로 가진다. runner는 READY로 출발한
 * 참가자다(준비 안 한 참가자는 출발 때 빠진다, A-13).
 */
@Entity
@Table(name = "crew_run_participation")
public class CrewRunParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer", nullable = false, length = 10)
    private AttendanceAnswer answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness", nullable = false, length = 12)
    private Readiness readiness = Readiness.NOT_ENTERED;

    @Column(name = "runner", nullable = false)
    private boolean runner;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "finish_rank")
    private Integer rank;

    @Column(name = "quit", nullable = false)
    private boolean quit;

    @Column(name = "linked_record_id", length = 36)
    private String linkedRecordId;

    protected CrewRunParticipation() {}

    CrewRunParticipation(long memberId, AttendanceAnswer answer, LocalDateTime now) {
        this.memberId = memberId;
        this.answer = answer;
        this.votedAt = now;
    }

    public void changeAnswer(AttendanceAnswer newAnswer, LocalDateTime now) {
        this.answer = newAnswer;
        this.votedAt = now;
    }

    /** 대기실 입장: ENTERING. */
    void enter() {
        readiness = Readiness.ENTERING;
    }

    /** ENTERING ↔ READY. 입장하지 않았으면 IllegalStateException. */
    public Readiness toggleReady() {
        if (readiness == Readiness.NOT_ENTERED) {
            throw new IllegalStateException("대기실에 들어오지 않은 참가자");
        }
        readiness = readiness == Readiness.READY ? Readiness.ENTERING : Readiness.READY;
        return readiness;
    }

    /** 대기실 나가기: NOT_ENTERED. */
    void leave() {
        readiness = Readiness.NOT_ENTERED;
    }

    /** 출발 때 READY였으면 진행 참가자가 된다. */
    void markRunner() {
        runner = true;
    }

    /** 처음 도착만 기록한다. */
    public void arrive(LocalDateTime at) {
        if (arrivedAt == null && !quit) {
            arrivedAt = at;
        }
    }

    /** 중간 그만두기. 이미 도착했으면 그대로다. */
    public void quit() {
        if (arrivedAt == null) {
            quit = true;
        }
    }

    /** 한 참여에 러닝 1건(FR-146). 이미 연결이 있으면 false. */
    public boolean link(String recordId) {
        if (linkedRecordId != null) {
            return false;
        }
        linkedRecordId = recordId;
        return true;
    }

    void rankAs(Integer value) {
        rank = value;
    }

    /** 회원 탈퇴: 참가자 익명화(FR-021). */
    void anonymize() {
        memberId = null;
    }

    public boolean isRunner() {
        return runner;
    }

    boolean isGoing() {
        return answer == AttendanceAnswer.GOING;
    }

    /** 도착했거나 그만뒀다(진행 종료 조건). */
    boolean isDone() {
        return arrivedAt != null || quit;
    }

    public Optional<Long> memberId() {
        return Optional.ofNullable(memberId);
    }

    boolean belongsTo(long member) {
        return memberId != null && memberId == member;
    }

    public AttendanceAnswer answer() {
        return answer;
    }

    public Readiness readiness() {
        return readiness;
    }

    public Optional<LocalDateTime> arrivedAt() {
        return Optional.ofNullable(arrivedAt);
    }

    public Integer rank() {
        return rank;
    }

    public boolean hasQuit() {
        return quit;
    }

    public Optional<String> linkedRecordId() {
        return Optional.ofNullable(linkedRecordId);
    }
}
