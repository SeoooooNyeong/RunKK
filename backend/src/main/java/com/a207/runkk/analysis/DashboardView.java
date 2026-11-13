package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DistanceUnit;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 대시보드 정보(설계 6.1.4 DashboardService.dashboard, srs.md 6.3 3.1, FR-050~FR-057, FR-077).
 *
 * @param baseDate 기준일
 * @param unit 회원 거리 표시 단위
 * @param today 오늘 요약(무효 포함)
 * @param total 누적 요약(무효 포함)
 * @param landmark 누적 유효 거리 랜드마크 문구(1~3단계)
 * @param streakDays 개인 스트릭
 * @param streakWeeks 개인 주 단위 연속(월요일 시작, 그 주에 유효 러닝 1번 이상, 2026-10-31 결정)
 * @param grass 개인 잔디(최근 12주)
 * @param weekly 이번 주 목표 달성률, 목표가 없으면 null(목표 설정 안내)
 * @param crew 크루 영역, 크루가 없으면 null
 * @param calendar 기준일이 속한 달의 날짜별 기록
 * @param latestRun 본인 기록 가운데 시작 시각이 가장 늦은 1건(유효·무효 모두, 기록 목록 맨 위 행과 같은 값), 없으면 null(05-21 D-491).
 *     대결 결과는 analysis가 battle을 모르므로 web.record가 붙인다(D-33)
 * @param weekRuns 이번 주(기준일이 속한 주 월요일 ~ 기준일, KST) 유효 러닝 횟수. 주간 목표와 무관하게 늘 준다(실기기 DV3-4)
 * @param weekMeters 같은 기간 유효 거리
 */
public record DashboardView(
        LocalDate baseDate,
        DistanceUnit unit,
        Totals today,
        Totals total,
        String landmark,
        int streakDays,
        Grass grass,
        WeeklyAchievement weekly,
        CrewArea crew,
        Map<LocalDate, List<CalendarRun>> calendar,
        int rewardBalance,
        int monthRuns,
        Distance monthMeters,
        int streakWeeks,
        RecordRow latestRun,
        int weekRuns,
        Distance weekMeters) {

    /**
     * 거리·시간·횟수 합.
     *
     * @param distance 거리
     * @param elapsed 시간
     * @param count 횟수
     */
    public record Totals(Distance distance, Duration elapsed, int count) {}

    /**
     * 크루 영역(FR-054, FR-055, FR-077). 크루원 개인은 드러내지 않는다.
     *
     * @param crewId 크루 ID
     * @param crewName 크루명(영역 제목)
     * @param streakDays 크루 스트릭
     * @param grass 크루 잔디
     * @param landmark 크루 누적 거리 랜드마크 문구(1~4단계)
     */
    public record CrewArea(
            long crewId, String crewName, int streakDays, Grass grass, String landmark) {}

    /**
     * 달력 칸의 기록 하나(FR-057).
     *
     * @param recordId 기록 ID
     * @param distance 거리
     * @param valid 유효 러닝
     */
    public record CalendarRun(String recordId, Distance distance, boolean valid) {}
}
