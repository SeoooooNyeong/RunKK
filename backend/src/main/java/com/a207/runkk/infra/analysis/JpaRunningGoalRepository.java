package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.RunningGoal;
import com.a207.runkk.analysis.RunningGoalRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * analysis.RunningGoalRepository의 JPA 구현(설계 5.1.1 infra.analysis).
 * running_goal·weekly_goal·daily_plan 테이블만 읽고 쓴다.
 */
@Repository
public class JpaRunningGoalRepository implements RunningGoalRepository {

    private final EntityManager entityManager;

    public JpaRunningGoalRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public RunningGoal findOrCreate(long memberId) {
        RunningGoal found = entityManager.find(RunningGoal.class, memberId);
        return found != null ? found : RunningGoal.create(memberId);
    }

    @Override
    public void save(RunningGoal goal) {
        if (!entityManager.contains(goal)) {
            entityManager.merge(goal);
        }
    }

    /** daily_plan(plan_date) 인덱스로 그날 계획이 있는 회원을 한 번에 읽는다. */
    @Override
    public List<Long> membersWithPlanOn(LocalDate day) {
        return entityManager
                .createQuery(
                        "select distinct g.memberId from RunningGoal g join g.plans p"
                                + " where key(p) = :day",
                        Long.class)
                .setParameter("day", day)
                .getResultList();
    }

    /** 컬렉션 표(daily_plan·weekly_goal)라 JPQL로 지울 수 없어 SQL 두 문장으로 지운다(plan_date 인덱스). */
    @Override
    public int deletePlansAndGoalsBefore(LocalDate before) {
        int plans =
                entityManager
                        .createNativeQuery("delete from daily_plan where plan_date < :before")
                        .setParameter("before", before)
                        .executeUpdate();
        int goals =
                entityManager
                        .createNativeQuery("delete from weekly_goal where week_start < :before")
                        .setParameter("before", before)
                        .executeUpdate();
        entityManager.clear();
        return plans + goals;
    }

    @Override
    public void deleteByMember(long memberId) {
        RunningGoal goal = entityManager.find(RunningGoal.class, memberId);
        if (goal != null) {
            entityManager.remove(goal);
        }
    }
}
