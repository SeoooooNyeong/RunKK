package com.a207.runkk.battle;

import com.a207.runkk.common.Distance;
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

    /** 처음 도착이면 true. */
    boolean arrive(LocalDateTime at) {
        if (arrivedAt != null) {
            return false;
        }
        arrivedAt = at;
        return true;
    }

    /** 기록 검증 결과와 기록상 대결 거리 도달 경과 시간(없으면 null). */
    void verify(String recordId, boolean valid, Duration reachElapsed) {
        this.recordId = recordId;
        this.verified = valid;
        this.reachMillis = reachElapsed == null ? null : reachElapsed.toMillis();
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
