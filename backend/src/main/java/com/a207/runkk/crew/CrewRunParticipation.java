package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 크루런 참가자 한 명의 응답·준비·결과(설계 6.1.7, 상태도 2.4.6, 자료사전 모집참여). 결과는 합산 방식의 끝냄과 끝낼 때 거리·경과다. CrewRunPost가
 * 합성으로 가진다. runner는 READY로 출발한 참가자다(준비 안 한 참가자는 출발 때 빠진다, A-13).
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

    /** 자기 크루런을 끝냈다(그만두기 또는 크루런 종료로 마감, 합산 방식). */
    @Column(name = "ended", nullable = false)
    private boolean ended;

    /** 끝낼 때 남긴 거리(미터). 연결된 기록이 없을 때 결과에 쓴다. */
    @Column(name = "final_distance_m")
    private Long finalDistanceMeters;

    /** 끝낼 때 남긴 경과(밀리초). */
    @Column(name = "final_elapsed_ms")
    private Long finalElapsedMillis;

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

    /** 자기 크루런 끝내기(합산 방식). 거리·경과가 있으면 남긴다. 이미 끝냈으면 아무것도 바꾸지 않고 false(재시도 안전). */
    boolean end(Distance distance, Duration elapsed) {
        if (ended) {
            return false;
        }
        ended = true;
        if (distance != null) {
            finalDistanceMeters = distance.meters();
        }
        if (elapsed != null) {
            finalElapsedMillis = elapsed.toMillis();
        }
        return true;
    }

    /** 한 참여에 러닝 1건(FR-146). 이미 연결이 있으면 false. */
    public boolean link(String recordId) {
        if (linkedRecordId != null) {
            return false;
        }
        linkedRecordId = recordId;
        return true;
    }

    /** 연결한 기록이 recordId면 연결을 비우고 true(기록 삭제, v0.14 D-232). */
    boolean unlink(String recordId) {
        if (recordId == null || !recordId.equals(linkedRecordId)) {
            return false;
        }
        linkedRecordId = null;
        return true;
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

    /** 자기 크루런을 끝냈다(진행 종료 조건). */
    public boolean hasEnded() {
        return ended;
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

    /** 끝낼 때 남긴 거리, 없으면 비어 있다. */
    public Optional<Distance> finalDistance() {
        return Optional.ofNullable(finalDistanceMeters).map(Distance::ofMeters);
    }

    /** 끝낼 때 남긴 경과, 없으면 비어 있다. */
    public Optional<Duration> finalElapsed() {
        return Optional.ofNullable(finalElapsedMillis).map(Duration::ofMillis);
    }

    public Optional<String> linkedRecordId() {
        return Optional.ofNullable(linkedRecordId);
    }
}
