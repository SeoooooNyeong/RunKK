package com.a207.runkk.battle;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.Gender;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대결 한 건의 생애주기(설계 6.1.6, 상태도 2.4.2, 판정 A-10). 참가자는 합성(BattleParticipant), 기록 상대는 값(OpponentRef)과 상대
 * 표(OpponentTrack, 앱이 보간, D-05)로 남긴다. 동시 변경은 version 낙관적 잠금으로 막는다(5.3).
 */
@Entity
@Table(name = "battle")
public class Battle {

    /** 첫 도착 뒤 종료 카운트다운(FR-107). */
    static final Duration COUNTDOWN = Duration.ofSeconds(10);

    /**
     * 출발하지 못한 대결을 버려진 것으로 보는 시간(활성 대결 1개 불변식 결정). 기록 대결은 생성 뒤, 출발 예약 대결은 출발 시각 뒤 이만큼 지나도 시작하지 않으면
     * CANCELLED다. 앱은 생성 응답 직후 3초 카운트다운 끝에 시작을 보고하고(R-02) 출발 예약 대결은 1초 시계가 매초 다시 시작하므로, 10분은 재시도와 앱
     * 재실행을 넉넉히 덮으면서 가장 짧은 기록 대결 최대 시간(3km × 15분 = 45분)보다 짧다. SRS·설계에 값이 없어 /sw-srs에 추가 요청.
     */
    static final Duration ABANDON_AFTER = Duration.ofMinutes(10);

    /** 결과 보류 만료(FR-108). */
    static final Duration PENDING_LIMIT = Duration.ofHours(24);

    /** 무승부로 보는 도착 시각 차(srs.md 6.3 4.5 규칙 1). */
    static final Duration DRAW_WINDOW = Duration.ofSeconds(1);

