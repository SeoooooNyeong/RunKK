package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.policy.CrewRunRule;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 크루런 모집글 하나의 생애주기(설계 6.1.7, 상태도 2.4.5·2.4.6, 6.2 A-13). 장소·위치 항목이 없다(FR-132). 시각은 매개변수로 받는다(D-09).
 */
@Entity
@DiscriminatorValue("CREW_RUN")
public class CrewRunPost extends SchedulePost {

    /** 출발 카운트다운(FR-148). */
    static final int COUNTDOWN_SECONDS = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "distance", length = 10)
    private StandardDistance distance;

    @Column(name = "vote_deadline")
    private LocalDateTime voteDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12)
    private CrewRunStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<CrewRunParticipation> participations = new ArrayList<>();

    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** 10분 전 알림(CREW_RUN_SOON)을 보냈는가(한 번만, FR-193). */
    @Column(name = "reminded")
    private boolean reminded;

    protected CrewRunPost() {}

    /**
     * scheduledAt &gt; now, 거리 1·3·5·10km, 마감 ≤ 희망일시(비우면 희망일시). VOTING이고 작성자는 GOING으로 참여한다(제안).
     * 위반이면 CREW_RUN_INVALID.
     */
    public CrewRunPost(long crewId, long authorId, CrewRunDraft draft, LocalDateTime now) {
        super(crewId, authorId, new Slot(draft.body(), draft.scheduledAt()), now);
        LocalDateTime deadline =
                draft.voteDeadline() == null ? draft.scheduledAt() : draft.voteDeadline();
        if (draft.scheduledAt() == null
                || !draft.scheduledAt().isAfter(now)
                || draft.distance() == null
                || !draft.distance().isCrewRun()
                || deadline.isAfter(draft.scheduledAt())) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
        this.distance = draft.distance();
        this.voteDeadline = deadline;
        this.status = CrewRunStatus.VOTING;
        participations.add(new CrewRunParticipation(authorId, AttendanceAnswer.GOING, now));
    }

    @Override
    protected int maxBody() {
        return SHORT_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.CREW_RUN;
    }

    /**
     * 참석 응답(v0.10 vote 개명, FR-145). 삭제 안 됨(아니면 IllegalStateException), now &lt; voteDeadline(아니면
     * VOTE_CLOSED). 있으면 응답 변경, 없으면 추가. GOING이 크루런 참가자이고 UNDECIDED·NOT_GOING은 참가자가 아니다.
     */
    @Override
    public AttendanceView answer(long memberId, AttendanceAnswer answer, LocalDateTime now) {
        requireNotDeleted();
        if (!now.isBefore(voteDeadline)) {
            throw new BusinessRuleException(ReasonCode.VOTE_CLOSED);
        }
        participationOf(memberId)
                .ifPresentOrElse(
                        p -> p.changeAnswer(answer, now),
                        () -> participations.add(new CrewRunParticipation(memberId, answer, now)));
        return AttendanceView.of(getId() == null ? 0 : getId(), answer, counts());
    }

    /** GOING·NOT_GOING·UNDECIDED 응답 수(FR-133). 탈퇴로 익명화된 참여도 센다. */
    @Override
    public AttendanceCounts counts() {
        return AttendanceCounts.of(
                participations.stream().map(CrewRunParticipation::answer).toList());
    }

    /** VOTING이고 now ≥ scheduledAt − 30분이면 LOBBY_OPEN, true. */
    public boolean openLobby(LocalDateTime now, CrewRunRule rule) {
        if (!isVisible()
                || status != CrewRunStatus.VOTING
                || now.isBefore(scheduledAt().minus(rule.lobbyOpenBefore()))) {
            return false;
        }
        status = CrewRunStatus.LOBBY_OPEN;
        return true;
    }

    /**
     * 대기실 개방 시각 ≤ now &lt; scheduledAt, 취소 아님. 응답이 없거나 NOT_GOING·UNDECIDED이면 GOING으로 바꾸고(D-152)
     * readiness ENTERING. 이르면 LOBBY_NOT_OPEN, 늦거나 시작·취소면 ALREADY_STARTED.
     */
    public void enterLobby(long memberId, LocalDateTime now, CrewRunRule rule) {
        if (!isVisible() || isPastLobby() || !now.isBefore(scheduledAt())) {
            throw new BusinessRuleException(ReasonCode.ALREADY_STARTED);
        }
        if (now.isBefore(scheduledAt().minus(rule.lobbyOpenBefore()))) {
            throw new BusinessRuleException(ReasonCode.LOBBY_NOT_OPEN);
        }
        openLobby(now, rule);
        CrewRunParticipation p =
                participationOf(memberId)
                        .orElseGet(
                                () -> {
                                    CrewRunParticipation added =
                                            new CrewRunParticipation(
                                                    memberId, AttendanceAnswer.GOING, now);
                                    participations.add(added);
                                    return added;
                                });
        if (!p.isGoing()) {
            p.changeAnswer(AttendanceAnswer.GOING, now);
        }
        p.enter();
    }

    private boolean isPastLobby() {
        return status != CrewRunStatus.VOTING && status != CrewRunStatus.LOBBY_OPEN;
    }

    /** ENTERING ↔ READY. 입장하지 않았거나 대기실이 아니면 IllegalStateException. */
    public Readiness toggleReady(long memberId) {
        requireLobby();
        return participationOf(memberId)
                .orElseThrow(() -> new IllegalStateException("대기실에 들어오지 않은 참가자"))
                .toggleReady();
    }

    /** NOT_ENTERED. */
    public void leaveLobby(long memberId) {
        requireLobby();
        participationOf(memberId).ifPresent(CrewRunParticipation::leave);
    }

    private void requireLobby() {
        if (status != CrewRunStatus.LOBBY_OPEN) {
            throw new IllegalStateException("대기실이 열려 있지 않다: " + status);
        }
    }

    /** 10분 전 알림 대상: GOING 응답자. */
    public List<Long> participantsToRemind() {
        return participations.stream()
                .filter(CrewRunParticipation::isGoing)
                .flatMap(p -> p.memberId().stream())
                .toList();
    }

    /** 아직 알리지 않았고 투표 중·대기실이고 now ≥ scheduledAt − 10분이면 알림 표시를 하고 true(FR-193 한 번). */
    public boolean markReminded(LocalDateTime now, CrewRunRule rule) {
        if (reminded
                || !isVisible()
                || isPastLobby()
                || now.isBefore(scheduledAt().minus(rule.reminderBefore()))) {
            return false;
        }
        reminded = true;
        return true;
    }

    /**
     * LOBBY_OPEN이고 now ≥ scheduledAt일 때: READY ≥ 2명이면 RUNNING, departedAt = scheduledAt + 3초,
     * READY가 아닌 참가자는 빠진다. 아니면 CANCELLED. 조건이 아니면 NONE(A-13).
     */
    public DepartureOutcome depart(LocalDateTime now, CrewRunRule rule) {
        if (status != CrewRunStatus.LOBBY_OPEN || now.isBefore(scheduledAt())) {
            return DepartureOutcome.NONE;
        }
        List<CrewRunParticipation> ready =
                participations.stream().filter(p -> p.readiness() == Readiness.READY).toList();
        if (ready.size() < rule.minReady()) {
            status = CrewRunStatus.CANCELLED;
            return DepartureOutcome.cancelled(participantsToRemind());
        }
        ready.forEach(CrewRunParticipation::markRunner);
        departedAt = scheduledAt().plusSeconds(COUNTDOWN_SECONDS);
        status = CrewRunStatus.RUNNING;
        return DepartureOutcome.started(departedAt, runners());
    }

    /** RUNNING인 진행 참가자의 처음 도착만 기록한다. 종료 뒤 늦게 온 기록은 순위를 바꾸지 않는다. */
    public void recordArrival(long memberId, LocalDateTime at) {
        if (status == CrewRunStatus.RUNNING) {
            runnerOf(memberId).ifPresent(p -> p.arrive(at));
        }
    }

    /** RUNNING인 진행 참가자만. 아니면 NOT_PARTICIPANT. 순위에서 뺀다(FR-192). */
    public void quit(long memberId, LocalDateTime now) {
        if (status != CrewRunStatus.RUNNING) {
            throw new BusinessRuleException(ReasonCode.NOT_PARTICIPANT);
        }
        runnerOf(memberId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.NOT_PARTICIPANT))
                .quit();
    }

    /** 출발 뒤(진행·종료) 참여에 러닝 1건을 잇는다(FR-146). 참여가 없거나 이미 연결이 있으면 false. */
    public boolean linkRun(long memberId, String recordId) {
        if (status != CrewRunStatus.RUNNING && status != CrewRunStatus.FINISHED) {
            return false;
        }
        return participationOf(memberId).map(p -> p.link(recordId)).orElse(false);
    }

    /** RUNNING이고 모두 도착·그만둠 또는 now ≥ departedAt + 거리 × 15분. */
    public boolean shouldClose(LocalDateTime now, CrewRunRule rule) {
        if (status != CrewRunStatus.RUNNING) {
            return false;
        }
        boolean allDone = runnerParticipations().stream().allMatch(CrewRunParticipation::isDone);
        return allDone || !now.isBefore(rule.closesAt(departedAt, distance));
    }

    /** 도착 순 rank 1, 2, 3…, 미도착·그만둠은 rank 없음(미완주). FINISHED. RUNNING이 아니면 IllegalStateException. */
    public void close(LocalDateTime now) {
        if (status != CrewRunStatus.RUNNING) {
            throw new IllegalStateException("진행 중이 아니다: " + status);
        }
        List<CrewRunParticipation> arrived =
                runnerParticipations().stream()
                        .filter(p -> p.arrivedAt().isPresent() && !p.hasQuit())
                        .sorted(Comparator.comparing(p -> p.arrivedAt().orElseThrow()))
                        .toList();
        runnerParticipations().forEach(p -> p.rankAs(null));
        for (int i = 0; i < arrived.size(); i++) {
            arrived.get(i).rankAs(i + 1);
        }
        endedAt = now;
        status = CrewRunStatus.FINISHED;
    }

    /** 거리, 희망일시, 참석·불참·미정 인원, 본인 응답, 상태, 완주 인원, 연결 기록(FR-133). */
    @Override
    public PostSummary summary(long viewerId) {
        int finishers = (int) participations.stream().filter(p -> p.rank() != null).count();
        PostSummary.CrewRunInfo run =
                new PostSummary.CrewRunInfo(
                        distance,
                        scheduledAt(),
                        voteDeadline,
                        status,
                        counts(),
                        participationOf(viewerId).map(CrewRunParticipation::answer).orElse(null),
                        finishers,
                        linkedRecordIds());
        return baseSummary(viewerId).withCrewRun(run);
    }

    /** READY로 출발한 회원. */
    public List<Long> runners() {
        return runnerParticipations().stream().flatMap(p -> p.memberId().stream()).toList();
    }

    private List<CrewRunParticipation> runnerParticipations() {
        return participations.stream().filter(CrewRunParticipation::isRunner).toList();
    }

    private Optional<CrewRunParticipation> runnerOf(long memberId) {
        return participationOf(memberId).filter(CrewRunParticipation::isRunner);
    }

    public Optional<CrewRunParticipation> participationOf(long memberId) {
        return participations.stream().filter(p -> p.belongsTo(memberId)).findFirst();
    }

    public Optional<Readiness> readinessOf(long memberId) {
        return participationOf(memberId).map(CrewRunParticipation::readiness);
    }

    /** 연결된 기록 ID(크루 반영 거리, Runimal 동행 축). */
    public Set<String> linkedRecordIds() {
        return participations.stream()
                .flatMap(p -> p.linkedRecordId().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<CrewRunParticipation> participations() {
        return Collections.unmodifiableList(participations);
    }

    /** 회원 탈퇴: 참가자 익명화(FR-021). */
    void anonymizeParticipant(long memberId) {
        participationOf(memberId).ifPresent(CrewRunParticipation::anonymize);
    }

    public CrewRunStatus status() {
        return status;
    }

    public StandardDistance distance() {
        return distance;
    }

    public Optional<LocalDateTime> departedAt() {
        return Optional.ofNullable(departedAt);
    }
}
