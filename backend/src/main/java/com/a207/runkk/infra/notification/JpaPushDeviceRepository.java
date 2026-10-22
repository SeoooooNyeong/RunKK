package com.a207.runkk.infra.notification;

import com.a207.runkk.notification.PushDevice;
import com.a207.runkk.notification.PushDeviceRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * notification.PushDeviceRepository의 JPA 구현(설계 5.1.1 infra.notification, D-43). push_device 테이블만 읽고
 * 쓴다.
 */
@Repository
public class JpaPushDeviceRepository implements PushDeviceRepository {

    private final EntityManager entityManager;

    public JpaPushDeviceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void save(PushDevice device) {
        if (!entityManager.contains(device)) {
            entityManager.merge(device);
        }
    }

    @Override
    public Optional<PushDevice> findByToken(String token) {
        return Optional.ofNullable(entityManager.find(PushDevice.class, token));
    }

    @Override
    public List<String> tokensOf(long memberId) {
        return entityManager
                .createQuery(
                        "select d.token from PushDevice d where d.memberId = :member", String.class)
                .setParameter("member", memberId)
                .getResultList();
    }

    /** 커밋 뒤 푸시 결과로 부르므로 새 트랜잭션에서 지운다(NotificationService.push). */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteTokens(Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return;
        }
        entityManager
                .createQuery("delete from PushDevice d where d.token in :tokens")
                .setParameter("tokens", List.copyOf(tokens))
                .executeUpdate();
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from PushDevice d where d.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
