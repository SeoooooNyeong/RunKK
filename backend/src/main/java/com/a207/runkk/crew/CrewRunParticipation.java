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

    /**
     * 출발 카운트다운이 시작될 때 준비했던 출발 후보(설계 05-32 [임시:D-성-882]). 준비 취소·퇴장·참석 철회 때만 빠지고 새 입장·재준비로 다시 들어오지
     * 않는다. 출발 뒤에는 카운트다운에서 함께 출발한 사람이다.
     */
    @Column(name = "countdown_candidate", nullable = false)
    private boolean countdownCandidate;

    /** 개인 측정 시작 시각(출발 후보는 출발 시각, 중간 참여자는 참여 승인 시각, [임시:D-성-884]). 옛 진행 참가자는 비어 있다. */
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    /** 서버가 한 번 예약한 이 참여의 측정 기록 ID(UUID, [임시:D-성-884]). 저장 성공의 증거가 아니다. 옛 진행 참가자는 비어 있다. */
    @Column(name = "measurement_record_id", length = 36)
    private String measurementRecordId;

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

    /** 대기실 입장: ENTERING. 출발 후보였으면 빠진다(준비가 풀린다). */
    void enter() {
        readiness = Readiness.ENTERING;
        countdownCandidate = false;
    }

    /** ENTERING ↔ READY. 입장하지 않았으면 IllegalStateException. 준비를 풀면 출발 후보에서 빠진다. */
    public Readiness toggleReady() {
        if (readiness == Readiness.NOT_ENTERED) {
            throw new IllegalStateException("대기실에 들어오지 않은 참가자");
        }
        return changeReady(readiness != Readiness.READY);
    }

    /**
     * 멱등 준비(설계 05-32 [임시:D-성-879]): 입장자의 준비를 ready로 둔다. 같은 값이면 아무것도 바꾸지 않는다. 준비를 풀면 출발 후보에서 빠지고 다시
     * 준비해도 후보로 돌아오지 않는다. 입장하지 않았으면 IllegalStateException.
     */
    Readiness changeReady(boolean ready) {
        if (readiness == Readiness.NOT_ENTERED) {
            throw new IllegalStateException("대기실에 들어오지 않은 참가자");
        }
        readiness = ready ? Readiness.READY : Readiness.ENTERING;
        if (!ready) {
            countdownCandidate = false;
        }
        return readiness;
    }

    /** 대기실 나가기: NOT_ENTERED. 출발 후보에서 빠진다. */
    void leave() {
        readiness = Readiness.NOT_ENTERED;
        countdownCandidate = false;
    }

    /** 카운트다운 시작 때 준비한 입장자를 출발 후보로 고정한다. */
    void markCandidate() {
        countdownCandidate = true;
    }

    /** 출발 후보에서만 뺀다(참석 철회). */
    void dropCandidate() {
        countdownCandidate = false;
    }

    /**
     * 진행 참가자가 된다(출발 후보는 출발 때, 그 밖은 중간 참여 때). 개인 측정 시작 시각과 측정 기록 ID를 한 번만 정하고 이미 진행 참가자면 바꾸지 않는다.
     */
    void markRunner(LocalDateTime startAt, String recordId) {
        if (runner) {
            return;
        }
        runner = true;
        joinedAt = startAt;
        measurementRecordId = recordId;
    }

    /** 시작 시각과 측정 기록 ID 없이 진행 참가자가 된다(05-32 전 출발과 같은 모양, 시험과 옛 자료 재현용). */
    void markRunner() {
        markRunner(null, null);
    }

    /** 옛 진행 참가자(측정 기록 ID 없음)에게 쓰기 경로에서 한 번 채운다. 연결된 기록이 있으면 그 ID를 쓴다. */
    void fillMeasurement(LocalDateTime fallbackStart, String newRecordId) {
        if (joinedAt == null) {
            joinedAt = fallbackStart;
        }
        if (measurementRecordId == null) {
            measurementRecordId = linkedRecordId != null ? linkedRecordId : newRecordId;
        }
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

    /** 출발 후보(카운트다운 시작 때 준비했고 취소·퇴장하지 않은 사람, 출발 뒤에는 함께 출발한 사람). */
    public boolean isCountdownCandidate() {
        return countdownCandidate;
    }

    /** 대기실에 들어와 있다(ENTERING 또는 READY). */
    boolean hasEntered() {
        return readiness != Readiness.NOT_ENTERED;
    }

    /** 개인 측정 시작 시각, 옛 진행 참가자나 진행 참가자가 아니면 비어 있다. */
    public Optional<LocalDateTime> joinedAt() {
        return Optional.ofNullable(joinedAt);
    }

    /** 서버가 예약한 측정 기록 ID, 없으면 비어 있다. */
    public Optional<String> measurementRecordId() {
        return Optional.ofNullable(measurementRecordId);
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
