package com.a207.runkk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.a207.runkk.bootstrap.RunkkApplication;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.CrewTag;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.crew.CrewCreateCommand;
import com.a207.runkk.crew.CrewPostService;
import com.a207.runkk.crew.CrewService;
import com.a207.runkk.crew.CrewVisibility;
import com.a207.runkk.crew.JoinPolicy;
import com.a207.runkk.crew.JoinTarget;
import com.a207.runkk.crew.MembershipService;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberRepository;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.report.ContentReport;
import com.a207.runkk.report.ReportCase;
import com.a207.runkk.report.ReportCaseRepository;
import com.a207.runkk.report.ReportCommand;
import com.a207.runkk.report.ReportReason;
import com.a207.runkk.report.ReportResolution;
import com.a207.runkk.report.ReportResult;
import com.a207.runkk.report.ReportService;
import com.a207.runkk.report.ReportStatus;
import com.a207.runkk.report.ReportTargetKind;
import com.a207.runkk.report.ReportWithdrawalListener;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 3장 통합 T-28 — S3c 신고 스레드(실 PostgreSQL, Flyway V24 + ddl validate): 서로 다른 신고자 셋·다섯의 동시 신고(TC-6716·
 * TC-6717), 사례가 없는 대상의 동시 첫 신고(TC-6608), 같은 신고자의 동시 재신고(TC-6609), 운영자 둘의 동시 숨김·기각(TC-6725), 고유 색인
 * 위반 변환(D-149), 신고자 탈퇴 익명화(TC-6735). 5.3 신고 자동 숨김 행, 6.2 A-24 잠금 순서.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = RunkkApplication.class)
@Import(TestcontainersConfig.class)
@ActiveProfiles("integration")
@DisplayName("신고 스레드 통합(S3c, PostgreSQL)")
class ReportThreadIntegrationTest {

    private static final int ROUNDS = 20;
    private static final AtomicInteger NAMES = new AtomicInteger();

    @Autowired CrewService crews;
    @Autowired MembershipService membership;
    @Autowired CrewPostService posts;
    @Autowired ReportService reports;
    @Autowired ReportCaseRepository reportCases;
    @Autowired ReportWithdrawalListener withdrawal;
    @Autowired MemberRepository members;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private long member() {
        String name = "신고러너" + NAMES.incrementAndGet();
        return tx.execute(
                s ->
                        members.save(MemberFixtures.member(name, LocalDateTime.now().minusDays(30)))
                                .getId());
    }

    /** [0] 크루 ID, [1] 크루장(글쓴이), 나머지 크루원. */
    private long[] crew(int memberCount) {
        long leader = member();
        long crewId =
                crews.create(
                                leader,
                                new CrewCreateCommand(
                                        "신고크루" + NAMES.incrementAndGet(),
                                        null,
                                        Set.of(CrewTag.WEEKDAY_EVENING),
                                        CrewVisibility.PUBLIC,
                                        JoinPolicy.FREE,
                                        null))
                        .crewId();
        long[] ids = new long[memberCount + 2];
        ids[0] = crewId;
        ids[1] = leader;
        for (int i = 0; i < memberCount; i++) {
            ids[i + 2] = member();
            membership.apply(ids[i + 2], JoinTarget.byCrew(crewId));
        }
        return ids;
    }

    private long cheer(long[] crew) {
        return posts.writeCheer(crew[1], crew[0], "신고될 응원").postId();
    }

    private static ReportCommand cheer(long postId) {
        return new ReportCommand(ReportTargetKind.CHEER, postId, ReportReason.ABUSE, null);
    }

