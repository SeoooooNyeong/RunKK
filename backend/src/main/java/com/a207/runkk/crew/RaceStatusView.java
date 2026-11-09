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
 * @param goal 그 주 공동 목표(이어 쓴 값 포함), 없으면 null
 * @param goalProgressPercent ⌊합계 × 100 ÷ 목표⌋(100을 넘을 수 있다, C2), 목표가 없으면 null
 * @param achievedAt 목표 달성 일시, 아니면 null
 * @param celebration 달성한 주의 축하 카드, 아니면 null
 */
public record RaceStatusView(
        long crewId,
        LocalDate weekStart,
        Distance total,
        LocalDateTime closesAt,
        List<Line> contributors,
        int myRate,
        Distance goal,
        Integer goalProgressPercent,
        LocalDateTime achievedAt,
        CelebrationCard celebration) {

    public RaceStatusView {
        contributors = List.copyOf(contributors);
    }

    /**
     * 기여자 한 명.
     *
     * @param name 표시명(탈퇴면 "탈퇴한 사용자")
     * @param distance 기여 거리
     * @param rate 기여율
     * @param memberId 기여자 회원 ID, 탈퇴했으면 null(앱이 본인 줄을 가린다)
     */
    public record Line(DisplayName name, Distance distance, int rate, Long memberId) {}
}
