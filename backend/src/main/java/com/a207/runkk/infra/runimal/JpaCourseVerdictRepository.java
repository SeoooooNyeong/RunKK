package com.a207.runkk.infra.runimal;

import com.a207.runkk.runimal.CourseVerdict;
import com.a207.runkk.runimal.CourseVerdictRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** runimal.CourseVerdictRepository의 JPA 구현(설계 5.1.1 infra.runimal). course_verdict 테이블만 읽고 쓴다. */
@Repository
public class JpaCourseVerdictRepository implements CourseVerdictRepository {

    private final EntityManager entityManager;

    public JpaCourseVerdictRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 같은 기록을 다시 판정하면 덮어쓴다. */
    @Override
    public void save(CourseVerdict verdict) {
        entityManager.merge(verdict);
    }

    @Override
    public Map<String, Boolean> findByRecords(Collection<String> recordIds) {
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select v.recordId, v.sameCourse from CourseVerdict v"
                                        + " where v.recordId in :ids",
                                Object[].class)
                        .setParameter("ids", List.copyOf(recordIds))
                        .getResultList();
        Map<String, Boolean> verdicts = new HashMap<>();
        rows.forEach(row -> verdicts.put((String) row[0], (Boolean) row[1]));
        return verdicts;
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from CourseVerdict v where v.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