    private static <T> List<Object> together(List<Callable<T>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> w : work) {
            futures.add(
                    pool.submit(
                            () -> {
                                start.await();
                                return w.call();
                            }));
        }
        start.countDown();
        pool.shutdown();
        List<Object> results = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                results.add(f.get());
            } catch (ExecutionException e) {
                results.add(e.getCause());
            }
        }
        return results;
    }

    private static long statusCount(List<Object> results, ReportStatus status) {
        return results.stream()
                .filter(r -> r instanceof ReportResult rr && rr.status() == status)
                .count();
    }

    private static long rejected(List<Object> results, ReasonCode reason) {
        return results.stream()
                .filter(r -> r instanceof BusinessRuleException e && e.reason() == reason)
                .count();
    }

    private List<Callable<ReportResult>> reporters(long[] crew, long postId, int n) {
        List<Callable<ReportResult>> work = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long reporter = crew[i + 2];
            work.add(() -> reports.submit(reporter, cheer(postId)));
        }
        return work;
    }

    private Integer intOf(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private boolean hidden(long postId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "select hidden from crew_post where id = ?", Boolean.class, postId));
    }

    @Test
    @DisplayName(
            "TC-6716: 서로 다른 신고자 셋이 동시에 신고하면 매회 사례 1행 AUTO_HIDDEN·열린 신고자 3, 신고 3행, 글 숨김, AUTO_HIDDEN"
                    + " 응답 정확히 1개")
    void threeConcurrentReporters_autoHideOnce() throws Exception {
        long[] crew = crew(3);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            List<Object> results = together(reporters(crew, post, 3));
            assertThat(results).allMatch(ReportResult.class::isInstance);
            assertThat(statusCount(results, ReportStatus.AUTO_HIDDEN)).isEqualTo(1);
            assertThat(statusCount(results, ReportStatus.RECEIVED)).isEqualTo(2);
            assertThat(
                            intOf(
                                    "select count(*) from report_case where target_kind = 'CHEER'"
                                            + " and target_id = ? and status = 'AUTO_HIDDEN'"
                                            + " and open_reporter_count = 3",
                                    post))
                    .isEqualTo(1);
            assertThat(
                            intOf(
                                    "select count(*) from content_report r join report_case c"
                                            + " on r.case_id = c.id where c.target_id = ?"
                                            + " and c.target_kind = 'CHEER'",
                                    post))
                    .isEqualTo(3);
            assertThat(hidden(post)).isTrue();
        }
    }

    @Test
    @DisplayName(
            "TC-6717: 다섯이 동시에 신고해도 자동 숨김 전이는 한 번, 사례가 숨김이 된 뒤 잠금을 얻은 신고는"
                    + " REPORT_TARGET_NOT_FOUND라 열린 신고자 3, 500 없음")
    void fiveConcurrentReporters_transitionOnce() throws Exception {
        long[] crew = crew(5);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            List<Object> results = together(reporters(crew, post, 5));
            assertThat(statusCount(results, ReportStatus.RECEIVED)).isEqualTo(2);
            assertThat(statusCount(results, ReportStatus.AUTO_HIDDEN)).isEqualTo(1);
            assertThat(rejected(results, ReasonCode.REPORT_TARGET_NOT_FOUND))
                    .as("그 밖의 예외(500·잠금 충돌) 0")
                    .isEqualTo(2);
            ReportCase c =
                    tx.execute(
                            s ->
                                    reportCases
                                            .findByTargetForUpdate(ReportTargetKind.CHEER, post)
                                            .orElseThrow());
            assertThat(c.getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);
            assertThat(c.getOpenReporterCount()).isEqualTo(3);
            assertThat(hidden(post)).isTrue();
        }
    }

    @Test
    @DisplayName(
            "리뷰 H1: 신고와 운영자 숨김이 동시에 와도 HIDDEN 사례에 닫히지 않는 열린 신고가 남지 않는다"
                    + "(늦은 신고는 REPORT_TARGET_NOT_FOUND)")
    void submitRacingOperatorHide_leavesNoOpenReport() throws Exception {
        long[] crew = crew(2);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            reports.submit(crew[2], cheer(post));
            long caseId =
                    tx.execute(
                            s ->
                                    reportCases
                                            .findByTargetForUpdate(ReportTargetKind.CHEER, post)
                                            .orElseThrow()
                                            .getId());
            List<Object> results =
                    together(
                            List.of(
                                    () -> reports.submit(crew[3], cheer(post)),
                                    () -> reports.hide("KAKAO:op-1", caseId)));
            assertThat(results.get(1)).isInstanceOf(ReportResolution.class);
            assertThat(
                            results.get(0) instanceof ReportResult
                                    || (results.get(0) instanceof BusinessRuleException e
                                            && e.reason() == ReasonCode.REPORT_TARGET_NOT_FOUND))
                    .as("늦은 신고는 거부, 먼저 잠근 신고는 접수되고 숨김 처리 때 닫힌다")
                    .isTrue();
            assertThat(
                            intOf(
                                    "select count(*) from content_report where case_id = ?"
                                            + " and resolved = false",
                                    caseId))
                    .isZero();
            assertThat(intOf("select open_reporter_count from report_case where id = ?", caseId))
                    .isZero();
            assertThat(hidden(post)).isTrue();
        }
    }

    @Test
    @DisplayName("TC-6608: 사례가 없는 대상에 둘이 동시에 첫 신고하면 사례 1행·신고 2행·열린 신고자 2(고유 제약 충돌 쪽도 다시 읽어 성공)")
    void concurrentFirstReports_createOneCase() throws Exception {
        long[] crew = crew(2);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            List<Object> results = together(reporters(crew, post, 2));
            assertThat(results).allMatch(ReportResult.class::isInstance);
            assertThat(
                            intOf(
                                    "select count(*) from report_case where target_kind = 'CHEER'"
                                            + " and target_id = ? and open_reporter_count = 2",
                                    post))
                    .isEqualTo(1);
            assertThat(hidden(post)).isFalse();
        }
    }

    @Test
    @DisplayName("TC-6609: 같은 신고자가 같은 대상을 동시에 두 번 신고하면 신고 1행, 다른 요청은 ALREADY_REPORTED(500 아님)")
    void sameReporterTwice_oneRow() throws Exception {
        long[] crew = crew(1);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            long reporter = crew[2];
            List<Object> results =
                    together(
                            List.of(
                                    () -> reports.submit(reporter, cheer(post)),
                                    () -> reports.submit(reporter, cheer(post))));
            assertThat(results.stream().filter(ReportResult.class::isInstance).count())
                    .isEqualTo(1);
            assertThat(rejected(results, ReasonCode.ALREADY_REPORTED)).isEqualTo(1);
            assertThat(
                            intOf(
                                    "select count(*) from content_report r join report_case c"
                                            + " on r.case_id = c.id where c.target_id = ?"
                                            + " and c.target_kind = 'CHEER'",
                                    post))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName(
            "D-149: saveReport는 uq_content_report_reporter 위반만 ALREADY_REPORTED로 바꾸고 다른 무결성 위반(내용"
                    + " 201자)은 그대로 둔다")
    void saveReport_translatesOnlyReporterUniqueViolation() {
        long[] crew = crew(1);
        long post = cheer(crew);
        long reporter = crew[2];
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            ReportCase c = lockedCase(post, crew[0]);
                                            reportCases.saveReport(
                                                    new ContentReport(
                                                            c.getId(),
                                                            reporter,
                                                            new ContentReport.Content(
                                                                    ReportReason.SPAM, null),
                                                            LocalDateTime.now()));
                                            reportCases.saveReport(
                                                    new ContentReport(
                                                            c.getId(),
                                                            reporter,
                                                            new ContentReport.Content(
                                                                    ReportReason.ABUSE, null),
                                                            LocalDateTime.now()));
                                        }))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.ALREADY_REPORTED));
        Throwable tooLong =
                org.junit.jupiter.api.Assertions.assertThrows(
                        RuntimeException.class,
                        () ->
                                tx.executeWithoutResult(
                                        s -> {
                                            ReportCase c = lockedCase(post, crew[0]);
                                            reportCases.saveReport(
                                                    new ContentReport(
                                                            c.getId(),
                                                            reporter,
                                                            new ContentReport.Content(
                                                                    ReportReason.OTHER,
                                                                    "가".repeat(201)),
                                                            LocalDateTime.now()));
                                        }));
        assertThat(tooLong).isNotInstanceOf(BusinessRuleException.class);
    }

    private ReportCase lockedCase(long post, long crewId) {
        reportCases.saveCase(
                ReportCase.open(ReportTargetKind.CHEER, post, crewId, LocalDateTime.now()));
        return reportCases.findByTargetForUpdate(ReportTargetKind.CHEER, post).orElseThrow();
    }

    @Test
    @DisplayName(
            "TC-6725: 운영자 둘이 같은 AUTO_HIDDEN 사례에 숨김과 기각을 동시에 하면 한쪽만 성공, 숨김 상태가 이긴 결정과 같고 감사 1건·알림 셋")
    void concurrentHideAndDismiss_oneWins() throws Exception {
        long[] crew = crew(3);
        for (int round = 0; round < ROUNDS; round++) {
            long post = cheer(crew);
            together(reporters(crew, post, 3));
            long caseId =
                    tx.execute(
                            s ->
                                    reportCases
                                            .findByTargetForUpdate(ReportTargetKind.CHEER, post)
                                            .orElseThrow()
                                            .getId());
            List<Object> results =
                    together(
                            List.of(
                                    () -> reports.hide("KAKAO:op-1", caseId),
                                    () -> reports.dismiss("KAKAO:op-2", caseId)));
            assertThat(rejected(results, ReasonCode.REPORT_DECIDED)).isEqualTo(1);
            ReportStatus winner =
                    results.stream()
                            .filter(ReportResolution.class::isInstance)
                            .map(r -> ((ReportResolution) r).status())
                            .findFirst()
                            .orElseThrow();
            assertThat(hidden(post)).isEqualTo(winner == ReportStatus.HIDDEN);
            assertThat(
                            intOf(
                                    "select count(*) from audit_record where target_id = ?"
                                            + " and action in ('REPORT_HIDE', 'REPORT_DISMISS')",
                                    String.valueOf(caseId)))
                    .isEqualTo(1);
            assertThat(
                            intOf(
                                    "select count(*) from content_report where case_id = ?"
                                            + " and resolved = false",
                                    caseId))
                    .isZero();
        }
        assertThat(
                        intOf(
                                "select count(*) from notification where type = 'REPORT_RESOLVED'"
                                        + " and target_id = ?",
                                String.valueOf(crew[0])))
                .isEqualTo(ROUNDS * 3);
    }

    @Test
    @DisplayName("TC-6735: 신고자가 탈퇴하면 신고자만 비고 사례 상태·열린 신고자 수는 그대로, 처리 알림은 남은 신고자에게만")
    void withdrawnReporter_isAnonymized() throws Exception {
        long[] crew = crew(3);
        long post = cheer(crew);
        for (int i = 0; i < 3; i++) {
            reports.submit(crew[i + 2], cheer(post));
        }
        withdrawal.onMemberWithdrawn(new MemberWithdrawn(crew[2], LocalDateTime.now()));
        assertThat(intOf("select count(*) from content_report where reporter_id = ?", crew[2]))
                .isZero();
        ReportCase c =
                tx.execute(
                        s ->
                                reportCases
                                        .findByTargetForUpdate(ReportTargetKind.CHEER, post)
                                        .orElseThrow());
        assertThat(c.getStatus()).isEqualTo(ReportStatus.AUTO_HIDDEN);
        assertThat(c.getOpenReporterCount()).isEqualTo(3);
        reports.hide("KAKAO:op-1", c.getId());
        assertThat(
                        intOf(
                                "select count(*) from notification where type = 'REPORT_RESOLVED'"
                                        + " and recipient_id in (?, ?, ?)",
                                crew[2],
                                crew[3],
                                crew[4]))
                .isEqualTo(2);
        assertThat(
                        intOf(
                                "select count(*) from notification where type = 'REPORT_RESOLVED'"
                                        + " and recipient_id = ?",
                                crew[2]))
                .isZero();
    }
}
