package com.a207.runkk.infra.crew;

import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.RaceContributionKey;
import com.a207.runkk.crew.WeeklyRace;
import com.a207.runkk.crew.WeeklyRaceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * crew.WeeklyRaceRepository의 JPA 구현(설계 5.1.1 infra.crew, 6.2 A-12). weekly_race·race_contribution만
 * 읽고 쓴다. 주 행이 없으면 먼저 만들고, 기여 행 삽입이 성공했을 때만 합계를 원자적으로 늘린다. 메모리에서 읽고-더하고-쓰지 않는다(5.3, NFR-034).
 */
@Repository
public class JpaWeeklyRaceRepository implements WeeklyRaceRepository {

    private final EntityManager entityManager;

    public JpaWeeklyRaceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public boolean addAtomically(RaceContributionKey key, Distance d) {
        entityManager
                .createNativeQuery(
                        "insert into weekly_race (crew_id, week_start, total_meters)"
                                + " values (:crew, :week, 0) on conflict do nothing")
                .setParameter("crew", key.crewId())
                .setParameter("week", key.week().monday())
                .executeUpdate();
        int inserted =
                entityManager
                        .createNativeQuery(
                                "insert into race_contribution"
                                        + " (crew_id, week_start, member_id, record_id, meters)"
                                        + " values (:crew, :week, :member, :record, :meters)"
                                        + " on conflict do nothing")
                        .setParameter("crew", key.crewId())
                        .setParameter("week", key.week().monday())
                        .setParameter("member", key.memberId())
                        .setParameter("record", key.recordId())
                        .setParameter("meters", d.meters())
                        .executeUpdate();
        if (inserted == 0) {
            return false;
        }
        entityManager
                .createNativeQuery(
                        "update weekly_race set total_meters = total_meters + :meters"
                                + " where crew_id = :crew and week_start = :week")
                .setParameter("meters", d.meters())
                .setParameter("crew", key.crewId())
                .setParameter("week", key.week().monday())
                .executeUpdate();
        return true;
    }

    @Override
    public Optional<WeeklyRace> find(long crewId, Week week) {
        List<WeeklyRaceRows.Contribution> rows =
                entityManager
                        .createQuery(
                                "select c from RaceContributionRow c where c.crewId = :crew"
                                        + " and c.weekStart = :week order by c.id",
                                WeeklyRaceRows.Contribution.class)
                        .setParameter("crew", crewId)
                        .setParameter("week", week.monday())
                        .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        WeeklyRace race = WeeklyRace.empty(crewId, week);
        for (WeeklyRaceRows.Contribution row : rows) {
            Distance meters = Distance.ofMeters(row.meters());
            if (row.memberId() == null) {
                race.addAnonymized(row.recordId(), meters);
            } else {
                race.add(row.memberId(), row.recordId(), meters);
            }
        }
        return Optional.of(race);
    }

    @Override
    public Distance contributionOf(String recordId) {
        Number meters =
                (Number)
                        entityManager
                                .createQuery(
                                        "select coalesce(sum(c.meters), 0) from RaceContributionRow"
                                                + " c where c.recordId = :record")
                                .setParameter("record", recordId)
                                .getSingleResult();
        return Distance.ofMeters(meters.longValue());
    }

    @Override
    public void deleteByCrew(long crewId) {
        entityManager
                .createQuery("delete from RaceContributionRow c where c.crewId = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
        entityManager
                .createQuery("delete from WeeklyRaceRow r where r.crewId = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
    }

    @Override
    public void anonymizeMember(long memberId) {
        entityManager
                .createQuery(
                        "update RaceContributionRow c set c.memberId = null where c.memberId ="
                                + " :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
