package com.a207.runkk.infra.audit;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditRecord;
import com.a207.runkk.audit.AuditRepository;
import com.a207.runkk.common.DateRange;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * audit.AuditRepository의 JPA 구현(설계 5.1.1 infra.audit, 추가 전용). audit_record 테이블만 읽고 쓴다(D-32). 수정 연산이
 * 없고, 테이블의 UPDATE는 마이그레이션의 트리거가 막는다.
 */
@Repository
public class JpaAuditRepository implements AuditRepository {

    private final EntityManager entityManager;

    public JpaAuditRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(AuditRecord record) {
        entityManager.persist(record);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<AuditRecord> find(DateRange range, Optional<AuditAction> action) {
        String sql =
                "select * from audit_record where recorded_at >= :from and recorded_at < :to"
                        + (action.isPresent() ? " and action = :action" : "")
                        + " order by recorded_at desc, id desc";
        Query query =
                entityManager
                        .createNativeQuery(sql, AuditRecord.class)
                        .setParameter("from", range.start().atStartOfDay())
                        .setParameter("to", range.end().plusDays(1).atStartOfDay());
        action.ifPresent(a -> query.setParameter("action", a.name()));
        return query.getResultList();
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff) {
        return entityManager
                .createNativeQuery("delete from audit_record where recorded_at < :cutoff")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
