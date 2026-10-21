package com.a207.runkk.analysis;

import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.running.RunSummary;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리포트 갱신과 조회(설계 6.1.4 ReportService, SQ-16, FR-064, FR-067). 유효 러닝 저장 사건을 커밋 뒤 비동기로 받아 최근 20회로 요약을
 * 만들고 코멘트 생성을 맡긴다(D-03). 요약이 그대로면(같은 사건 재수신) 코멘트를 다시 만들지 않는다(T-16).
 */
@Service
public class ReportService {

    /** 최근 10회 + 직전 10회(FR-064). */
    static final int SUMMARY_RUNS = FitnessSummary.WINDOW * 2;

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reportRepository;
    private final RunQueryService runQueryService;
    private final ReportCommentService reportCommentService;
    private final AnalysisTransactions transactions;
    private final Clock clock;

    public ReportService(
            ReportRepository reportRepository,
            RunQueryService runQueryService,
            ReportCommentService reportCommentService,
            AnalysisTransactions transactions,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.runQueryService = runQueryService;
        this.reportCommentService = reportCommentService;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** 비동기. 유효 러닝만. recentSummaries(20) → FitnessSummary.from → Report.refresh → 바뀌었으면 코멘트 생성. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        List<RunSummary> runs = runQueryService.recentSummaries(e.memberId(), SUMMARY_RUNS);
        if (runs.isEmpty()) {
            return;
        }
        List<RunSummary> recent = runs.subList(0, Math.min(FitnessSummary.WINDOW, runs.size()));
        List<RunSummary> previous = runs.subList(recent.size(), runs.size());
        FitnessSummary summary =
                FitnessSummary.from(recent, previous)
                        .numbered(Math.max(e.validRunOrdinal(), recent.size()));
        if (refreshOnce(e.memberId(), summary)) {
            reportCommentService.generateComment(e.memberId());
        }
    }

    /** 리포트가 없으면 "유효 러닝 기록 없음". */
    public ReportView report(long memberId) {
        return transactions.read(
                () ->
                        reportRepository
                                .findByMember(memberId)
                                .map(Report::view)
                                .orElseGet(ReportView::none));
    }

    /** 같은 회원 사건이 동시에 와 첫 행 만들기·version이 겹치면 한 번 다시 읽어 맞춘다. */
    private boolean refreshOnce(long memberId, FitnessSummary summary) {
        try {
            return refresh(memberId, summary);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            log.info("report_refresh_retry reason={}", e.getClass().getSimpleName());
            return refresh(memberId, summary);
        }
    }

    private boolean refresh(long memberId, FitnessSummary summary) {
        LocalDateTime now = LocalDateTime.now(clock);
        return transactions.write(
                () -> {
                    Optional<Report> found = reportRepository.findByMember(memberId);
                    if (found.isEmpty()) {
                        reportRepository.save(Report.create(memberId, summary, now));
                        return true;
                    }
                    boolean changed = found.get().refresh(summary, now);
                    reportRepository.save(found.get());
                    // 요약을 바꿨거나, 같은 요약인데 앞선 코멘트 생성이 끊겨 코멘트가 없으면 다시 만든다(리뷰 HIGH).
                    return changed || !found.get().hasComment();
                });
    }
}