    /** 최대 대결 시간 = 거리 × 15분/km(A-11) = 미터당 900밀리초. */
    private static final long MAX_MILLIS_PER_METER = 900;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 10)
    private BattleType type;

    @Column(name = "distance_meters", nullable = false, updatable = false)
    private long distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private BattleStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "countdown_ends_at")
    private LocalDateTime countdownEndsAt;

    @Column(name = "max_ends_at")
    private LocalDateTime maxEndsAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "battle_id", nullable = false)
    @OrderColumn(name = "seq")
    private List<BattleParticipant> participants = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_kind", length = 12, updatable = false)
    private OpponentKind opponentKind;

    @Column(name = "opponent_record_id", length = 36, updatable = false)
    private String opponentRecordId;

    @Column(name = "opponent_name", length = 40, updatable = false)
    private String opponentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_tier", length = 10, updatable = false)
    private Tier opponentTier;

    @Column(name = "opponent_arrival_millis", updatable = false)
    private Long opponentArrivalMillis;

    @Column(name = "opponent_track", columnDefinition = "text", updatable = false)
    private String opponentTrack;

    @Column(name = "opponent_pace_seconds", updatable = false)
    private Integer opponentPaceSeconds;

    @Column(name = "opponent_fastest_km_seconds", updatable = false)
    private Integer opponentFastestKmSeconds;

    /** 유명인 캐릭터 그림 키(2026-10-31 결정). 유명인 기록이 지워져도 결과 화면이 같은 그림이도록 남긴다. */
    @Column(name = "opponent_figure", length = 20, updatable = false)
    private String opponentFigure;

    /** 기록 상대 캐릭터 성별(2026-10-31 결정). 고스트만 매칭 때 정하고 셀럽·나와의 싸움은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_gender", length = 6)
    private Gender opponentGender;

    @Column(name = "opponent_arrives_at")
    private LocalDateTime opponentArrivesAt;

    @Column(name = "opponent_arrived_at")
    private LocalDateTime opponentArrivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "opponent_verdict", length = 10)
    private Verdict opponentVerdict;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    @Column(name = "corrected", nullable = false)
    private boolean corrected;

    @Column(name = "winner_member_id")
    private Long winnerMemberId;

    @Column(name = "winner_opponent", nullable = false)
    private boolean winnerOpponent;

    /** 배치고사 판 번호(만든 때, 1~3). 배치고사 대결이 아니면 null(D-477). GHOST만 가질 수 있다. */
    @Column(name = "placement_round", updatable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer placementRound;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Battle() {}

    private Battle(BattleType type, Distance distance, BattleStatus status, LocalDateTime now) {
        this.type = type;
        this.distanceMeters = distance.meters();
        this.status = status;
        this.createdAt = now;
    }

    /** 랜덤 대결. 두 표 모두 ASSIGNED여야 한다. WAITING_START. */
    public static Battle random(MatchTicket a, MatchTicket b, LocalDateTime now) {
        if (a.getStatus() != MatchStatus.ASSIGNED || b.getStatus() != MatchStatus.ASSIGNED) {
            throw new IllegalStateException("배정된 두 표로만 대결을 만든다");
        }
        if (a.getMemberId() == b.getMemberId() || a.getDistance() != b.getDistance()) {
            throw new IllegalArgumentException("다른 회원, 같은 거리여야 한다");
        }
        Battle battle =
                new Battle(
                        BattleType.RANDOM,
                        a.getDistance().distance(),
                        BattleStatus.WAITING_START,
                        now);
        battle.participants.add(new BattleParticipant(a.getMemberId()));
        battle.participants.add(new BattleParticipant(b.getMemberId()));
        battle.moveTo(battle.status);
        return battle;
    }

    /**
     * 친구 대결(FR-229, D-222). 초대가 ACCEPTED이고 참가한 회원이 있어야 한다. FRIEND, WAITING_START, 참가자는 초대한 회원·참가한
     * 회원, 거리는 초대 거리. 아니면 IllegalStateException.
     */
    public static Battle friend(FriendInvite invite, LocalDateTime now) {
        if (invite.getStatus() != FriendInviteStatus.ACCEPTED || invite.getJoinerId().isEmpty()) {
            throw new IllegalStateException("참가된 초대로만 친구 대결을 만든다");
        }
        Battle battle =
                new Battle(
                        BattleType.FRIEND,
                        invite.getDistance().distance(),
                        BattleStatus.WAITING_START,
                        now);
        battle.participants.add(new BattleParticipant(invite.getInviterId()));
        battle.participants.add(new BattleParticipant(invite.getJoinerId().orElseThrow()));
        battle.moveTo(battle.status);
        return battle;
    }

    /** 기록 대결. GHOST는 WAITING_START, SELF·CELEBRITY는 IN_PROGRESS(앱 시작 보고 전이라 startAt 없음). */
    public static Battle againstRecord(RecordBattleSpec spec, LocalDateTime now) {
        BattleStatus initial =
                spec.type().startsWithCountdown()
                        ? BattleStatus.IN_PROGRESS
                        : BattleStatus.WAITING_START;
        Battle battle = new Battle(spec.type(), spec.distance(), initial, now);
        battle.participants.add(new BattleParticipant(spec.memberId()));
        battle.keepOpponent(spec.opponent(), spec.track());
        battle.keepOpponentValues(spec.opponentValues());
        battle.opponentFigure = spec.opponentFigure();
        if (initial == BattleStatus.IN_PROGRESS) {
            battle.maxEndsAt = now.plus(battle.maxDuration());
        }
        battle.moveTo(initial);
        return battle;
    }

    private void keepOpponent(OpponentRef ref, OpponentTrack track) {
        opponentKind = ref.kind();
        opponentRecordId = ref.recordId();
        opponentName = ref.displayName().value();
        opponentTier = ref.displayTier();
        opponentArrivalMillis = ref.arrivalElapsed().toMillis();
        opponentTrack = track.encode();
    }

    private void keepOpponentValues(ComparisonValues values) {
        opponentPaceSeconds = values.averagePaceValue().map(Pace::secondsPerKm).orElse(null);
        opponentFastestKmSeconds = values.fastestKmValue().map(Pace::secondsPerKm).orElse(null);
    }

    /**
     * 기록 상대 캐릭터 성별을 정한다(GhostMatcher, 2026-10-31 결정). 한 번만 정하고 남·여만 받는다. 기록 상대가 없거나 이미 정했으면
     * IllegalStateException.
     */
    public void showOpponentAs(Gender gender) {
        requireShown(gender);
        if (opponentKind == null || opponentGender != null) {
            throw new IllegalStateException("기록 상대 성별을 정할 수 없다: " + opponentKind);
        }
        opponentGender = gender;
    }

    /** 다른 참가자에게 보이는 그 참가자의 캐릭터 성별을 정한다(랜덤·친구 대결 성사, BattleStartService.announceMatch). */
    public void showMemberAs(long memberId, Gender gender) {
        requireShown(gender);
        requireParticipant(memberId).showAs(gender);
    }

    private static void requireShown(Gender gender) {
        if (gender != Gender.MALE && gender != Gender.FEMALE) {
            throw new IllegalArgumentException("상대 성별은 남·여만: " + gender);
        }
    }

    /** 회원이 보는 상대 캐릭터 성별: 기록 상대면 대결에 남긴 값, 랜덤·친구 대결이면 다른 참가자의 값. 정하지 않았으면 비어 있다. */
    public Optional<Gender> opponentGenderFor(long memberId) {
        if (opponentKind != null) {
            return Optional.ofNullable(opponentGender);
        }
        return rivalOf(memberId).flatMap(BattleParticipant::getShownGender);
    }

    /** 출발 시각(성사 + 3초 + 10초, D-17). WAITING_START만. */
    public void scheduleStart(LocalDateTime at) {
        requireStatus(BattleStatus.WAITING_START);
        this.startAt = at;
    }

    /**
     * 출발. WAITING_START이고 now ≥ startAt이면 그 시각에, 기록 대결(SELF·CELEBRITY)의 시작 보고면 now에 시작한다. 상대 도착 시각
     * = startAt + 상대 도착 경과 시간.
     */
    public void start(LocalDateTime now) {
        if (status == BattleStatus.WAITING_START && startAt != null && !now.isBefore(startAt)) {
            moveTo(BattleStatus.IN_PROGRESS);
        } else if (isAwaitingStartReport()) {
            startAt = now;
        } else {
            throw new IllegalStateException("시작할 수 없는 대결: " + status);
        }
        maxEndsAt = startAt.plus(maxDuration());
        if (opponentArrivalMillis != null) {
            opponentArrivesAt = startAt.plus(Duration.ofMillis(opponentArrivalMillis));
        }
    }

    /** 나와의 싸움·유명인 대결이 앱의 시작 보고를 아직 받지 않았다(IN_PROGRESS, startAt 없음). */
    private boolean isAwaitingStartReport() {
        return status == BattleStatus.IN_PROGRESS && startAt == null && type.startsWithCountdown();
    }

    /**
     * 출발하지 못한 채 ABANDON_AFTER가 지났는가: 시작 보고 없는 기록 대결은 생성 시각, 출발 예약 대결(WAITING_START)은 출발 시각(없으면 생성
     * 시각)부터 센다. BattleRepository.findAbandoned의 조건이다.
     */
    public boolean isAbandoned(LocalDateTime now) {
        LocalDateTime since;
        if (isAwaitingStartReport()) {
            since = createdAt;
        } else if (status == BattleStatus.WAITING_START) {
            since = startAt == null ? createdAt : startAt;
        } else {
            return false;
        }
        return !now.isBefore(since.plus(ABANDON_AFTER));
    }

    /**
     * 출발하지 못한 대결 취소: WAITING_START이거나 시작 보고 전 기록 대결만. CANCELLED(활성 아님, 판정·이력·전적 없음). 버려진 대결
     * 정리(BattleStartService.cancelAbandoned), 새 기록 대결이 시작 보고 전의 이전 기록 대결을 대신할
     * 때(RecordBattleService), 출발 전 포기(cancelIfUnstarted) 때 부른다.
     */
    public void cancelUnstarted(LocalDateTime now) {
        if (status != BattleStatus.WAITING_START && !isAwaitingStartReport()) {
            throw new IllegalStateException("출발한 대결은 취소할 수 없다: " + status);
        }
        closedAt = now;
        moveTo(BattleStatus.CANCELLED);
    }

    /**
     * 시작 보고 전의 기록 대결이면 취소(CANCELLED)하고 true, 그 밖의 활성 대결은 그대로 두고 false. 새 기록 대결이 이전 대결을 대신할
     * 때(RecordBattleService) 상태 검사와 취소를 한 번에 한다(코드 리뷰 3차 B-L4, Tell, Don't Ask).
     */
    public boolean cancelIfAwaitingStartReport(LocalDateTime now) {
        if (!isAwaitingStartReport()) {
            return false;
        }
        cancelUnstarted(now);
        return true;
    }

    /**
     * 출발하지 못한 대결(WAITING_START, 시작 보고 전 기록 대결)이면 취소(CANCELLED)하고 true, 출발한 대결은 그대로 두고 false. 출발 전에
     * 그만두면 승패 없이 취소한다(배틀런 QA 7번 결정, BattleResultService.forfeit).
     */
    public boolean cancelIfUnstarted(LocalDateTime now) {
        if (status != BattleStatus.WAITING_START && !isAwaitingStartReport()) {
            return false;
        }
        cancelUnstarted(now);
        return true;
    }

    /**
     * 러너의 대결 취소(BattleResultService.cancel, 2026-10-30 결정). 셀프런·셀럽런(type.allowsCancel)의 활성 대결을 출발
     * 여부와 상관없이 CANCELLED(closedAt = now, 활성 칸 비움)로 바꾼다. 판정·이력·전적·리워드가 없다. 취소할 수 없는 종류이거나 활성이 아니면
     * IllegalStateException이다(종류 거부는 서비스가 사유 코드로 먼저 막는다).
     */
    public void cancelByRunner(LocalDateTime now) {
        if (!type.allowsCancel()) {
            throw new IllegalStateException("취소할 수 없는 대결 종류: " + type);
        }
        if (!status.isActive()) {
            throw new IllegalStateException("활성 대결만 취소할 수 있다: " + status);
        }
        closedAt = now;
        moveTo(BattleStatus.CANCELLED);
    }

    /** 그 참가자의 기록 ID를 비운다(기록 삭제, v0.14 D-232). 참가자가 아니면 아무것도 하지 않는다. */
    public void forgetRecord(long memberId) {
        participant(memberId).ifPresent(BattleParticipant::forgetRecord);
    }

    public boolean isCancelled() {
        return status == BattleStatus.CANCELLED;
    }

    /**
     * 상태를 바꾸고 참가자의 활성 대결 칸(battle_participant.active_member_id 고유 인덱스)을 맞춘다. 활성이면 회원 ID, 아니면 비운다.
     * 그래서 한 회원이 활성 대결을 둘 가질 수 없다는 불변식을 PostgreSQL·H2 고유 제약이 지킨다(동시 생성 경합 포함).
     */
    private void moveTo(BattleStatus next) {
        status = next;
        participants.forEach(p -> p.syncActive(next.isActive()));
    }

    /**
     * 도착 기록. 첫 도착이면 COUNTDOWN, countdownEndsAt = at + 10초, true. 카운트다운 안 도착이면 기록만 하고 false. 카운트다운
     * 뒤나 진행 중이 아니면 무시(false).
     */
    public boolean recordArrival(Side side, LocalDateTime at) {
        if (!status.isRunning() || isAfterCountdown(at)) {
            return false;
        }
        boolean firstForSide = arriveSide(side, at);
        if (!firstForSide || status == BattleStatus.COUNTDOWN) {
            return false;
        }
        moveTo(BattleStatus.COUNTDOWN);
        countdownEndsAt = at.plus(COUNTDOWN);
        return true;
    }

    private boolean isAfterCountdown(LocalDateTime at) {
        return status == BattleStatus.COUNTDOWN && at.isAfter(countdownEndsAt);
    }

    private boolean arriveSide(Side side, LocalDateTime at) {
        if (side instanceof Side.MemberSide member) {
            return requireParticipant(member.memberId()).arrive(at);
        }
        if (opponentKind == null || opponentArrivedAt != null) {
            return false;
        }
        opponentArrivedAt = at;
        return true;
    }

    public void recordOvertakes(long memberId, int count) {
        requireParticipant(memberId).recordOvertakes(count);
    }

    /** 기록 상대와의 거리 차 최댓값(결과 비교표). */
    void recordGaps(long memberId, Distance lead, Distance trail) {
        requireParticipant(memberId).recordGaps(lead, trail);
    }

    void markNewRecord(long memberId) {
        requireParticipant(memberId).markNewRecord();
    }

    public void markDisconnected(long memberId, LocalDateTime at) {
        requireParticipant(memberId).disconnect(at);
    }

    public void markReconnected(long memberId, LocalDateTime at) {
        requireParticipant(memberId).reconnect(at);
    }

    /**
     * 포기. 포기자 패, 상대(다른 참가자 또는 기록 상대) 승. FINISHED. 셀프런·셀럽런도 같다(2026-10-30 결정). 출발 전 대결은
     * cancelIfUnstarted로 취소한다.
     */
    public void forfeit(long memberId, LocalDateTime now) {
        BattleParticipant quitter = requireParticipant(memberId);
        if (!status.isRunning()) {
            throw new IllegalStateException("진행 중인 대결만 포기할 수 있다: " + status);
        }
        quitter.forfeit();
        Map<Side, Verdict> verdicts = new LinkedHashMap<>();
        for (Side side : sides()) {
            verdicts.put(side, side.equals(Side.member(memberId)) ? Verdict.LOSE : Verdict.WIN);
        }
        apply(verdicts, now, false);
    }

    /** 카운트다운 종료 또는 최대 시간(거리 × 15분) 초과 → RESULT_PENDING. */
    public void closeCountdown(LocalDateTime now) {
        if (!isCloseDue(now)) {
            throw new IllegalStateException("아직 끝낼 수 없는 대결: " + status);
        }
        moveTo(BattleStatus.RESULT_PENDING);
        closedAt = now;
    }

    /** 카운트다운이 끝났거나 최대 시간이 지났는가(BattleRepository.findCountdownDue의 조건). */
    public boolean isCloseDue(LocalDateTime now) {
        boolean countdownOver = status == BattleStatus.COUNTDOWN && !now.isBefore(countdownEndsAt);
        boolean overtime = status.isRunning() && maxEndsAt != null && !now.isBefore(maxEndsAt);
        return countdownOver || overtime;
    }

    /**
     * 참가자 기록 검증(BattleResultService.onRunSaved, D-51, D-482). 기록 ID·검증값·도달 경과 시간·유효 러닝 순번을 참가자에
     * 남긴다.
     */
    public void verify(long memberId, RecordCheck check) {
        requireParticipant(memberId).verify(check);
    }

    /**
     * 배치고사 판으로 표시한다(D-477). GHOST이고 저장 전이며 아직 표시하지 않았을 때만, 판 번호는 1~3. 아니면 IllegalArgumentException·
     * IllegalStateException.
     */
    public void markPlacement(int round) {
        if (round < 1 || round > 3) {
            throw new IllegalArgumentException("배치고사 판 번호는 1~3: " + round);
        }
        if (type != BattleType.GHOST || id != null || placementRound != null) {
            throw new IllegalStateException("배치고사 표시는 저장 전 고스트 대결 한 번만: " + type);
        }
        placementRound = round;
    }

    /** 배치고사 판 번호(만든 때). 배치고사 대결이 아니면 비어 있다. */
    public OptionalInt placementRound() {
        return placementRound == null ? OptionalInt.empty() : OptionalInt.of(placementRound);
    }

    /**
     * 판정(srs.md 6.3 4.5 의사결정표, A-10). 먼저 도착한 쪽 기록이 아직 없거나 아직 달리는 참가자의 기록이 없으면 비어 있다. 판정되면 FINISHED.
     * 이미 FINISHED였고 결과가 바뀌면 corrected = true. 결과가 같으면 비어 있고 아무것도 바꾸지 않는다(정정은 한 번 표시되면 유지).
     */
    public Optional<Judgement> judge(LocalDateTime now) {
        if (status == BattleStatus.WAITING_START
                || status == BattleStatus.CANCELLED
                || hasForfeit()) {
            return Optional.empty();
        }
        List<ArrivalClaim> claims = claims();
        if (claims.stream().anyMatch(this::mayStillRun)) {
            return Optional.empty();
        }
        List<ArrivalClaim> inTime = arrivedInTime(claims);
        if (awaitsDecisiveRecord(inTime)) {
            return Optional.empty();
        }
        Map<Side, Verdict> verdicts = decide(inTime);
        boolean finished = status == BattleStatus.FINISHED;
        if (finished && verdicts.equals(currentVerdicts())) {
            return Optional.empty(); // 같은 결과로 다시 판정: 정정 표시·판정 시각·사건을 그대로 둔다(멱등)
        }
        return Optional.of(apply(verdicts, now, finished));
    }

    /** 24시간 보류 만료. 없는 기록을 무효로 보고 판정한다. 보류 뒤 첫 판정이라 corrected = false. */
    public Optional<Judgement> expirePending(LocalDateTime now) {
        if (status != BattleStatus.RESULT_PENDING
                || now.isBefore(pendingSince().plus(PENDING_LIMIT))) {
            return Optional.empty();
        }
        participants.forEach(BattleParticipant::markMissingRecordInvalid);
        return judge(now); // RESULT_PENDING에서 정하는 첫 판정이라 정정이 아니다
    }

    /** 보류가 시작된 시각: 카운트다운 종료 시각, 카운트다운 없이 끝났으면 닫은 시각. */
    LocalDateTime pendingSince() {
        return countdownEndsAt != null ? countdownEndsAt : closedAt;
    }

    private boolean hasForfeit() {
        return participants.stream().anyMatch(BattleParticipant::isForfeited);
    }

    private List<ArrivalClaim> claims() {
        List<ArrivalClaim> claims = new ArrayList<>();
        participants.forEach(p -> claims.add(p.claim(startAt)));
        if (opponentKind != null) {
            LocalDateTime at = startAt == null ? null : opponentArrivesAt;
            claims.add(new ArrivalClaim(Side.OPPONENT, at, Boolean.TRUE));
        }
        return claims;
    }

    /** 기록이 없는 참가자가 아직 달리는 중이거나, 기록 대결이라 도착 시각을 알 수 없으면 기다린다. */
    private boolean mayStillRun(ArrivalClaim claim) {
        if (claim.hasRecord()) {
            return false;
        }
        return status.isRunning() || (!claim.hasArrival() && opponentKind != null);
    }

    private List<ArrivalClaim> arrivedInTime(List<ArrivalClaim> claims) {
        return claims.stream()
                .filter(ArrivalClaim::hasArrival)
                .filter(c -> countdownEndsAt == null || !c.arrivedAt().isAfter(countdownEndsAt))
                .sorted(Comparator.comparing(ArrivalClaim::arrivedAt))
                .toList();
    }

    /** 먼저 도착한 쪽, 또는 1초 안에 따라 도착한 쪽의 기록이 아직 없으면 보류(FR-108). */
    private static boolean awaitsDecisiveRecord(List<ArrivalClaim> inTime) {
        if (inTime.isEmpty()) {
            return false;
        }
        if (!inTime.get(0).hasRecord()) {
            return true;
        }
        return inTime.size() > 1 && withinDrawWindow(inTime) && !inTime.get(1).hasRecord();
    }

    private static boolean withinDrawWindow(List<ArrivalClaim> inTime) {
        Duration gap = Duration.between(inTime.get(0).arrivedAt(), inTime.get(1).arrivedAt());
        return gap.compareTo(DRAW_WINDOW) <= 0;
    }

    /** 의사결정표 4규칙. */
    private Map<Side, Verdict> decide(List<ArrivalClaim> inTime) {
        if (inTime.isEmpty()) {
            return everyone(Verdict.DRAW); // 규칙 4
        }
        ArrivalClaim first = inTime.get(0);
        if (inTime.size() > 1
                && withinDrawWindow(inTime)
                && first.isValid()
                && inTime.get(1).isValid()) {
            return everyone(Verdict.DRAW); // 규칙 1
        }
        if (first.isValid()) {
            return winnerTakesAll(first.side()); // 규칙 2
        }
        Optional<ArrivalClaim> second =
                inTime.stream().skip(1).filter(ArrivalClaim::isValid).findFirst();
        if (second.isPresent()) {
            return winnerTakesAll(second.get().side()); // 규칙 3, 다른 쪽 승
        }
        Map<Side, Verdict> verdicts = everyone(Verdict.DRAW); // 규칙 3, 무승부
        verdicts.put(first.side(), Verdict.LOSE);
        return verdicts;
    }

    private Map<Side, Verdict> everyone(Verdict verdict) {
        Map<Side, Verdict> verdicts = new LinkedHashMap<>();
        sides().forEach(side -> verdicts.put(side, verdict));
        return verdicts;
    }

    private Map<Side, Verdict> winnerTakesAll(Side winner) {
        Map<Side, Verdict> verdicts = everyone(Verdict.LOSE);
        verdicts.put(winner, Verdict.WIN);
        return verdicts;
    }

    private List<Side> sides() {
        List<Side> sides = new ArrayList<>();
        participants.forEach(p -> sides.add(Side.member(p.getMemberId())));
        if (opponentKind != null) {
            sides.add(Side.OPPONENT);
        }
        return sides;
    }

    private Judgement apply(Map<Side, Verdict> verdicts, LocalDateTime now, boolean isCorrection) {
        participants.forEach(p -> p.judge(verdicts.get(Side.member(p.getMemberId()))));
        opponentVerdict = verdicts.get(Side.OPPONENT);
        Side winner = winnerOf(verdicts);
        winnerOpponent = Side.OPPONENT.equals(winner);
        winnerMemberId =
                winner instanceof Side.MemberSide member ? Long.valueOf(member.memberId()) : null;
        corrected = corrected || isCorrection;
        judgedAt = now;
        moveTo(BattleStatus.FINISHED);
        return getJudgement().orElseThrow();
    }

    private static Side winnerOf(Map<Side, Verdict> verdicts) {
        return verdicts.entrySet().stream()
                .filter(e -> e.getValue() == Verdict.WIN)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private Map<Side, Verdict> currentVerdicts() {
        return getJudgement().map(Judgement::verdicts).orElse(Map.of());
    }

    private Duration maxDuration() {
        return Duration.ofMillis(distanceMeters * MAX_MILLIS_PER_METER);
    }

    private void requireStatus(BattleStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("대결 상태가 " + expected + "가 아니다: " + status);
        }
    }

    private BattleParticipant requireParticipant(long memberId) {
        return participant(memberId)
                .orElseThrow(() -> new IllegalArgumentException("참가자가 아니다: " + memberId));
    }

    public boolean isParticipant(long memberId) {
        return participant(memberId).isPresent();
    }

    /** 활성 = WAITING_START, IN_PROGRESS, COUNTDOWN. */
    public boolean isActive() {
        return status.isActive();
    }

    public Optional<BattleParticipant> participant(long memberId) {
        return participants.stream().filter(p -> p.getMemberId() == memberId).findFirst();
    }

    /** 랜덤 대결의 다른 참가자. */
    public Optional<BattleParticipant> rivalOf(long memberId) {
        return participants.stream().filter(p -> p.getMemberId() != memberId).findFirst();
    }

    public List<BattleParticipant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    public List<Long> memberIds() {
        return participants.stream().map(BattleParticipant::getMemberId).toList();
    }

    public Long getId() {
        return id;
    }

    public BattleType getType() {
        return type;
    }

    public Distance getDistance() {
        return Distance.ofMeters(distanceMeters);
    }

    public BattleStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Optional<LocalDateTime> getStartAt() {
        return Optional.ofNullable(startAt);
    }

    public Optional<LocalDateTime> getCountdownEndsAt() {
        return Optional.ofNullable(countdownEndsAt);
    }

    /** 기록 상대 도착 시각(시작 뒤에만). */
    public Optional<LocalDateTime> getOpponentArrivesAt() {
        return Optional.ofNullable(opponentArrivesAt);
    }

    /** 기록 상대 도착을 기록했는가(BattleRepository.findOpponentArrivalDue의 조건). */
    public boolean isOpponentArrivalDue(LocalDateTime now) {
        return status.isRunning()
                && opponentArrivesAt != null
                && opponentArrivedAt == null
                && !now.isBefore(opponentArrivesAt);
    }

    public Optional<OpponentRef> getOpponent() {
        if (opponentKind == null) {
            return Optional.empty();
        }
        return Optional.of(
                new OpponentRef(
                        opponentKind,
                        opponentRecordId,
                        DisplayName.of(opponentName),
                        opponentTier,
                        Duration.ofMillis(opponentArrivalMillis)));
    }

    /** 유명인 캐릭터 그림 키. 유명인 대결이 아니거나 키 없이 등록한 유명인이면 비어 있다. */
    public Optional<String> getOpponentFigure() {
        return Optional.ofNullable(opponentFigure);
    }

    /** 기록 상대 표(BattleStart·기록 대결 시작 정보). 랜덤 대결이면 비어 있다. */
    public Optional<OpponentTrack> getOpponentTrack() {
        return opponentKind == null
                ? Optional.empty()
                : Optional.of(OpponentTrack.decode(opponentTrack));
    }

    /** 기록 대결 비교표의 상대 값(대결을 만들 때 남긴 값, FR-172). 랜덤 대결이면 비어 있다. */
    public Optional<ComparisonValues> opponentValues() {
        if (opponentKind == null) {
            return Optional.empty();
        }
        return Optional.of(
                new ComparisonValues(
                        Duration.ofMillis(opponentArrivalMillis),
                        opponentPaceSeconds == null ? null : new Pace(opponentPaceSeconds),
                        opponentFastestKmSeconds == null
                                ? null
                                : new Pace(opponentFastestKmSeconds),
                        null,
                        null));
    }

    /** 대결 거리에 해당하는 기준 거리(랜덤·고스트·나와의 싸움). 유명인 기록 거리가 기준 거리가 아니면 비어 있다. */
    public Optional<StandardDistance> standardDistance() {
        return Arrays.stream(StandardDistance.values())
                .filter(s -> s.distance().meters() == distanceMeters)
                .findFirst();
    }

    public Optional<Judgement> getJudgement() {
        if (judgedAt == null) {
            return Optional.empty();
        }
        Map<Side, Verdict> verdicts = new LinkedHashMap<>();
        participants.forEach(
                p -> p.getVerdict().ifPresent(v -> verdicts.put(Side.member(p.getMemberId()), v)));
        if (opponentVerdict != null) {
            verdicts.put(Side.OPPONENT, opponentVerdict);
        }
        Side winner = winnerOpponent ? Side.OPPONENT : null;
        if (winnerMemberId != null) {
            winner = Side.member(winnerMemberId);
        }
        return Optional.of(new Judgement(verdicts, winner, corrected, judgedAt));
    }
}
