package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 크루 한 곳의 한 주 합산 거리와 공동 목표(설계 6.1.7, FR-138~FR-141, FR-217, D-105). 저장은
 * WeeklyRaceRepository.addAtomically 한 번으로 하고(5.3), 이 클래스의 add는 단위 시험과 인메모리 저장소에서 같은 규칙(이중 합산 금지)을
 * 보장하는 기준 구현이다. 저장소는 읽은 행으로 이 객체를 만든다.
 */
public class WeeklyRace {

    /** 기여율 백분율. */
    private static final int PERCENT = 100;

    /** 공동 목표 하한(D-113). */
    public static final Distance MIN_GOAL = Distance.ofMeters(1_000);

    private final long crewId;
    private final Week week;
    private Distance total = Distance.ZERO;
    private final Map<String, Long> ownerOf = new LinkedHashMap<>();
    private final Map<String, Distance> recordDistances = new HashMap<>();
    private Distance goal;
    private Long goalSetBy;
    private LocalDateTime achievedAt;

    private WeeklyRace(long crewId, Week week) {
        this.crewId = crewId;
        this.week = Objects.requireNonNull(week, "week");
    }

    /** 합계 0의 주. */
    public static WeeklyRace empty(long crewId, Week week) {
        return new WeeklyRace(crewId, week);
    }

    /**
     * 주 행에서 읽은 목표 상태(목표·설정자·달성 일시).
     *
     * @param goal 목표, 없으면 null
     * @param setBy 목표 설정자 회원 ID, 없으면 null
     * @param achievedAt 달성 일시, 아직이면 null
     */
    public record GoalState(Distance goal, Long setBy, LocalDateTime achievedAt) {}

    /** 저장소가 읽은 주 행(목표·달성 일시 포함)으로 만든다. 기여는 add·addAnonymized로 더한다. */
    public static WeeklyRace restore(long crewId, Week week, GoalState state) {
        WeeklyRace race = new WeeklyRace(crewId, week);
        race.goal = state.goal();
        race.goalSetBy = state.setBy();
        race.achievedAt = state.achievedAt();
        return race;
    }

    /**
     * 목표 설정·변경(FR-138). 1km 미만이면 GOAL_INVALID. 이미 달성한 주면 achievedAt은 그대로 두고 다시 판정하지 않는다(달성 기록은 저장소의
     * 조건부 UPDATE markAchievedIfReached가 한 번만 쓴다, A-12).
     */
    public void setGoal(Distance newGoal, long setterId) {
        if (newGoal == null || newGoal.compareTo(MIN_GOAL) < 0) {
            throw new BusinessRuleException(ReasonCode.GOAL_INVALID);
        }
        this.goal = newGoal;
        this.goalSetBy = setterId;
    }

    /** achievedAt != null. */
    public boolean isAchieved() {
        return achievedAt != null;
    }

    /** 진행률 min(100, ⌊합계 × 100 ÷ 목표⌋), 목표가 없으면 빈 값(FR-140). */
    public Optional<Integer> goalProgressPercent() {
        if (goal == null) {
            return Optional.empty();
        }
        return Optional.of((int) Math.min(PERCENT, total.meters() * PERCENT / goal.meters()));
    }

    public Optional<Distance> goal() {
        return Optional.ofNullable(goal);
    }

    public Optional<Long> goalSetBy() {
        return Optional.ofNullable(goalSetBy);
    }

    public Optional<LocalDateTime> achievedAt() {
        return Optional.ofNullable(achievedAt);
    }

    /** 같은 recordId가 이미 있으면 false(이중 합산 방지). */
    public boolean add(long memberId, String recordId, Distance d) {
        return put(memberId, recordId, d);
    }

    /** 탈퇴로 익명화된 기여(저장소가 읽은 행을 되살릴 때). */
    public boolean addAnonymized(String recordId, Distance d) {
        return put(null, recordId, d);
    }

    private boolean put(Long memberId, String recordId, Distance d) {
        if (recordDistances.containsKey(recordId)) {
            return false;
        }
        ownerOf.put(recordId, memberId);
        recordDistances.put(recordId, d);
        total = total.plus(d);
        return true;
    }

    /** ⌊기여 ÷ 합계 × 100⌋, 합계 0이면 0. */
    public int contributionRate(long memberId) {
        if (total.meters() == 0) {
            return 0;
        }
        return (int) (contributedBy(memberId).meters() * PERCENT / total.meters());
    }

    private Distance contributedBy(Long memberId) {
        return ownerOf.entrySet().stream()
                .filter(e -> Objects.equals(e.getValue(), memberId))
                .map(e -> recordDistances.get(e.getKey()))
                .reduce(Distance.ZERO, Distance::plus);
    }

    /** week.closesAt()(일요일 24시). */
    public LocalDateTime closesAt() {
        return week.closesAt();
    }

    /** 기록 하나의 반영 거리. 없으면 0(FR-078). */
    public Distance contributionOf(String recordId) {
        return recordDistances.getOrDefault(recordId, Distance.ZERO);
    }

    /** 기여자별(익명화된 기여는 memberId null로 한 줄). */
    public List<RaceContribution> contributions() {
        Map<Long, Set<String>> records = new LinkedHashMap<>();
        ownerOf.forEach(
                (recordId, owner) ->
                        records.computeIfAbsent(owner, k -> new HashSet<>()).add(recordId));
        List<RaceContribution> result = new ArrayList<>();
        records.forEach(
                (owner, ids) -> result.add(new RaceContribution(owner, contributedBy(owner), ids)));
        return result;
    }

    public long crewId() {
        return crewId;
    }

    public Week week() {
        return week;
    }

    public Distance total() {
        return total;
    }
}
