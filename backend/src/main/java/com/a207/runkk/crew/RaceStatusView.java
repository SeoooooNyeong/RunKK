package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 크루 주간 현황(자료사전 레이스현황, FR-140). 기여율 = ⌊기여 ÷ 합계 × 100⌋.
 *
 * @param crewId 크루 ID
 * @param weekStart 주 시작일(월요일)
 * @param total 합산 거리
 * @param closesAt 마감 일시(일요일 24시)
 * @param contributors 기여자, 거리 큰 순
 * @param myRate 본인 기여율
 */
public record RaceStatusView(
        long crewId,
        LocalDate weekStart,
        Distance total,
        LocalDateTime closesAt,
        List<Line> contributors,
        int myRate) {

    public RaceStatusView {
        contributors = List.copyOf(contributors);
    }

    /**
     * 기여자 한 명.
     *
     * @param name 표시명(탈퇴면 "탈퇴한 사용자")
     * @param distance 기여 거리
     * @param rate 기여율
     */
    public record Line(DisplayName name, Distance distance, int rate) {}
}
