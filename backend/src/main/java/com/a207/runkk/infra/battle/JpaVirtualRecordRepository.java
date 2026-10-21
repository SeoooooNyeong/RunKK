package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.VirtualRecord;
import com.a207.runkk.battle.VirtualRecordRepository;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * battle.VirtualRecordRepository의 JPA 구현(설계 5.1.1 infra.battle, D16). virtual_record 테이블만 읽고 쓴다.
 */
@Repository
public class JpaVirtualRecordRepository implements VirtualRecordRepository {

    private final EntityManager entityManager;

    public JpaVirtualRecordRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<VirtualRecord> findByTierAndDistance(Tier tier, StandardDistance distance) {
        return entityManager
                .createQuery(
                        "select v from VirtualRecord v where v.tier = :tier and v.distance ="
                                + " :distance order by v.finishMillis",
                        VirtualRecord.class)
                .setParameter("tier", tier)
                .setParameter("distance", distance)
                .getResultList();
    }

    @Override
    public long count() {
        return entityManager
                .createQuery("select count(v) from VirtualRecord v", Long.class)
                .getSingleResult();
    }

    /** 일괄 삭제를 먼저 실행해 새 닉네임이 옛 닉네임과 겹쳐도 고유 제약에 걸리지 않게 한다(한 트랜잭션). */
    @Override
    public void replaceAll(List<VirtualRecord> records) {
        entityManager.createQuery("delete from VirtualRecord v").executeUpdate();
        records.forEach(entityManager::persist);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return entityManager
                        .createQuery(
                                "select count(v) from VirtualRecord v where v.nickname = :name",
                                Long.class)
                        .setParameter("name", nickname)
                        .getSingleResult()
                > 0;
    }
}
