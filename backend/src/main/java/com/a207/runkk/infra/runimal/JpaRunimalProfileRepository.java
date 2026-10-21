package com.a207.runkk.infra.runimal;

import com.a207.runkk.runimal.RunimalProfile;
import com.a207.runkk.runimal.RunimalProfileRepository;
import com.a207.runkk.runimal.RunimalType;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/** runimal.RunimalProfileRepository의 JPA 구현(설계 5.1.1 infra.runimal). runimal_profile 테이블만 읽고 쓴다. */
@Repository
public class JpaRunimalProfileRepository implements RunimalProfileRepository {

    private final EntityManager entityManager;

    public JpaRunimalProfileRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** 없으면 새로 만든다. 저장은 save가 한다. */
    @Override
    public RunimalProfile findOrCreate(long memberId) {
        RunimalProfile found = entityManager.find(RunimalProfile.class, memberId);
        return found != null ? found : RunimalProfile.create(memberId);
    }

    @Override
    public void save(RunimalProfile profile) {
        if (!entityManager.contains(profile)) {
            entityManager.merge(profile);
        }
    }

    @Override
    public Map<Long, RunimalType> typesOf(Collection<Long> memberIds) {
        List<Object[]> rows =
                entityManager
                        .createQuery(
                                "select p.memberId, p.confirmedType, p.provisionalType from"
                                        + " RunimalProfile p where p.memberId in :ids",
                                Object[].class)
                        .setParameter("ids", List.copyOf(memberIds))
                        .getResultList();
        Map<Long, RunimalType> types = new HashMap<>();
        for (Object[] row : rows) {
            RunimalType type = row[1] != null ? (RunimalType) row[1] : (RunimalType) row[2];
            if (type != null) {
                types.put((Long) row[0], type);
            }
        }
        return types;
    }

    @Override
    public void deleteByMember(long memberId) {
        entityManager
                .createQuery("delete from RunimalProfile p where p.memberId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
