package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.PermissionDeniedException;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 크루런 모집글 하나의 생애주기(설계 6.1.7, 상태도 2.4.5·2.4.6, 6.2 A-13). 장소·위치 항목이 없다(FR-132). 시각은 매개변수로 받는다(D-09).
 * 진행은 합산 방식이다(S15P31A207-236): 각자 달리고 각자 끝내며 순위·도착 판정이 없다. 피그마 맞춤 W6(S15P31A207-297): 제목과 내용(본문)을
 * 나눠 두고, 거리는 3·5·10km·하프 또는 제한 없음(null), 작성자는 VOTING 동안 제목·내용·거리를 고칠 수 있으며, 매주 반복이면
 * seriesId(CrewRunSeries)로 묶인다.
 */
@Entity
@DiscriminatorValue("CREW_RUN")
public class CrewRunPost extends SchedulePost {

    /** 출발 카운트다운(FR-148). */
    static final int COUNTDOWN_SECONDS = 3;

    /** 제목 상한(코드 포인트, W6). */
    static final int MAX_TITLE = 30;

    /** 제한 없음 크루런의 종료 기준 거리(CREW_RUN_END_RULE의 km당 분을 하프 거리로 계산한다, W6). */
    static final StandardDistance UNLIMITED_CLOSE_BASIS = StandardDistance.HALF;

    /** 제목(W6). V57 전 글은 본문 첫 줄을 옮겨 넣었다. 열은 200자(옛 첫 줄), 새 글은 30자. */
    @Column(name = "title", length = 200 * UTF16_UNITS_PER_CHAR)
    private String title;

    /** 매주 반복이면 그 반복(crew_run_series) ID, 아니면 null(W6). */
    @Column(name = "series_id")
    private Long seriesId;

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

    /** 출발 카운트다운 예약 식별값(설계 05-32 [임시:D-성-881]). 한 크루런에 한 번만 정하고 바꾸지 않는다. 예약 전이면 null. */
    @Column(name = "countdown_token", length = 40)
    private String countdownToken;

    /** 카운트다운을 예약한 서버 시각. */
    @Column(name = "countdown_planned_at")
    private LocalDateTime countdownPlannedAt;

    /** 측정 시작 절대 시각 = 예약 시각 + 3초(앱은 다시 3초를 더하지 않는다, [임시:D-성-881]). */
    @Column(name = "countdown_start_at")
    private LocalDateTime countdownStartAt;

    /** 카운트다운을 연 계기(전원 준비, 모집자 지금 시작, 예약 시각). */
    @Enumerated(EnumType.STRING)
    @Column(name = "start_reason", length = 12)
    private StartReason startReason;

    /**
     * 변경 순번(설계 05-32 4.1, [임시:D-성-878]). 대기실, 출발, 참여, 결과, 모집 변경을 알릴 때 글 행 잠금 안에서 올린다. 부모 JPA
     * version과 따로 둔다.
     */
    @Column(name = "change_revision")
    private Long changeRevision = 0L;

    protected CrewRunPost() {}

    /**
     * scheduledAt &gt; now, 제목 공백 제외 1~30자, 거리 3·5·10km·하프 또는 제한 없음(null), 마감 ≤ 희망일시(비우면 희망일시).
     * VOTING이고 작성자는 GOING으로 참여한다(제안). 위반이면 CREW_RUN_INVALID, 내용 200자 초과는 BODY_TOO_LONG. 15일·5분
     * 규칙(CrewRunSlot)은 앱 입력에만 걸고 여기서는 보지 않는다.
     */
    public CrewRunPost(long crewId, long authorId, CrewRunDraft draft, LocalDateTime now) {
        this(crewId, authorId, draft, now, null);
        if (draft.distance() != null && !draft.distance().isCrewRun()) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
    }

    /** 공통 생성: 거리 선택지는 부르는 쪽이 본다(반복 회차는 최신 회차 거리를 그대로 복사한다). */
    private CrewRunPost(
            long crewId, long authorId, CrewRunDraft draft, LocalDateTime now, Long seriesId) {
        super(crewId, authorId, new Slot(draft.body(), draft.scheduledAt()), now);
        LocalDateTime deadline =
                draft.voteDeadline() == null ? draft.scheduledAt() : draft.voteDeadline();
        if (draft.scheduledAt() == null
                || !draft.scheduledAt().isAfter(now)
                || deadline.isAfter(draft.scheduledAt())) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
        this.title = requireTitle(draft.title());
        this.distance = draft.distance();
        this.voteDeadline = deadline;
        this.status = CrewRunStatus.VOTING;
        this.seriesId = seriesId;
        participations.add(new CrewRunParticipation(authorId, AttendanceAnswer.GOING, now));
    }

