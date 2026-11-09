package com.a207.runkk.infra.crew;

import com.a207.runkk.crew.CrewRunSeries;
import com.a207.runkk.crew.CrewRunSeriesRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * crew.CrewRunSeriesRepository의 JPA 구현(W6). crew_run_series만 읽고 쓴다. 크루 삭제 때의 행 삭제는
 * JpaCrewPostRepository가 한다.
 */
@Repository
public class JpaCrewRunSeriesRepository implements CrewRunSeriesRepository {

    private final EntityManager entityManager;

    public JpaCrewRunSeriesRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CrewRunSeries save(CrewRunSeries series) {
        if (series.getId() == null) {
            entityManager.persist(series);
            entityManager.flush();
            return series;
        }
        return entityManager.contains(series) ? series : entityManager.merge(series);
    }

    @Override
    public Optional<CrewRunSeries> findById(long id) {
        return Optional.ofNullable(entityManager.find(CrewRunSeries.class, id));
    }

    @Override
    public Optional<CrewRunSeries> findByIdForUpdate(long id) {
        return Optional.ofNullable(
                entityManager.find(CrewRunSeries.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public List<Long> findActiveIdsBefore(LocalDateTime before) {
        return entityManager
                .createQuery(
                        "select s.id from CrewRunSeries s where s.active = true"
                                + " and s.lastScheduledAt < :before order by s.id",
                        Long.class)
                .setParameter("before", before)
                .getResultList();
    }
}
