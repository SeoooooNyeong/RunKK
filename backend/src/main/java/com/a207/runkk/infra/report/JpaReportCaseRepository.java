package com.a207.runkk.infra.report;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.report.ContentReport;
import com.a207.runkk.report.ReportCase;
import com.a207.runkk.report.ReportCaseRepository;
import com.a207.runkk.report.ReportReason;
import com.a207.runkk.report.ReportStatus;
import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportedPeriod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * report.ReportCaseRepository의 JPA 구현(설계 6.1.13 infra.report, 12.2 N16, D-104, D-149).
 * report_case·content_report 표만 읽고 쓰고 crew 표를 모른다. 사례 잠금은 SELECT … FOR UPDATE, 새 사례는 INSERT … ON
 * CONFLICT DO NOTHING이라 같은 대상의 첫 신고가 동시에 와도 트랜잭션이 깨지지 않는다(6.2 A-24).
 */
@Repository
public class JpaReportCaseRepository implements ReportCaseRepository {

    /** 같은 사례·같은 신고자 고유 색인(V24). 위반은 ALREADY_REPORTED다(D-149). */
    static final String REPORTER_UNIQUE = "uq_content_report_reporter";

    private static final String UNIQUE_VIOLATION = "23505";

    private final EntityManager entityManager;