    /** 공백만이거나 30자(코드 포인트) 넘는 제목은 CREW_RUN_INVALID. 앞뒤 공백은 걷는다. */
    private static String requireTitle(String title) {
        if (title == null || title.isBlank() || codePoints(title.strip()) > MAX_TITLE) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
        return title.strip();
    }

    /**
     * 반복의 다음 회차(W6): 이 글(최신 회차)의 제목·내용·거리를 복사하고 일시만 scheduledAt이다. 작성자는 참석, 상태 VOTING, 마감 = 일시. 탈퇴로
     * 작성자가 없으면 IllegalStateException(반복을 끝낼 일이다).
     */
    public CrewRunPost nextOccurrence(long series, LocalDateTime scheduledAt, LocalDateTime now) {
        if (authorId() == null) {
            throw new IllegalStateException("작성자가 없는 반복");
        }
        return new CrewRunPost(
                crewId(),
                authorId(),
                new CrewRunDraft(title, body(), scheduledAt, distance, null, CrewRunRepeat.WEEKLY),
                now,
                series);
    }

    /**
     * 모집글 수정(F-13d, W6): 삭제 안 됨, 작성자만(아니면 PermissionDeniedException), VOTING이고 now &lt; 일시(아니면
     * ALREADY_STARTED). 제목·내용·거리만 바꾼다. 거리는 선택지이거나 지금 거리와 같아야 한다(기존 1km 유지, 아니면 CREW_RUN_INVALID).
     * 반복은 CrewRunRepeater가 다룬다.
     */
    public void edit(long memberId, CrewRunEdit edit, LocalDateTime now) {
        requireNotDeleted();
        if (!Objects.equals(authorId(), memberId)) {
            throw new PermissionDeniedException("작성자만 모집글을 고칠 수 있다");
        }
        if (!isEditable(now)) {
            throw new BusinessRuleException(ReasonCode.ALREADY_STARTED);
        }
        String newTitle = requireTitle(edit.title());
        if (edit.distance() != null
                && !edit.distance().isCrewRun()
                && edit.distance() != distance) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_INVALID);
        }
        changeBody(edit.body());
        this.title = newTitle;
        this.distance = edit.distance();
    }

    /** 상세 ⋯ 메뉴 "수정하기": 작성자이고 고칠 수 있는 상태(W6). */
    public boolean canEdit(long memberId, LocalDateTime now) {
        return Objects.equals(authorId(), memberId) && isEditable(now);
    }

    private boolean isEditable(LocalDateTime now) {
        return isVisible() && status == CrewRunStatus.VOTING && now.isBefore(scheduledAt());
    }

    /** 반복에 묶는다(작성 때·수정으로 반복을 시작할 때, W6). */
    void attachSeries(long series) {
        this.seriesId = series;
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
                        p -> {
                            p.changeAnswer(answer, now);
                            if (answer != AttendanceAnswer.GOING) {
                                p.dropCandidate(); // 참석 철회는 출발 후보에서만 뺀다(U-33)
                            }
                        },
                        () -> participations.add(new CrewRunParticipation(memberId, answer, now)));
        return AttendanceView.of(getId() == null ? 0 : getId(), answer, counts());
    }

    /**
     * 참석 취소(피그마 F-13b "참석 취소하기", W6): 참석 응답을 거둔다(응답 행을 지워 본인 응답이 없어진다). 삭제 안 됨(아니면
     * IllegalStateException), now &lt; voteDeadline(아니면 VOTE_CLOSED). 응답이 없으면 그대로이고, 작성자도 거둘 수 있다.
     * 출발 뒤 진행 참가자는 마감 뒤라 여기 오지 않는다.
     */
    public AttendanceView withdraw(long memberId, LocalDateTime now) {
        requireNotDeleted();
        if (!now.isBefore(voteDeadline)) {
            throw new BusinessRuleException(ReasonCode.VOTE_CLOSED);
        }
        participations.removeIf(p -> p.belongsTo(memberId) && !p.isRunner());
        return AttendanceView.of(getId() == null ? 0 : getId(), null, counts());
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
        // 옛 앱은 대기실 화면에 들어올 때마다 enter를 다시 부른다. 카운트다운 후보가 그 재호출로 빠지면 출발 신호를 못 받는다.
        if (hasCountdown() && p.isCountdownCandidate()) {
            return;
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

    /**
     * 멱등 준비(설계 05-32 [임시:D-성-879], PUT /lobby/ready): 대기실(LOBBY_OPEN, 카운트다운 중 포함)의 입장자만. 같은 값이면 바뀐
     * 것이 없어 false. 카운트다운 중 준비를 풀면 출발 후보에서 빠지고 다시 준비해도 후보로 돌아오지 않는다. 대기실이 아니면
     * IllegalStateException(409 INVALID_STATE), 입장하지 않았으면 NOT_PARTICIPANT.
     */
    public boolean setReady(long memberId, boolean ready) {
        requireLobby();
        CrewRunParticipation p =
                participationOf(memberId)
                        .filter(CrewRunParticipation::hasEntered)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.NOT_PARTICIPANT));
        Readiness before = p.readiness();
        return p.changeReady(ready) != before;
    }

    /**
     * 전원 준비 출발(FR-성-357, U-25, U-33): 대기실이고 아직 예약이 없을 때 입장자(GOING이고 들어와 있는 사람)가 최소 준비 인원 이상이고 모두
     * 준비했으면 지금 준비자를 출발 후보로 고정하고 now + 3초 출발을 예약한다. 예약했으면 true. 참석만 하고 들어오지 않은 사람은 분모에서 빠진다.
     */
    public boolean planIfAllReady(LocalDateTime now, CrewRunRule rule) {
        if (!isVisible() || status != CrewRunStatus.LOBBY_OPEN || hasCountdown()) {
            return false;
        }
        List<CrewRunParticipation> in = entrants();
        if (in.size() < rule.minReady()
                || !in.stream().allMatch(p -> p.readiness() == Readiness.READY)) {
            return false;
        }
        plan(now, StartReason.ALL_READY);
        return true;
    }

    /**
     * 모집자 「지금 시작」(FR-성-358): 모집자(작성자)만, 아니면 PermissionDeniedException. 이미 예약이 있으면(카운트다운 중, 출발 뒤)
     * 인원을 다시 보지 않고 그 예약을 그대로 두어 false. 최초 예약만 준비자가 최소 준비 인원 이상이어야 하고 아니면 CREW_RUN_NOT_READY. 대기실이
     * 지났으면 ALREADY_STARTED. 새로 예약했으면 true(같은 3초 규칙과 후보 고정).
     */
    public boolean startNow(long memberId, LocalDateTime now, CrewRunRule rule) {
        requireNotDeleted();
        if (!Objects.equals(authorId(), memberId)) {
            throw new PermissionDeniedException("모집자만 지금 시작할 수 있다");
        }
        if (hasCountdown()
                && (status == CrewRunStatus.LOBBY_OPEN || status == CrewRunStatus.RUNNING)) {
            return false;
        }
        if (status == CrewRunStatus.VOTING) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_NOT_READY);
        }
        if (status != CrewRunStatus.LOBBY_OPEN) {
            throw new BusinessRuleException(ReasonCode.ALREADY_STARTED);
        }
        if (readyEntrants().size() < rule.minReady()) {
            throw new BusinessRuleException(ReasonCode.CREW_RUN_NOT_READY);
        }
        plan(now, StartReason.HOST);
        return true;
    }

    /** 「지금 시작」 단추를 보일까: 모집자이고 예약 전 대기실에 준비자가 최소 준비 인원 이상. */
    public boolean canStartNow(long memberId, CrewRunRule rule) {
        return isVisible()
                && Objects.equals(authorId(), memberId)
                && status == CrewRunStatus.LOBBY_OPEN
                && !hasCountdown()
                && readyEntrants().size() >= rule.minReady();
    }

    /** 지금 준비자를 후보로 고정하고 예약한다. 시각은 밀리초까지만 둔다(DB 왕복에도 같은 값). */
    private void plan(LocalDateTime now, StartReason reason) {
        LocalDateTime at = now.truncatedTo(ChronoUnit.MILLIS);
        readyEntrants().forEach(CrewRunParticipation::markCandidate);
        long revision = markChanged();
        countdownToken = getId() + "-" + revision;
        countdownPlannedAt = at;
        countdownStartAt = at.plusSeconds(COUNTDOWN_SECONDS);
        startReason = reason;
    }

    /** 출발 예약이 있다(카운트다운 중이거나 그 예약으로 출발했다). */
    public boolean hasCountdown() {
        return countdownToken != null;
    }

    /** 대기실 입장자: GOING이고 들어와 있는 참여(입장 순). */
    private List<CrewRunParticipation> entrants() {
        return participations.stream()
                .filter(p -> p.isGoing() && p.hasEntered() && p.memberId().isPresent())
                .toList();
    }

    private List<CrewRunParticipation> readyEntrants() {
        return entrants().stream().filter(p -> p.readiness() == Readiness.READY).toList();
    }

    /** 지금 대기실 입장자 회원 ID(출발 통지 대상, [임시:D-성-883]). */
    public List<Long> entrantIds() {
        return entrants().stream().flatMap(p -> p.memberId().stream()).toList();
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
     * 1초 시계의 출발 판정(FR-성-357~359, U-33). LOBBY_OPEN일 때만 본다. 예약이 없고 now ≥ scheduledAt이면 준비자가 최소 준비 인원
     * 이상일 때 후보를 고정하고 now + 3초로 예약하고(PLANNED), 아니면 CANCELLED(최초 예약 전 부족만 취소). 예약이 있고 now ≥ startAt이면
     * 남은 후보로 RUNNING(0명, 1명이어도 같은 시각), departedAt = startAt, 후보마다 측정 시작 시각과 측정 기록 ID를 한 번
     * 정한다(STARTED). 다시 세거나 재예약하지 않는다. 그 밖은 NONE.
     */
    public DepartureOutcome depart(LocalDateTime now, CrewRunRule rule) {
        if (status != CrewRunStatus.LOBBY_OPEN) {
            return DepartureOutcome.NONE;
        }
        if (!hasCountdown()) {
            if (now.isBefore(scheduledAt())) {
                return DepartureOutcome.NONE;
            }
            if (readyEntrants().size() < rule.minReady()) {
                status = CrewRunStatus.CANCELLED;
                return DepartureOutcome.cancelled(participantsToRemind());
            }
            plan(now, StartReason.SCHEDULED);
            return DepartureOutcome.planned(
                    countdownStartAt, countdownParticipantIds(), countdownToken);
        }
        if (now.isBefore(countdownStartAt)) {
            return DepartureOutcome.NONE;
        }
        participations.stream()
                .filter(CrewRunParticipation::isCountdownCandidate)
                .forEach(p -> p.markRunner(countdownStartAt, newRecordId()));
        departedAt = countdownStartAt;
        status = CrewRunStatus.RUNNING;
        List<Long> notified = new ArrayList<>(entrantIds());
        runners().stream().filter(id -> !notified.contains(id)).forEach(notified::add);
        return DepartureOutcome.started(departedAt, runners(), notified, countdownToken);
    }

    /**
     * 중간 참여(FR-성-361, FR-성-362, U-26): 이미 진행 참가자면 그 참여를 그대로 두고 false(같은 측정 시작 시각과 측정 기록 ID, 옛 진행
     * 참가자는 출발 시각과 연결 기록으로 한 번 채운다). 아니면 RUNNING이고 now &lt; 종료 기준 시각일 때만 받아 now부터 재는 진행 참가자로 등록하고
     * true. 응답이 없거나 불참이어도 GOING으로 바꾼다. 출발 전, 종료, 취소, 종료 기준 시각 뒤면 IllegalStateException(409
     * INVALID_STATE). 소속 검사는 서비스가 먼저 한다.
     */
    public boolean join(long memberId, LocalDateTime now, CrewRunRule rule) {
        Optional<CrewRunParticipation> existing = runnerOf(memberId);
        if (existing.isPresent()) {
            existing.get().fillMeasurement(departedAt, newRecordId());
            return false;
        }
        if (!isVisible() || status != CrewRunStatus.RUNNING || !now.isBefore(closesAt(rule))) {
            throw new IllegalStateException("진행 중인 크루런이 아니다: " + status);
        }
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
        p.markRunner(now.truncatedTo(ChronoUnit.MILLIS), newRecordId());
        return true;
    }

    /** 중간 참여 단추를 보일까: RUNNING이고 종료 기준 시각 전이며 아직 진행 참가자가 아니다. */
    public boolean canJoin(long memberId, LocalDateTime now, CrewRunRule rule) {
        return isVisible()
                && status == CrewRunStatus.RUNNING
                && now.isBefore(closesAt(rule))
                && runnerOf(memberId).isEmpty();
    }

    /** 종료 기준 시각(출발 + 거리 × km당 분, 제한 없음은 하프 기준). 출발 전이면 IllegalStateException. */
    private LocalDateTime closesAt(CrewRunRule rule) {
        if (departedAt == null) {
            throw new IllegalStateException("출발하지 않았다");
        }
        return rule.closesAt(departedAt, distance == null ? UNLIMITED_CLOSE_BASIS : distance);
    }

    private static String newRecordId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 일시정지할 수 있는지 확인한다(합산 방식): RUNNING이고 아직 끝내지 않은 진행 참가자. 아니면 NOT_PARTICIPANT. 정지 상태는 세션에만 두고 DB에
     * 쓰지 않는다.
     */
    public void checkPausable(long memberId) {
        if (status != CrewRunStatus.RUNNING
                || runnerOf(memberId).filter(p -> !p.hasEnded()).isEmpty()) {
            throw new BusinessRuleException(ReasonCode.NOT_PARTICIPANT);
        }
    }

    /**
     * 자기 크루런 끝내기(합산 방식, FR-192 대체). RUNNING·FINISHED인 크루런의 진행 참가자만, 아니면 NOT_PARTICIPANT. 거리·경과를 남기고
     * 새로 끝냈으면 true, 이미 끝냈으면 false(재시도 안전).
     */
    public boolean end(long memberId, Distance distance, Duration elapsed) {
        if (status != CrewRunStatus.RUNNING && status != CrewRunStatus.FINISHED) {
            throw new BusinessRuleException(ReasonCode.NOT_PARTICIPANT);
        }
        return runnerOf(memberId)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.NOT_PARTICIPANT))
                .end(distance, elapsed);
    }

    /** 출발 뒤(진행·종료) 참여에 러닝 1건을 잇는다(FR-146). 참여가 없거나 이미 연결이 있으면 false. */
    public boolean linkRun(long memberId, String recordId) {
        if (status != CrewRunStatus.RUNNING && status != CrewRunStatus.FINISHED) {
            return false;
        }
        return participationOf(memberId).map(p -> p.link(recordId)).orElse(false);
    }

    /** RUNNING이고 진행 참가자(1명 이상)가 모두 끝냈거나 now ≥ departedAt + 거리 × km당 분(CREW_RUN_END_RULE). */
    public boolean shouldClose(LocalDateTime now, CrewRunRule rule) {
        if (status != CrewRunStatus.RUNNING) {
            return false;
        }
        // 진행 참가자가 0명이면(후보가 모두 빠진 출발, U-33) 전원 종료로 보지 않고 늦은 참여를 기다린다
        List<CrewRunParticipation> runners = runnerParticipations();
        boolean allEnded =
                !runners.isEmpty() && runners.stream().allMatch(CrewRunParticipation::hasEnded);
        return allEnded || !now.isBefore(closesAt(rule));
    }

    /**
     * 아직 끝내지 않은 진행 참가자를 마지막 중계 값(latest, 없으면 값 없이)으로 마감하고 FINISHED. RUNNING이 아니면
     * IllegalStateException.
     */
    public void close(LocalDateTime now, Map<Long, LiveProgress> latest) {
        if (status != CrewRunStatus.RUNNING) {
            throw new IllegalStateException("진행 중이 아니다: " + status);
        }
        for (CrewRunParticipation p : runnerParticipations()) {
            Optional<LiveProgress> last = p.memberId().map(latest::get);
            p.end(
                    last.map(LiveProgress::distance).orElse(null),
                    last.map(LiveProgress::elapsed).orElse(null));
        }
        endedAt = now;
        status = CrewRunStatus.FINISHED;
    }

    /**
     * 제목, 거리, 희망일시, 참석·불참·미정 인원, 본인 응답, 상태, 함께 달린 인원(진행 참가자 수, 합산 방식이라 완주를 따로 세지 않는다), 연결
     * 기록(FR-133).
     */
    @Override
    public PostSummary summary(long viewerId) {
        int finishers = runnerParticipations().size();
        PostSummary.CrewRunInfo run =
                new PostSummary.CrewRunInfo(
                        title,
                        distance,
                        scheduledAt(),
                        voteDeadline,
                        status,
                        counts(),
                        participationOf(viewerId).map(CrewRunParticipation::answer).orElse(null),
                        finishers,
                        linkedRecordIds(),
                        myRunOf(viewerId));
        return baseSummary(viewerId).withCrewRun(run);
    }

    /** 열람자의 참여 상태(v0.14 S15P31A207-314): 참여 없음·진행 참가자 아님 NONE, 측정 중 RUNNING, 측정 끝 ENDED. */
    public MyRunState myRunOf(long viewerId) {
        return MyRunState.of(participationOf(viewerId));
    }

    /** 진행 참가자(출발한 후보와 중간 참여자), 참여 순. */
    public List<Long> runners() {
        return runnerParticipations().stream().flatMap(p -> p.memberId().stream()).toList();
    }

    private List<CrewRunParticipation> runnerParticipations() {
        return participations.stream().filter(CrewRunParticipation::isRunner).toList();
    }

    private Optional<CrewRunParticipation> runnerOf(long memberId) {
        return participationOf(memberId).filter(CrewRunParticipation::isRunner);
    }

    /** 그 회원 참여의 기록 연결을 비운다(기록 삭제, v0.14 D-232). 비웠으면 true. */
    public boolean unlinkRun(long memberId, String recordId) {
        return participationOf(memberId).map(p -> p.unlink(recordId)).orElse(false);
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

    /** 거리, 제한 없음이면 null. */
    public StandardDistance distance() {
        return distance;
    }

    /** 제목(W6). */
    public String title() {
        return title;
    }

    /** 반복 ID, 반복이 아니면 null(W6). */
    public Long seriesId() {
        return seriesId;
    }

    public LocalDateTime voteDeadline() {
        return voteDeadline;
    }

    public Optional<LocalDateTime> departedAt() {
        return Optional.ofNullable(departedAt);
    }

    /**
     * 화면의 논리 단계(설계 05-32 2.3): 투표 중과 대기실은 LOBBY, 예약이 있는 대기실은 COUNTDOWN, 그 밖은 상태 그대로. 상태 enum은 그대로
     * 둔다(LOBBY_OPEN 유지).
     */
    public CrewRunPhase phase() {
        return switch (status) {
            case VOTING -> CrewRunPhase.LOBBY;
            case LOBBY_OPEN -> hasCountdown() ? CrewRunPhase.COUNTDOWN : CrewRunPhase.LOBBY;
            case RUNNING -> CrewRunPhase.RUNNING;
            case FINISHED -> CrewRunPhase.FINISHED;
            case CANCELLED -> CrewRunPhase.CANCELLED;
        };
    }

    /** 출발 예약 식별값, 예약 전이면 비어 있다. */
    public Optional<String> countdownToken() {
        return Optional.ofNullable(countdownToken);
    }

    /** 측정 시작 절대 시각: 예약이 있으면 예약 시각 + 3초, 옛 출발이면 departedAt, 아니면 비어 있다. */
    public Optional<LocalDateTime> startAt() {
        return Optional.ofNullable(countdownStartAt != null ? countdownStartAt : departedAt);
    }

    /** 예약 계기, 예약 전이면 비어 있다. */
    public Optional<StartReason> startReason() {
        return Optional.ofNullable(startReason);
    }

    /** 출발 후보(카운트다운 중에는 남은 후보, 출발 뒤에는 함께 출발한 사람) 회원 ID, 참여 순. */
    public List<Long> countdownParticipantIds() {
        return participations.stream()
                .filter(CrewRunParticipation::isCountdownCandidate)
                .flatMap(p -> p.memberId().stream())
                .toList();
    }

    /** 변경 순번(실시간 변경 신호와 조회 응답의 revision). */
    public long revision() {
        return changeRevision == null ? 0 : changeRevision;
    }

    /** 변경을 알리기 전에 글 행 잠금 안에서 순번을 하나 올린다(CrewRunAnnouncer.changed). */
    long markChanged() {
        changeRevision = revision() + 1;
        return changeRevision;
    }
}
