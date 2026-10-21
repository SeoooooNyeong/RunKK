package com.a207.runkk.analysis;

import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.OperationPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 리포트 코멘트를 만들어 붙인다(설계 6.1.4 ReportCommentService, srs.md 6.3 3.4.2). CommentContext(LLM 동의, AI 코멘트
 * 사용)를 만들어 FallbackCommentGenerator에 넘기고 결과를 Report.attachComment로 붙인다. ai 호출(최대 3초) 동안 트랜잭션을 잡지
 * 않는다.
 */
@Service
public class ReportCommentService {

    private static final Logger log = LoggerFactory.getLogger(ReportCommentService.class);

    private final ReportRepository reportRepository;
    private final CommentGenerator commentGenerator;
    private final MemberQueryService memberQueryService;
    private final OperationPolicy policy;
    private final AnalysisTransactions transactions;
    private final Clock clock;

    public ReportCommentService(
            ReportRepository reportRepository,
            CommentGenerator commentGenerator,
            MemberQueryService memberQueryService,
            OperationPolicy policy,
            AnalysisTransactions transactions,
            Clock clock) {
        this.reportRepository = reportRepository;
        this.commentGenerator = commentGenerator;
        this.memberQueryService = memberQueryService;
        this.policy = policy;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** 리포트가 없으면 아무것도 하지 않는다. 그 사이 요약이 바뀌었으면 늦은 코멘트는 버린다. */
    public void generateComment(long memberId) {
        Optional<FitnessSummary> summary =
                transactions.read(
                        () -> reportRepository.findByMember(memberId).map(Report::summary));
        if (summary.isEmpty()) {
            return;
        }
        CommentContext context =
                new CommentContext(
                        memberQueryService.preferenceOf(memberId).llmAgreed(),
                        policy.features().aiCommentEnabled(),
                        memberId,
                        LocalDate.now(clock));
        commentGenerator
                .generate(summary.get(), context)
                .ifPresent(comment -> attach(memberId, comment));
    }

    private void attach(long memberId, Comment comment) {
        try {
            transactions.write(
                    () -> {
                        reportRepository
                                .findByMember(memberId)
                                .filter(report -> report.attachComment(comment))
                                .ifPresent(reportRepository::save);
                        return null;
                    });
        } catch (OptimisticLockingFailureException e) {
            log.info("report_comment_dropped reason=concurrent_refresh");
        }
    }
}