    public JpaReportCaseRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Optional<ReportCase> findByTargetForUpdate(ReportTargetKind kind, long targetId) {
        return entityManager
                .createQuery(
                        "select c from ReportCase c where c.targetKind = :kind"
                                + " and c.targetId = :target",
                        ReportCase.class)
                .setParameter("kind", kind)
                .setParameter("target", targetId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public ReportCase saveCase(ReportCase c) {
        if (c.getId() == null) {
            entityManager
                    .createNativeQuery(
                            "insert into report_case (target_kind, target_id, crew_id, status,"
                                    + " open_reporter_count, opened_at, version)"
                                    + " values (:kind, :target, :crew, :status, :count, :opened, 0)"
                                    + " on conflict do nothing")
                    .setParameter("kind", c.getTargetKind().name())
                    .setParameter("target", c.getTargetId())
                    .setParameter("crew", c.getCrewId())
                    .setParameter("status", c.getStatus().name())
                    .setParameter("count", c.getOpenReporterCount())
                    .setParameter("opened", c.getOpenedAt())
                    .executeUpdate();
            return c;
        }
        return entityManager.contains(c) ? c : entityManager.merge(c);
    }

    @Override
    public Optional<ReportCase> findByIdForUpdate(long caseId) {
        return Optional.ofNullable(
                entityManager.find(ReportCase.class, caseId, LockModeType.PESSIMISTIC_WRITE));
    }

    /** (status, opened_at) 색인. 커서는 앞 쪽 마지막 사례의 (openedAt, id) 다음부터다(키셋, OFFSET 없음). */
    @Override
    public List<ReportCase> findByStatus(Collection<ReportStatus> statuses, PageCursor page) {
        ReportCase after = page.cursor() == null ? null : cursorCase(page.cursor());
        TypedQuery<ReportCase> query =
                entityManager
                        .createQuery(
                                "select c from ReportCase c where c.status in :statuses"
                                        + (after == null
                                                ? ""
                                                : " and (c.openedAt > :at"
                                                        + " or (c.openedAt = :at and c.id > :id))")
                                        + " order by c.openedAt asc, c.id asc",
                                ReportCase.class)
                        .setParameter("statuses", List.copyOf(statuses));
        if (after != null) {
            query.setParameter("at", after.getOpenedAt()).setParameter("id", after.getId());
        }
        if (page.size() != null) {
            query.setMaxResults(page.size() + 1);
        }
        return query.getResultList();
    }

    /** 커서 사례. 사례는 지우지 않으므로 없는 ID는 잘못된 커서다(400). 처음부터 다시 주면 앱이 같은 쪽을 되풀이해 읽는다. */
    private ReportCase cursorCase(long caseId) {
        ReportCase found = entityManager.find(ReportCase.class, caseId);
        if (found == null) {
            throw new IllegalArgumentException("없는 신고 사례 커서: " + caseId);
        }
        return found;
    }

    /**
     * 바로 넣는다(IDENTITY + flush). uq_content_report_reporter 위반만 ALREADY_REPORTED로 바꾼다(D-149). 위반 뒤
     * 트랜잭션은 이미 깨졌으므로(PostgreSQL aborted) 호출자는 이 예외를 잡아 계속하지 않고 그대로 올려 롤백한다.
     */
    @Override
    public ContentReport saveReport(ContentReport r) {
        try {
            entityManager.persist(r);
            entityManager.flush();
            return r;
        } catch (PersistenceException | DataIntegrityViolationException e) {
            if (isReporterUniqueViolation(e)) {
                throw new BusinessRuleException(ReasonCode.ALREADY_REPORTED);
            }
            throw e;
        }
    }

    /** 원인 사슬에서 uq_content_report_reporter 고유 위반(SQLSTATE 23505)을 찾는다. 다른 제약 위반은 거짓. */
    static boolean isReporterUniqueViolation(Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .anyMatch(JpaReportCaseRepository::isReporterUnique);
    }

    private static boolean isReporterUnique(Throwable cause) {
        boolean hibernate =
                cause instanceof org.hibernate.exception.ConstraintViolationException c
                        && mentionsReporterUnique(c.getConstraintName());
        boolean sql =
                cause instanceof SQLException s
                        && UNIQUE_VIOLATION.equals(s.getSQLState())
                        && mentionsReporterUnique(s.getMessage());
        return hibernate || sql;
    }

    private static boolean mentionsReporterUnique(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(REPORTER_UNIQUE);
    }

    @Override
    public boolean existsReport(long caseId, long reporterId) {
        return !entityManager
                .createQuery(
                        "select r.id from ContentReport r where r.caseId = :case"
                                + " and r.reporterId = :reporter",
                        Long.class)
                .setParameter("case", caseId)
                .setParameter("reporter", reporterId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public List<Long> openReporterIds(long caseId) {
        return entityManager
                .createQuery(
                        "select r.reporterId from ContentReport r where r.caseId = :case"
                                + " and r.resolved = false and r.reporterId is not null"
                                + " order by r.id",
                        Long.class)
                .setParameter("case", caseId)
                .getResultList();
    }

    @Override
    public Map<ReportReason, Integer> reasonCounts(long caseId) {
        Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
        entityManager
                .createQuery(
                        "select r.reason, count(r) from ContentReport r where r.caseId = :case"
                                + " group by r.reason",
                        Object[].class)
                .setParameter("case", caseId)
                .getResultList()
                .forEach(row -> counts.put((ReportReason) row[0], ((Number) row[1]).intValue()));
        return counts;
    }

    @Override
    public Optional<ReportedPeriod> reportedPeriod(long caseId) {
        Object[] row =
                entityManager
                        .createQuery(
                                "select min(r.reportedAt), max(r.reportedAt) from ContentReport r"
                                        + " where r.caseId = :case",
                                Object[].class)
                        .setParameter("case", caseId)
                        .getSingleResult();
        if (row[0] == null) {
            return Optional.empty();
        }
        return Optional.of(new ReportedPeriod((LocalDateTime) row[0], (LocalDateTime) row[1]));
    }

    @Override
    public void resolveOpenReports(long caseId) {
        entityManager
                .createQuery(
                        "update ContentReport r set r.resolved = true where r.caseId = :case"
                                + " and r.resolved = false")
                .setParameter("case", caseId)
                .executeUpdate();
    }

    @Override
    public void anonymizeReporter(long memberId) {
        entityManager
                .createQuery(
                        "update ContentReport r set r.reporterId = null"
                                + " where r.reporterId = :member")
                .setParameter("member", memberId)
                .executeUpdate();
    }
}
