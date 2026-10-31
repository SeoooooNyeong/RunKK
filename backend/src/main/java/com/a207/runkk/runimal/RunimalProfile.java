package com.a207.runkk.runimal;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 회원의 Runimal 판정 상태(설계 6.1.8, 상태도 2.4.11). 판정 시점은 running이 정한 유효 러닝 순번으로 정하고 validRunCount는 직전 반영
 * 순번으로만 쓴다(D-37). 앞 순번 처리가 실패해 건너뛰어 와도 그 사이 판정 지점을 놓치지 않는다(D-40).
 */
@Entity
@Table(name = "runimal_profile")
public class RunimalProfile {

    /** 임시 유형 판정 지점(FR-152). */
    static final int PROVISIONAL_POINT = 3;

    /** 확정 판정 주기(FR-153). */
    static final int CONFIRM_PERIOD = 10;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "valid_run_count", nullable = false)
    private int validRunCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "provisional_type", length = 20)
    private RunimalType provisionalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmed_type", length = 20)
    private RunimalType confirmedType;

    @Column(name = "companion_ratio")
    private Double companionRatio;

    @Column(name = "distance_ratio")
    private Double distanceRatio;

    @Column(name = "course_ratio")
    private Double courseRatio;

    @Column(name = "habit_ratio")
    private Double habitRatio;

    @Column(name = "judged_at")
    private LocalDateTime judgedAt;

    /** null이면 아직 저장 전(새 행). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 확정받은 적 있는 유형(v0.14 D-245, 표 runimal_confirmed_history). confirm이 넣는다. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "runimal_confirmed_history",
            joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private Set<RunimalType> confirmedHistory = new HashSet<>();

    /** 직전 onValidRun 결과. assignProvisional·confirm의 사전 조건. 저장하지 않는다. */
    @Transient private JudgeTrigger pendingTrigger = JudgeTrigger.NONE;

    protected RunimalProfile() {}

    private RunimalProfile(long memberId) {
        this.memberId = memberId;
    }

    public static RunimalProfile create(long memberId) {
        return new RunimalProfile(memberId);
    }

    /**
     * 유효 러닝 순번 반영(D-37, D-40). 순번 ≤ validRunCount면 NONE. 아니면 validRunCount &lt; p ≤ 순번인 판정 지점 p 중
     * 10의 배수가 있으면 CONFIRM, 없고 3이 있으면 PROVISIONAL, 둘 다 없으면 NONE(4~9회는 다시 판정하지 않음, Q-47).
     */
    public JudgeTrigger onValidRun(int validRunOrdinal) {
        if (validRunOrdinal <= validRunCount) {
            pendingTrigger = JudgeTrigger.NONE;
            return pendingTrigger;
        }
        boolean passesConfirm = validRunOrdinal / CONFIRM_PERIOD > validRunCount / CONFIRM_PERIOD;
        boolean passesProvisional =
                validRunCount < PROVISIONAL_POINT && validRunOrdinal >= PROVISIONAL_POINT;
        validRunCount = validRunOrdinal;
        if (passesConfirm) {
            pendingTrigger = JudgeTrigger.CONFIRM;
        } else if (passesProvisional) {
            pendingTrigger = JudgeTrigger.PROVISIONAL;
        } else {
            pendingTrigger = JudgeTrigger.NONE;
        }
        return pendingTrigger;
    }

    /** onValidRun이 PROVISIONAL을 준 뒤에만. */
    void assignProvisional(RunimalType t, AxisRatios r, LocalDateTime now) {
        requireTrigger(JudgeTrigger.PROVISIONAL);
        provisionalType = t;
        keep(r, now);
    }

    /** onValidRun이 CONFIRM을 준 뒤에만. 확정 유형이 바뀌었으면 true(새 카드 표시, FR-153). */
    boolean confirm(RunimalType t, AxisRatios r, LocalDateTime now) {
        requireTrigger(JudgeTrigger.CONFIRM);
        boolean changed = confirmedType != t;
        confirmedType = t;
        confirmedHistory.add(t);
        keep(r, now);
        return changed;
    }

    /** 이 유형으로 한 번이라도 확정받았는가(16유형 목록의 흐림, FR-226, v0.14 D-245). */
    public boolean hasEverConfirmed(RunimalType t) {
        return confirmedHistory.contains(t);
    }

    /** 확정받은 적 있는 유형(불변 사본). */
    public Set<RunimalType> confirmedHistory() {
        return confirmedHistory.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(confirmedHistory));
    }

    private void requireTrigger(JudgeTrigger expected) {
        if (pendingTrigger != expected) {
            throw new IllegalStateException("판정 지점이 아니다: " + pendingTrigger);
        }
        pendingTrigger = JudgeTrigger.NONE;
    }

    private void keep(AxisRatios r, LocalDateTime now) {
        companionRatio = r.ratioOfA(RunimalAxis.COMPANION);
        distanceRatio = r.ratioOfA(RunimalAxis.DISTANCE);
        courseRatio = r.ratioOfA(RunimalAxis.COURSE);
        habitRatio = r.ratioOfA(RunimalAxis.HABIT);
        judgedAt = now;
    }

    /** 3 미만이면 3 − count, 아니면 10 − (count mod 10)(FR-154). */
    public int remainingToNextJudgement() {
        if (validRunCount < PROVISIONAL_POINT) {
            return PROVISIONAL_POINT - validRunCount;
        }
        return CONFIRM_PERIOD - validRunCount % CONFIRM_PERIOD;
    }

    /** 확정 우선, 없으면 임시, 둘 다 없으면 비어 있다. */
    public Optional<RunimalCardView> card() {
        return currentType().map(t -> RunimalCardView.of(t, ratios(), confirmedType == null));
    }

    public Optional<RunimalType> currentType() {
        return Optional.ofNullable(confirmedType).or(() -> Optional.ofNullable(provisionalType));
    }

    private AxisRatios ratios() {
        Map<RunimalAxis, Double> map = new EnumMap<>(RunimalAxis.class);
        map.put(RunimalAxis.COMPANION, companionRatio);
        map.put(RunimalAxis.DISTANCE, distanceRatio);
        map.put(RunimalAxis.COURSE, courseRatio);
        map.put(RunimalAxis.HABIT, habitRatio);
        return new AxisRatios(map);
    }

    public long getMemberId() {
        return memberId;
    }

    public int getValidRunCount() {
        return validRunCount;
    }

    public Optional<RunimalType> getProvisionalType() {
        return Optional.ofNullable(provisionalType);
    }

    public Optional<RunimalType> getConfirmedType() {
        return Optional.ofNullable(confirmedType);
    }
}
