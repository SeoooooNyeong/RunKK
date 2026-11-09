package com.a207.runkk.infra.crew;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.crew.BoardPost;
import com.a207.runkk.crew.CheerPost;
import com.a207.runkk.crew.CrewPost;
import com.a207.runkk.crew.CrewPostRepository;
import com.a207.runkk.crew.CrewRunPost;
import com.a207.runkk.crew.CrewRunStatus;
import com.a207.runkk.crew.MeetupPost;
import com.a207.runkk.crew.NoticePost;
import com.a207.runkk.crew.PollPost;
import com.a207.runkk.crew.PostKind;
import com.a207.runkk.crew.PostPhoto;
import com.a207.runkk.crew.PostQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * crew.CrewPostRepository의 JPA 구현(설계 5.1.1 infra.crew). crew_post(단일 테이블 상속)와 딸린
 * crew_run_participation·crew_meetup_attendance·crew_poll_option·crew_poll_response·crew_photo만 읽고
 * 쓴다.
 */
@Repository
public class JpaCrewPostRepository implements CrewPostRepository {

    /** 시계가 미리 볼 기간(대기실 개방·알림 전 시각보다 넉넉히). */
    static final int LOOKAHEAD_DAYS = 1;

    /** 크루 글 ID 부분 질의(딸린 행 삭제용). */
    private static final String POSTS_OF_CREW = "(select id from crew_post where crew_id = :crew)";

    private final EntityManager entityManager;

    public JpaCrewPostRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CrewPost save(CrewPost post) {
        if (post.getId() == null) {
            entityManager.persist(post);
            entityManager.flush();
            return post;
        }
        return entityManager.contains(post) ? post : entityManager.merge(post);
    }

    @Override
    public Optional<CrewPost> findById(long id) {
        return Optional.ofNullable(entityManager.find(CrewPost.class, id));
    }

