package com.a207.runkk.infra.policy;

import com.a207.runkk.policy.OperationSetting;
import com.a207.runkk.policy.OperationSettingHistory;
import com.a207.runkk.policy.OperationSettingRepository;
import com.a207.runkk.policy.SettingKey;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * policy.OperationSettingRepository의 JPA 구현(설계 5.1.1 infra.policy).
 * operation_setting·operation_setting_history 테이블만 읽고 쓴다(D-32).
 */
@Repository
public class JpaOperationSettingRepository implements OperationSettingRepository {

    private final EntityManager entityManager;

    public JpaOperationSettingRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<OperationSetting> find(SettingKey key) {
        return Optional.ofNullable(entityManager.find(OperationSetting.class, key));
    }

    /** 새 항목(version null)은 저장하고, 있던 항목은 version 낙관적 잠금으로 합친다(5.3). */
    @Override
    public OperationSetting save(OperationSetting setting) {
        return entityManager.merge(setting);
    }

    @Override
    public void saveHistory(OperationSettingHistory history) {
        entityManager.persist(history);
    }

    @Override
    public List<OperationSetting> findAll() {
        return entityManager
                .createQuery("select s from OperationSetting s", OperationSetting.class)
                .getResultList();
    }
}
