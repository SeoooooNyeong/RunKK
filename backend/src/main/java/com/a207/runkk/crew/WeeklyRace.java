package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 크루 한 곳의 한 주 합산 거리(설계 6.1.7, FR-139, FR-140). 저장은 WeeklyRaceRepository.addAtomically 한 번으로
 * 하고(5.3), 이 클래스의 add는 단위 시험과 인메모리 저장소에서 같은 규칙(이중 합산 금지)을 보장하는 기준 구현이다. 저장소는 읽은 행으로 이 객체를 만든다.
 */
public class WeeklyRace {

    /** 기여율 백분율. */
    private static final int PERCENT = 100;

    private final long crewId;
    private final Week week;
    private Distance total = Distance.ZERO;
    private final Map<String, Long> ownerOf = new LinkedHashMap<>();
    private final Map<String, Distance> recordDistances = new HashMap<>();

    private WeeklyRace(long crewId, Week week) {
        this.crewId = crewId;
        this.week = Objects.requireNonNull(week, "week");
    }

    /** 합계 0의 주. */
    public static WeeklyRace empty(long crewId, Week week) {
        return new WeeklyRace(crewId, week);
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
