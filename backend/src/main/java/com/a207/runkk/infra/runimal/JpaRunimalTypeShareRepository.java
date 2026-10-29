package com.a207.runkk.infra.runimal;

import com.a207.runkk.runimal.RunimalType;
import com.a207.runkk.runimal.RunimalTypeShare;
import com.a207.runkk.runimal.RunimalTypeShareRepository;
import jakarta.persistence.EntityManager;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * runimal.RunimalTypeShareRepository의 JPA 구현(표 runimal_type_share, V30). 확정자 수는 runimal_profile을
 * 유형별로 센다.
 */
@Repository
public class JpaRunimalTypeShareRepository implements RunimalTypeShareRepository {

    private final EntityManager entityManager;

    public JpaRunimalTypeShareRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void replaceAll(List<RunimalTypeShare> shares) {
        // 같은 키(유형)를 다시 넣으므로 관리 중인 행을 지우고 비운 뒤 넣는다(대량 삭제는 영속성 문맥을 지나친다)
        entityManager
                .createQuery("select s from RunimalTypeShare s", RunimalTypeShare.class)
                .getResultList()
                .forEach(entityManager::remove);
        entityManager.flush();
        shares.forEach(entityManager::persist);
    }

    @Override
    public Map<RunimalType, RunimalTypeShare> findAll() {
        Map<RunimalType, RunimalTypeShare> shares = new EnumMap<>(RunimalType.class);
        entityManager
                .createQuery("select s from RunimalTypeShare s", RunimalTypeShare.class)
                .getResultList()
                .forEach(s -> shares.put(s.type(), s));
        return shares;
    }

    @Override
    public Map<RunimalType, Integer> countConfirmedByType() {
        Map<RunimalType, Integer> counts = new EnumMap<>(RunimalType.class);
        entityManager
                .createQuery(
                        "select p.confirmedType, count(p) from RunimalProfile p"
                                + " where p.confirmedType is not null group by p.confirmedType",
                        Object[].class)
                .getResultList()
                .forEach(row -> counts.put((RunimalType) row[0], ((Long) row[1]).intValue()));
        return counts;
    }
}