    /**
     * 이미 영속성 문맥에 있는 글이면 떼어 낸 뒤 잠가 다시 읽어 다른 트랜잭션이 커밋한 상태(딸린 참석·응답 포함)를 본다. refresh는 딸린 컬렉션을 잠그기 전에
     * 먼저 읽어 낡은 정원 판정을 하므로 쓰지 않는다(5.3, 리뷰 반영).
     */
    @Override
    public Optional<CrewPost> findByIdForUpdate(long id) {
        CrewPost cached = entityManager.find(CrewPost.class, id);
        if (cached == null) {
            return Optional.empty();
        }
        entityManager.detach(cached);
        return Optional.ofNullable(
                entityManager.find(CrewPost.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public Optional<CrewRunPost> findCrewRunById(long id) {
        return Optional.ofNullable(entityManager.find(CrewRunPost.class, id));
    }

    @Override
    public Optional<CrewRunPost> findCrewRunByIdForUpdate(long id) {
        return Optional.ofNullable(
                entityManager.find(CrewRunPost.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public List<CrewPost> findVisible(long crewId, PostQuery query) {
        PageCursor page = query.page();
        String entity = query.pinnedOnly() ? "NoticePost" : "CrewPost";
        StringBuilder jpql =
                new StringBuilder("select p from ")
                        .append(entity)
                        .append(
                                " p where p.crewId = :crew and p.deleted = false and p.hidden ="
                                        + " false");
        if (query.pinnedOnly()) {
            jpql.append(" and p.pinned = true");
        } else if (!query.kinds().isEmpty()) {
            jpql.append(" and type(p) in (")
                    .append(
                            query.kinds().stream()
                                    .map(k -> typeOf(k).getSimpleName())
                                    .sorted()
                                    .collect(Collectors.joining(", ")))
                    .append(")");
        }
        if (page.cursor() != null) {
            jpql.append(" and p.id < :cursor");
        }
        jpql.append(" order by p.id desc");
        TypedQuery<CrewPost> q =
                entityManager
                        .createQuery(jpql.toString(), CrewPost.class)
                        .setParameter("crew", crewId);
        if (page.cursor() != null) {
            q.setParameter("cursor", page.cursor());
        }
        if (page.size() != null) {
            q.setMaxResults(page.size() + 1);
        }
        return q.getResultList();
    }

    private static Class<? extends CrewPost> typeOf(PostKind kind) {
        return switch (kind) {
            case CHEER -> CheerPost.class;
            case CREW_RUN -> CrewRunPost.class;
            case MEETUP -> MeetupPost.class;
            case NOTICE -> NoticePost.class;
            case POLL -> PollPost.class;
            case BOARD -> BoardPost.class;
        };
    }

    @Override
    public int countPinnedNotices(long crewId) {
        return entityManager
                .createQuery(
                        "select count(p) from NoticePost p where p.crewId = :crew and p.pinned ="
                                + " true and p.deleted = false and p.hidden = false",
                        Long.class)
                .setParameter("crew", crewId)
                .getSingleResult()
                .intValue();
    }

    @Override
    public List<PollPost> findClosingPolls(LocalDateTime from, LocalDateTime to) {
        return entityManager
                .createQuery(
                        "select p from PollPost p where p.deleted = false and p.hidden = false"
                                + " and p.reminded = false and p.closesAt >= :from"
                                + " and p.closesAt <= :to order by p.id",
                        PollPost.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
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
    public List<CrewRunPost> findActiveCrewRunsOf(long crewId) {
        return entityManager
                .createQuery(
                        "select p from CrewRunPost p where p.crewId = :crew and p.deleted = false"
                                + " and p.status in :active order by p.id",
                        CrewRunPost.class)
                .setParameter("crew", crewId)
                .setParameter("active", List.of(CrewRunStatus.LOBBY_OPEN, CrewRunStatus.RUNNING))
                .getResultList();
    }

    @Override
    public int countFinishedCrewRuns(long crewId) {
        return entityManager
                .createQuery(
                        "select count(p) from CrewRunPost p where p.crewId = :crew"
                                + " and p.deleted = false and p.status = :finished",
                        Long.class)
                .setParameter("crew", crewId)
                .setParameter("finished", CrewRunStatus.FINISHED)
                .getSingleResult()
                .intValue();
    }

    /** V50 색인(crew_id, scheduled_at DESC, 마친 크루런만)을 탄다. */
    @Override
    public List<CrewRunPost> findRecentFinishedCrewRuns(long crewId, int limit) {
        return entityManager
                .createQuery(
                        "select p from CrewRunPost p where p.crewId = :crew and p.deleted = false"
                            + " and p.status = :finished order by p.scheduledAt desc, p.id desc",
                        CrewRunPost.class)
                .setParameter("crew", crewId)
                .setParameter("finished", CrewRunStatus.FINISHED)
                .setMaxResults(limit)
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
    public Optional<PostPhoto> findPhoto(long photoId) {
        return Optional.ofNullable(entityManager.find(PostPhoto.class, photoId));
    }

    @Override
    public List<PostPhoto> findAlbum(long crewId, PageCursor page) {
        TypedQuery<PostPhoto> q =
                entityManager
                        .createQuery(
                                "select ph from PostPhoto ph, BoardPost p where ph.postId = p.id"
                                        + " and p.crewId = :crew and p.deleted = false"
                                        + " and p.hidden = false"
                                        + (page.cursor() == null ? "" : " and ph.id < :cursor")
                                        + " order by ph.id desc",
                                PostPhoto.class)
                        .setParameter("crew", crewId);
        if (page.cursor() != null) {
            q.setParameter("cursor", page.cursor());
        }
        if (page.size() != null) {
            q.setMaxResults(page.size() + 1);
        }
        return q.getResultList();
    }

    /** 앞으로 열려 있는 응답 표와 그 글을 고르는 조건(표 이름과 조건은 항상 짝이다). */
    private enum OpenResponse {
        RUN("crew_run_participation", "post_kind = 'CREW_RUN' and scheduled_at > :now"),
        MEETUP("crew_meetup_attendance", "post_kind = 'MEETUP' and scheduled_at > :now"),
        POLL("crew_poll_response", "post_kind = 'POLL' and poll_closes_at > :now");

        private final String table;
        private final String postCondition;

        OpenResponse(String table, String postCondition) {
            this.table = table;
            this.postCondition = postCondition;
        }
    }

    @Override
    public int deleteOpenResponses(long crewId, long memberId, LocalDateTime now) {
        int deleted = 0;
        for (OpenResponse kind : OpenResponse.values()) {
            deleted += deleteMemberRows(kind, crewId, memberId, now);
        }
        return deleted;
    }

    private int deleteMemberRows(OpenResponse kind, long crewId, long memberId, LocalDateTime now) {
        return entityManager
                .createNativeQuery(
                        "delete from "
                                + kind.table
                                + " where member_id = :member and post_id in (select id from"
                                + " crew_post where crew_id = :crew and deleted = false and "
                                + kind.postCondition
                                + ")")
                .setParameter("member", memberId)
                .setParameter("crew", crewId)
                .setParameter("now", now)
                .executeUpdate();
    }

    @Override
    public int deleteResponsesByMember(long memberId) {
        int deleted = 0;
        for (String table : List.of("crew_poll_response", "crew_meetup_attendance")) {
            deleted +=
                    entityManager
                            .createNativeQuery(
                                    "delete from " + table + " where member_id = :member")
                            .setParameter("member", memberId)
                            .executeUpdate();
        }
        return deleted;
    }

    /** 딸린 행을 FK 순서대로 먼저 지우고 글을 지운다(DB의 ON DELETE CASCADE에 기대지 않는다). */
    @Override
    public void deleteByCrew(long crewId) {
        entityManager.flush();
        for (String table :
                List.of(
                        "crew_poll_response",
                        "crew_poll_option",
                        "crew_meetup_attendance",
                        "crew_photo",
                        "crew_run_participation")) {
            entityManager
                    .createNativeQuery(
                            "delete from " + table + " where post_id in " + POSTS_OF_CREW)
                    .setParameter("crew", crewId)
                    .executeUpdate();
        }
        entityManager
                .createNativeQuery("delete from crew_post where crew_id = :crew")
                .setParameter("crew", crewId)
                .executeUpdate();
    }
}
