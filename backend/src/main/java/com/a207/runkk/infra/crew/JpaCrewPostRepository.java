package com.a207.runkk.infra.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.crew.CheerPost;
import com.a207.runkk.crew.CrewPost;
import com.a207.runkk.crew.CrewPostRepository;
import com.a207.runkk.crew.CrewRunPost;
import com.a207.runkk.crew.CrewRunStatus;
import com.a207.runkk.crew.PostKind;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * crew.CrewPostRepository의 JPA 구현(설계 5.1.1 infra.crew). crew_post(단일 테이블
 * 상속)·crew_run_participation만 읽고 쓴다.
 */
@Repository
public class JpaCrewPostRepository implements CrewPostRepository {

    /** 시계가 미리 볼 기간(대기실 개방·알림 전 시각보다 넉넉히). */
    static final int LOOKAHEAD_DAYS = 1;

    private final EntityManager entityManager;

    public JpaCrewPostRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CrewPost save(CrewPost post) {
        if (post.getId() == null) {
            entityManager.persist(post);
            return post;
        }
        return entityManager.contains(post) ? post : entityManager.merge(post);
    }

    @Override
    public Optional<CrewPost> findById(long id) {
        return Optional.ofNullable(entityManager.find(CrewPost.class, id));
    }

    @Override
    public Optional<CrewRunPost> findCrewRunById(long id) {
        return Optional.ofNullable(entityManager.find(CrewRunPost.class, id));
    }

    @Override
    public List<CrewPost> findVisible(long crewId, PostKind kind) {
        Class<? extends CrewPost> type = kind == null ? CrewPost.class : typeOf(kind);
        return entityManager
                .createQuery(
                        "select p from "
                                + type.getSimpleName()
                                + " p where p.crewId = :crew and p.deleted = false"
                                + " order by p.createdAt desc, p.id desc",
                        CrewPost.class)
                .setParameter("crew", crewId)
                .getResultList();
    }

    private static Class<? extends CrewPost> typeOf(PostKind kind) {
        return kind == PostKind.CHEER ? CheerPost.class : CrewRunPost.class;
    }

    @Override
    public List<CrewRunPost> findActiveCrewRuns(LocalDateTime now) {
        return entityManager
                .createQuery(
                        "select p from CrewRunPost p where p.deleted = false and (p.status ="
                            + " :running or (p.status in :waiting and p.scheduledAt <= :horizon))"
                            + " order by p.id",
                        CrewRunPost.class)
                .setParameter("running", CrewRunStatus.RUNNING)
                .setParameter("waiting", List.of(CrewRunStatus.VOTING, CrewRunStatus.LOBBY_OPEN))
                .setParameter("horizon", now.plusDays(LOOKAHEAD_DAYS))
                .getResultList();
    }

    @Override
    public List<CrewRunPost> findCrewRunsOf(long memberId, DateRange range) {
        return entityManager
                .createQuery(
                        "select distinct p from CrewRunPost p join p.participations x"
                                + " where x.memberId = :member and p.scheduledAt >= :from"
                                + " and p.scheduledAt < :to",
                        CrewRunPost.class)
                .setParameter("member", memberId)
                .setParameter("from", range.start().atStartOfDay())
                .setParameter("to", range.end().plusDays(1).atStartOfDay())
                .getResultList();
    }

    @Override
    public List<CrewPost> findByAuthor(long memberId) {
        return entityManager
                .createQuery("select p from CrewPost p where p.authorId = :member", CrewPost.class)
                .setParameter("member", memberId)
                .getResultList();
    }

    @Override
    public void deleteByCrew(long crewId) {
        entityManager
                .createQuery("select p from CrewPost p where p.crewId = :crew", CrewPost.class)
                .setParameter("crew", crewId)
                .getResultList()
                .forEach(entityManager::remove);
    }
}
