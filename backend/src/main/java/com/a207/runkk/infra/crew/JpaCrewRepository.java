package com.a207.runkk.infra.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Week;
import com.a207.runkk.crew.Crew;
import com.a207.runkk.crew.CrewListRow;
import com.a207.runkk.crew.CrewMembership;
import com.a207.runkk.crew.CrewRepository;
import com.a207.runkk.crew.CrewSearch;
import com.a207.runkk.crew.CrewVisibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * crew.CrewRepository의 JPA 구현(설계 5.1.1 infra.crew). crew·crew_membership·weekly_race만 읽는다(D-32,
 * 6.5). 크루장 표시명은 붙이지 않는다(CrewQueryService가 MemberQueryService.summaries로 붙인다).
 */
@Repository
public class JpaCrewRepository implements CrewRepository {

    private final EntityManager entityManager;

    public JpaCrewRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Crew save(Crew crew) {
        if (crew.getId() == null) {
            entityManager.persist(crew);
            return crew;
        }
        return entityManager.contains(crew) ? crew : entityManager.merge(crew);
    }

    @Override
    public Optional<Crew> findById(long id) {
        return Optional.ofNullable(entityManager.find(Crew.class, id));
    }

    @Override
    public Optional<Crew> findByIdForUpdate(long id) {
        return Optional.ofNullable(
                entityManager.find(Crew.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<Crew> findByInviteCode(String code) {
        return entityManager
                .createQuery("select c from Crew c where c.inviteCode.code = :code", Crew.class)
                .setParameter("code", code.strip().toUpperCase(Locale.ROOT))
                .getResultStream()
                .findFirst();
    }

    @Override
    public boolean existsByName(String name) {
        return count("select count(c) from Crew c where c.name = :value", name) > 0;
    }

    @Override
    public boolean existsByInviteCode(String code) {
        return count("select count(c) from Crew c where c.inviteCode.code = :value", code) > 0;
    }

    private long count(String jpql, String value) {
        return entityManager
                .createQuery(jpql, Long.class)
                .setParameter("value", value)
                .getSingleResult();
    }

    @Override
    public List<CrewMembership> findMembershipHistory(long memberId) {
        return entityManager
                .createQuery(
                        "select m from CrewMembership m where m.memberId = :member order by m.id",
                        CrewMembership.class)
                .setParameter("member", memberId)
                .getResultList();
    }

    /** 검색어를 LIKE 글자 그대로로: escape 문자 !와 %·_ 앞에 !를 붙인다(코드 리뷰 2차 L13). */
    private static String likeLiteral(String keyword) {
        return keyword.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /** 공개 크루, 키워드는 SQL로, 태그 AND는 읽은 뒤 거른다. 이번 주 거리는 weekly_race 한 번으로 붙인다. */
    @Override
    public List<CrewListRow> search(CrewSearch cond, Week week) {
        TypedQuery<Crew> query =
                entityManager.createQuery(
                        "select c from Crew c where c.visibility = :public"
                                + (cond.keyword() == null
                                        ? ""
                                        : " and (lower(c.name) like :keyword escape '!' or"
                                                + " lower(c.intro) like :keyword escape '!')")
                                + " order by c.id desc",
                        Crew.class);
        query.setParameter("public", CrewVisibility.PUBLIC);
        if (cond.keyword() != null) {
            query.setParameter("keyword", "%" + likeLiteral(cond.keyword()) + "%");
        }
        List<Crew> crews =
                query.getResultList().stream()
                        .filter(c -> c.tags().containsAll(cond.tags()))
                        .toList();
        Map<Long, Long> weekly = weeklyTotals(crews.stream().map(Crew::getId).toList(), week);
        return crews.stream()
                .map(
                        c ->
                                new CrewListRow(
                                        c.getId(),
                                        c.name().value(),
                                        c.intro(),
                                        c.tags(),
                                        c.logoUrl(),
                                        c.headcount(),
                                        c.joinPolicy(),
                                        c.leaderId(),
                                        Distance.ofMeters(weekly.getOrDefault(c.getId(), 0L)),
                                        c.createdAt()))
                .toList();
    }

    @Override
    public Distance weeklyDistance(long crewId, Week week) {
        return Distance.ofMeters(weeklyTotals(List.of(crewId), week).getOrDefault(crewId, 0L));
    }

    @Override
    public List<Crew> findAllById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return entityManager
                .createQuery("select c from Crew c where c.id in :ids", Crew.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    private Map<Long, Long> weeklyTotals(List<Long> crewIds, Week week) {
        Map<Long, Long> totals = new HashMap<>();
        if (crewIds.isEmpty()) {
            return totals;
        }
        entityManager
                .createQuery(
                        "select r.crewId, r.totalMeters from WeeklyRaceRow r where r.weekStart ="
                                + " :week and r.crewId in :crews",
                        Object[].class)
                .setParameter("week", week.monday())
                .setParameter("crews", crewIds)
                .getResultList()
                .forEach(row -> totals.put((Long) row[0], (Long) row[1]));
        return totals;
    }

    @Override
    public List<Long> crewIdsActiveBetween(DateRange range) {
        return entityManager
                .createQuery(
                        "select distinct m.crew.id from CrewMembership m where m.joinedAt < :to"
                                + " and (m.leftAt is null or m.leftAt >= :from)",
                        Long.class)
                .setParameter("from", range.start().atStartOfDay())
                .setParameter("to", range.end().plusDays(1).atStartOfDay())
                .getResultList();
    }

    @Override
    public List<CrewMembership> findActiveMemberships() {
        return entityManager
                .createQuery(
                        "select m from CrewMembership m where m.leftAt is null",
                        CrewMembership.class)
                .getResultList();
    }

    @Override
    public void delete(long crewId) {
        Crew crew = entityManager.find(Crew.class, crewId);
        if (crew != null) {
            entityManager.remove(crew);
            entityManager.flush();
        }
    }
}
