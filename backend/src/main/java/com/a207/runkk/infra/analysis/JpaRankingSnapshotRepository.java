package com.a207.runkk.infra.analysis;

import com.a207.runkk.analysis.RankingKind;
import com.a207.runkk.analysis.RankingSnapshot;
import com.a207.runkk.analysis.RankingSnapshotRepository;
import com.a207.runkk.common.AggregationPeriod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * analysis.RankingSnapshotRepository의 구현(설계 5.1.1 infra.analysis, D-25). 스냅숏은
 * ranking_snapshot·ranking_entry에 저장하고 같은 종류·기간의 이전 스냅숏은 지운다. 최근 스냅숏은 RankingCache(Redis)에 두고, 캐시가
 * 비면 DB에서 읽어 다시 채운다. 캐시는 커밋 뒤에 쓴다.
 */
@Repository
public class JpaRankingSnapshotRepository implements RankingSnapshotRepository {

    private final EntityManager entityManager;
    private final RankingCache cache;

    public JpaRankingSnapshotRepository(EntityManager entityManager, RankingCache cache) {
        this.entityManager = entityManager;
        this.cache = cache;
    }

    @Override
    public void save(RankingSnapshot snapshot) {
        List<RankingSnapshot> previous = findAll(snapshot.getKind(), snapshot.getPeriod());
        previous.forEach(entityManager::remove);
        entityManager.persist(snapshot);
        afterCommit(() -> cache.put(snapshot));
    }

    @Override
    public Optional<RankingSnapshot> latest(RankingKind kind, AggregationPeriod period) {
        Optional<RankingSnapshot> cached = cache.get(kind, period);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<RankingSnapshot> stored =
                query(kind, period).setMaxResults(1).getResultList().stream().findFirst();
        stored.ifPresent(cache::putIfAbsent);
        return stored;
    }

    private List<RankingSnapshot> findAll(RankingKind kind, AggregationPeriod period) {
        return query(kind, period).getResultList();
    }

    private TypedQuery<RankingSnapshot> query(RankingKind kind, AggregationPeriod period) {
        return entityManager
                .createQuery(
                        "select s from RankingSnapshot s where s.kind = :kind and s.period ="
                                + " :period order by s.computedAt desc",
                        RankingSnapshot.class)
                .setParameter("kind", kind)
                .setParameter("period", period);
    }

    private static void afterCommit(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        work.run();
                    }
                });
    }
}
