package com.a207.runkk.infra.battle;

import com.a207.runkk.battle.CelebrityRecord;
import com.a207.runkk.battle.CelebrityRecordRepository;
import com.a207.runkk.common.StandardDistance;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** battle.CelebrityRecordRepository의 JPA 구현(설계 5.1.1 infra.battle). celebrity_record 테이블만 읽고 쓴다. */
@Repository
public class JpaCelebrityRecordRepository implements CelebrityRecordRepository {

    private final EntityManager entityManager;

    public JpaCelebrityRecordRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<CelebrityRecord> findAll(Optional<StandardDistance> filter) {
        if (filter.isEmpty()) {
            return entityManager
                    .createQuery(
                            "select c from CelebrityRecord c order by c.registeredAt desc",
                            CelebrityRecord.class)
                    .getResultList();
        }
        return entityManager
                .createQuery(
                        "select c from CelebrityRecord c where c.distanceMeters = :meters"
                                + " order by c.registeredAt desc",
                        CelebrityRecord.class)
                .setParameter("meters", filter.get().distance().meters())
                .getResultList();
    }

    @Override
    public Optional<CelebrityRecord> findById(long id) {
        return Optional.ofNullable(entityManager.find(CelebrityRecord.class, id));
    }

    @Override
    public CelebrityRecord save(CelebrityRecord record) {
        if (record.getId() == null) {
            entityManager.persist(record);
            return record;
        }
        return entityManager.merge(record);
    }

    /** 원래 값까지 지운다(srs.md 6.3 7.1). 끝난 대결은 대결에 남긴 상대 표시명으로 남는다(FR-172). */
    @Override
    public void delete(long id) {
        entityManager
                .createQuery("delete from CelebrityRecord c where c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
