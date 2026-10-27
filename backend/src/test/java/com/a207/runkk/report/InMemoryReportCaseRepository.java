package com.a207.runkk.report;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PageCursor;
import com.a207.runkk.common.ReasonCode;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** 시험용 인메모리 ReportCaseRepository(설계 6.1.12a). infra.report 구현과 같은 약속을 지킨다. */
public class InMemoryReportCaseRepository implements ReportCaseRepository {

    final Map<Long, ReportCase> cases = new LinkedHashMap<>();
    final Map<Long, ContentReport> reports = new LinkedHashMap<>();
    private long caseSequence;
    private long reportSequence;

    private static final Comparator<ReportCase> OLDEST_FIRST =
            Comparator.comparing(ReportCase::getOpenedAt).thenComparing(ReportCase::getId);

    @Override
    public Optional<ReportCase> findByTargetForUpdate(ReportTargetKind kind, long targetId) {
        return cases.values().stream()
                .filter(c -> c.getTargetKind() == kind && c.getTargetId() == targetId)
                .findFirst();
    }

    /** 새 사례는 같은 대상이 없을 때만 넣는다(INSERT … ON CONFLICT DO NOTHING과 같다). */
    @Override
    public ReportCase saveCase(ReportCase c) {
        if (c.getId() == null) {
            if (findByTargetForUpdate(c.getTargetKind(), c.getTargetId()).isPresent()) {
                return c;
            }
            setId(c, ++caseSequence);
        }
        cases.put(c.getId(), c);
        return c;
    }

    @Override
    public Optional<ReportCase> findByIdForUpdate(long caseId) {
        return Optional.ofNullable(cases.get(caseId));
    }

    @Override
    public List<ReportCase> findByStatus(Collection<ReportStatus> statuses, PageCursor page) {
        Stream<ReportCase> rows =
                cases.values().stream()
                        .filter(c -> statuses.contains(c.getStatus()))
                        .sorted(OLDEST_FIRST);
        if (page.cursor() != null) {
            ReportCase after = cases.get(page.cursor());
            if (after == null) {
                throw new IllegalArgumentException("없는 신고 사례 커서: " + page.cursor());
            }
            rows = rows.filter(c -> OLDEST_FIRST.compare(c, after) > 0);
        }
        if (page.size() != null) {
            rows = rows.limit(page.size() + 1L);
        }
        return rows.toList();
    }

    @Override
    public ContentReport saveReport(ContentReport r) {
        boolean duplicate =
                r.getReporterId() != null
                        && reports.values().stream()
                                .anyMatch(
                                        o ->
                                                o.getCaseId() == r.getCaseId()
                                                        && Objects.equals(
                                                                o.getReporterId(),
                                                                r.getReporterId()));
        if (duplicate) {
            throw new BusinessRuleException(ReasonCode.ALREADY_REPORTED);
        }
        setId(r, ++reportSequence);
        reports.put(r.getId(), r);
        return r;
    }

    private Stream<ContentReport> of(long caseId) {
        return reports.values().stream().filter(r -> r.getCaseId() == caseId);
    }

    @Override
    public boolean existsReport(long caseId, long reporterId) {
        return of(caseId).anyMatch(r -> Objects.equals(r.getReporterId(), reporterId));
    }

    @Override
    public List<Long> openReporterIds(long caseId) {
        return of(caseId)
                .filter(r -> !r.isResolved() && r.getReporterId() != null)
                .map(ContentReport::getReporterId)
                .toList();
    }

    @Override
    public Map<ReportReason, Integer> reasonCounts(long caseId) {
        Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
        of(caseId).forEach(r -> counts.merge(r.getReason(), 1, Integer::sum));
        return counts;
    }

    @Override
    public Optional<ReportedPeriod> reportedPeriod(long caseId) {
        List<LocalDateTime> times = of(caseId).map(ContentReport::getReportedAt).sorted().toList();
        if (times.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReportedPeriod(times.get(0), times.get(times.size() - 1)));
    }

    @Override
    public void resolveOpenReports(long caseId) {
        of(caseId).forEach(r -> setField(r, "resolved", true));
    }

    @Override
    public void anonymizeReporter(long memberId) {
        reports.values().stream()
                .filter(r -> Objects.equals(r.getReporterId(), memberId))
                .forEach(ContentReport::anonymizeReporter);
    }

    List<ContentReport> reportsOf(long caseId) {
        return of(caseId).toList();
    }

    private static void setId(Object entity, long id) {
        setField(entity, "id", id);
    }

    /** infra.report가 JPQL 한 문장으로 바꾸는 칼럼을 시험 저장소는 반사로 바꾼다(엔티티에 setter를 두지 않는다). */
    private static void setField(Object entity, String name, Object value) {
        try {
            Field f = entity.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
