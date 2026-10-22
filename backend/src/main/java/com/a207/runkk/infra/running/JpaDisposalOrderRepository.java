package com.a207.runkk.infra.running;

import com.a207.runkk.running.DisposalOrder;
import com.a207.runkk.running.DisposalOrderRepository;
import com.a207.runkk.running.DisposalStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** running.DisposalOrderRepository의 JPA 구현(설계 5.1.1 infra.running). disposal_order 테이블만 읽고 쓴다. */
@Repository
public class JpaDisposalOrderRepository implements DisposalOrderRepository {

    private final EntityManager entityManager;

    public JpaDisposalOrderRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(DisposalOrder order) {
        if (!entityManager.contains(order)) {
            entityManager.persist(order);
        }
    }

    @Override
    public List<DisposalOrder> findScheduled() {
        return entityManager
                .createQuery(
                        "select o from DisposalOrder o where o.status = :status order by o.id",
                        DisposalOrder.class)
                .setParameter("status", DisposalStatus.SCHEDULED)
                .getResultList();
    }

    @Override
    public List<DisposalOrder> findNotConfirmedByMember(long memberId) {
        return entityManager
                .createQuery(
                        "select o from DisposalOrder o where o.memberId = :m"
                                + " and o.deviceConfirmedAt is null order by o.id",
                        DisposalOrder.class)
                .setParameter("m", memberId)
                .getResultList();
    }

    @Override
    public Optional<DisposalOrder> findById(long id) {
        return Optional.ofNullable(entityManager.find(DisposalOrder.class, id));
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from DisposalOrder o where o.memberId = :m")
                .setParameter("m", memberId)
                .executeUpdate();
    }
}
