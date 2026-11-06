package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
import com.a207.runkk.member.Gender;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 대결 참가자 한 명(설계 6.1.6, 상태도 2.4.3). Battle이 합성으로 갖고(cascade ALL) 혼자 저장하지 않는다. 판정 도착 시각은 대결 startAt +
 * 기록상 도달 경과 시간이다(D-30).
 */
@Entity
@Table(name = "battle_participant")
public class BattleParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "record_id", length = 36)
    private String recordId;

    /** null = 기록 없음. */
    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "reach_millis")
    private Long reachMillis;

    /** 붙은 기록의 유효 러닝 순번(무효·없음이면 null, D-482). 배치고사 판 셈이 확정 판 순번으로 남긴다. */
    @Column(name = "valid_run_ordinal")
    private Integer validRunOrdinal;

    @Column(name = "overtakes", nullable = false)
    private int overtakes;

    @Column(name = "forfeited", nullable = false)
    private boolean forfeited;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 10)
    private Verdict verdict;

    @Column(name = "max_lead_meters")
    private Long maxLeadMeters;

    @Column(name = "max_trail_meters")
    private Long maxTrailMeters;

    @Column(name = "new_record", nullable = false)
    private boolean newRecord;

    /** 다른 참가자에게 보이는 캐릭터 성별(랜덤·친구 대결, 2026-10-31 결정). 성사 때 정하고 기록 대결은 null이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "shown_gender", length = 6)
    private Gender shownGender;

    /** 활성 대결이면 회원 ID, 아니면 null. 고유 제약이 회원별 활성 대결 1개를 지킨다(Battle.moveTo가 맞춘다). */
    @Column(name = "active_member_id", unique = true)
    private Long activeMemberId;

    @ElementCollection
    @CollectionTable(
            name = "battle_disconnection",
            joinColumns = @JoinColumn(name = "participant_id"))
    @OrderColumn(name = "seq")
    private List<Disconnection> disconnections = new ArrayList<>();

    protected BattleParticipant() {}

    BattleParticipant(long memberId) {
        this.memberId = memberId;
    }

    void syncActive(boolean active) {
        activeMemberId = active ? memberId : null;
    }

    /** 처음 도착이면 true. */
    boolean arrive(LocalDateTime at) {
        if (arrivedAt != null) {
            return false;
        }
        arrivedAt = at;
        return true;
    }

    /** 기록 검증 결과, 기록상 대결 거리 도달 경과 시간(없으면 null), 유효 러닝 순번(D-482). */
    void verify(RecordCheck check) {
        this.recordId = check.recordId();
        this.verified = check.valid();
        this.reachMillis = check.reachElapsed() == null ? null : check.reachElapsed().toMillis();
        this.validRunOrdinal = check.validRunOrdinal() > 0 ? check.validRunOrdinal() : null;
    }

    /** 기록이 삭제되었다(v0.14 D-232). 판정·도달 시간은 그대로 두고 기록 ID만 비운다. */
    void forgetRecord() {
        this.recordId = null;
    }

    /** 24시간이 지나도 기록이 없으면 무효로 본다(A-10 expirePending). */
    void markMissingRecordInvalid() {
        if (verified == null) {
            verified = false;
        }
    }

    void forfeit() {
        forfeited = true;
    }

    /** 한 번만 정한다(다시 연결·결과 화면에서도 같은 그림). */
    void showAs(Gender gender) {
        if (shownGender != null) {
            throw new IllegalStateException("이미 정한 성별: " + shownGender);
        }
        shownGender = gender;
    }

    void recordOvertakes(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("추월 횟수는 0 이상: " + count);
        }
        overtakes = count;
    }

    /** 기록 상대와의 거리 차 최댓값(앞섰을 때, 뒤처졌을 때). 비교표 MAX_LEAD·MAX_GAP 줄에 쓴다. */
    void recordGaps(Distance lead, Distance trail) {
        maxLeadMeters = lead.meters();
        maxTrailMeters = trail.meters();
    }

    void markNewRecord() {
        newRecord = true;
    }

    void judge(Verdict decided) {
        verdict = decided;
    }

    void disconnect(LocalDateTime at) {
        if (isDisconnected()) {
            return;
        }
        disconnections.add(new Disconnection(at));
    }

    void reconnect(LocalDateTime at) {
        if (isDisconnected()) {
            disconnections.get(disconnections.size() - 1).close(at);
        }
    }

    boolean isDisconnected() {
        return !disconnections.isEmpty() && disconnections.get(disconnections.size() - 1).isOpen();
    }

    /** 판정용 도착 주장 = startAt + reachElapsed(D-30). 기록이 없으면 실시간 도착 시각. */
    ArrivalClaim claim(LocalDateTime startAt) {
        if (verified == null) {
            return new ArrivalClaim(Side.member(memberId), arrivedAt, null);
        }
        LocalDateTime at =
                reachMillis == null || startAt == null ? null : startAt.plusNanos(reachNanos());
        return new ArrivalClaim(Side.member(memberId), at, verified);
    }

    private long reachNanos() {
        return Duration.ofMillis(reachMillis).toNanos();
    }

    public long getMemberId() {
        return memberId;
    }

    public Optional<LocalDateTime> getArrivedAt() {
        return Optional.ofNullable(arrivedAt);
    }

    public Optional<String> getRecordId() {
        return Optional.ofNullable(recordId);
    }

    /** 기록 검증 결과. 기록이 없으면 비어 있다. */
    public Optional<Boolean> getVerified() {
        return Optional.ofNullable(verified);
    }

    /** 붙은 기록의 유효 러닝 순번(D-482). 없으면 비어 있다. */
    public OptionalInt getValidRunOrdinal() {
        return validRunOrdinal == null ? OptionalInt.empty() : OptionalInt.of(validRunOrdinal);
    }

    public Optional<Duration> getReachElapsed() {
        return Optional.ofNullable(reachMillis).map(Duration::ofMillis);
    }

    public int getOvertakes() {
        return overtakes;
    }

    public boolean isForfeited() {
        return forfeited;
    }

    public Optional<Verdict> getVerdict() {
        return Optional.ofNullable(verdict);
    }

    public Optional<Gender> getShownGender() {
        return Optional.ofNullable(shownGender);
    }

    public boolean isNewRecord() {
        return newRecord;
    }

    public Optional<Distance> maxLead() {
        return Optional.ofNullable(maxLeadMeters).map(Distance::ofMeters);
    }

    public Optional<Distance> maxTrail() {
        return Optional.ofNullable(maxTrailMeters).map(Distance::ofMeters);
    }

    public List<Disconnection> getDisconnections() {
        return Collections.unmodifiableList(disconnections);
    }
}
