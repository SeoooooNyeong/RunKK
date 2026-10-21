package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.Report;
import com.a207.runkk.analysis.ReportRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** analysis.ReportRepository의 JPA 구현(설계 5.1.1 infra.analysis). report 테이블만 읽고 쓴다. */
@Repository
public class JpaReportRepository implements ReportRepository {

    private final EntityManager entityManager;

    public JpaReportRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<Report> findByMember(long memberId) {
        return Optional.ofNullable(entityManager.find(Report.class, memberId));
    }

    @Override
    public void save(Report report) {
        if (!entityManager.contains(report)) {
            entityManager.persist(report);
        }
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from Report r where r.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
