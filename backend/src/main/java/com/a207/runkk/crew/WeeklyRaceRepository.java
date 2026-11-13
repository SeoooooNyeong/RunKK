package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 크루 주간 거리 저장소 포트(설계 6.1.7, 6.2 A-12). 구현은 infra.crew.JpaWeeklyRaceRepository. 합산은 메모리에서 읽고-더하고-쓰지
 * 않고 addAtomically 한 번으로 한다(5.3).
 */
public interface WeeklyRaceRepository {

    /**
     * 기여 행 삽입이 성공했을 때만 합계를 원자적으로 늘리고 true. 같은 (크루, 주, 기록)이 이미 있으면 false. 그 주 행을 처음 만들면 직전 목표를 이어
     * 쓴다(latestGoalBefore, FR-138, D-105).
     */
    boolean addAtomically(RaceContributionKey key, Distance d);

    /** 그 주 행(목표·달성 일시)과 기여. 행이 없으면 빈 값. */
    Optional<WeeklyRace> find(long crewId, Week week);

    /** 그 주 행을 읽거나 합계 0으로 만든다(만들 때 목표 이어 쓰기). setGoal용. */
    WeeklyRace findOrCreate(long crewId, Week week);

    /** 목표·설정자를 저장한다(WeeklyRace.setGoal 뒤). 달성 일시는 바꾸지 않는다. */
    void saveGoal(WeeklyRace race);

    /**
     * 조건부 UPDATE … SET achieved_at = now WHERE achieved_at IS NULL AND goal_meters IS NOT NULL AND
     * total_meters &gt;= goal_meters. 한 행을 바꾸면 true. 두 기록이 함께 목표를 넘겨도 참은 한 번이다(A-12, 5.3).
     */
    boolean markAchievedIfReached(long crewId, Week week, LocalDateTime now);

    /** 그 주보다 앞선 주 가운데 목표가 있는 가장 최근 주의 목표. */
    Optional<Distance> latestGoalBefore(long crewId, Week week);

    /** 기록 하나의 반영 거리(어느 크루·주든), 없으면 0. */
    Distance contributionOf(String recordId);

    /** 기록 하나가 반영된 크루(1인 1크루라 하나), 반영이 없으면 빈 값. */
    Optional<Long> contributedCrewOf(String recordId);

    void deleteByCrew(long crewId);

    /**
     * 이 크루에 반영된 회원별 기여 거리 합(전 기간, 피그마 맞춤 W7 크루원 누적 거리). 기여가 없는 회원과 익명화된 기여는 맵에 없다. memberIds가 비면 빈
     * 맵.
     */
    Map<Long, Distance> totalsByMember(long crewId, List<Long> memberIds);

    /**
     * 목표가 있고 주 시작일이 before보다 앞선 주를 최신 주부터 limit개(피그마 맞춤 W7 F-21 지난 목표). 참여 인원은 기여 거리가 0보다 큰 기여자
     * 수다(익명 기여는 한 묶음, 축하 카드와 같은 규칙).
     */
    List<GoalHistoryRow> goalHistory(long crewId, LocalDate before, int limit);

    /** 탈퇴 회원의 기여를 익명화한다(합계는 그대로, FR-021). */
    void anonymizeMember(long memberId);
}
