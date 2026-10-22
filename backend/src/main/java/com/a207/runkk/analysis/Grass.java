package com.a207.runkk.analysis;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 최근 12주 날짜별 러닝 거리 격자(설계 6.1.4 Grass, FR-053, FR-055).
 *
 * @param cells 날짜 → 그날 유효 거리(기간 안 모든 날짜, 기록이 없으면 0)
 */
public record Grass(SortedMap<LocalDate, Distance> cells) {

    /** 잔디 기간(주). */
    public static final int WEEKS = 12;

    /** 진하기 단계 수(0은 기록 없음, 1~4). */
    static final int LEVELS = 4;

    public Grass {
        cells = Collections.unmodifiableSortedMap(new TreeMap<>(cells));
    }

    /** 기간 안 날짜마다 거리를 채운다. 기간 밖 값은 버린다. */
    public static Grass of(Map<LocalDate, Distance> daily, DateRange last12Weeks) {
        SortedMap<LocalDate, Distance> cells = new TreeMap<>();
        last12Weeks.days().forEach(day -> cells.put(day, daily.getOrDefault(day, Distance.ZERO)));
        return new Grass(cells);
    }

    /** base가 속한 주를 포함한 최근 12주. */
    public static DateRange rangeEndingOn(LocalDate base) {
        return DateRange.lastWeeks(base, WEEKS);
    }

    /** 0~4단계. 그 기간 최댓값 대비 비율로 정한다. */
    public int intensityOn(LocalDate day) {
        long value = cells.getOrDefault(day, Distance.ZERO).meters();
        long max = cells.values().stream().mapToLong(Distance::meters).max().orElse(0);
        if (value == 0 || max == 0) {
            return 0;
        }
        return (int) Math.max(1, Math.ceil((double) value * LEVELS / max));
    }
}
