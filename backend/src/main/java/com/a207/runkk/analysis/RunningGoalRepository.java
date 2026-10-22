package com.a207.runkk.analysis;

import java.time.LocalDate;
import java.util.List;

/**
 * RunningGoal 저장 포트(설계 6.1.4). 구현 infra.analysis.JpaRunningGoalRepository. analysis 밖에서 쓰지 않는다(13.1
 * 규칙 4).
 */
public interface RunningGoalRepository {

    /** 없으면 새로 만든다. 저장은 save가 한다. */
    RunningGoal findOrCreate(long memberId);

    void save(RunningGoal goal);

    /** 그날 계획이 있는 회원(20시 계획 미이행 알림, FR-075). */
    List<Long> membersWithPlanOn(LocalDate day);

    void deleteByMember(long memberId);

    /** before보다 이전 날짜의 계획과 이전 주의 주간 목표 행을 지우고 지운 행 수를 돌려준다(보존 기간 정리). */
    int deletePlansAndGoalsBefore(LocalDate before);
}
