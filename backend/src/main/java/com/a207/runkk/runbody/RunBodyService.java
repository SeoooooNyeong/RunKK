package com.a207.runkk.runbody;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.BirthYearSet;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunRecordDeleted;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 런바디 분석 작업을 만들고 진행시키고 결과를 내준다(설계 05-19 2.2·S-31, D-441~D-445·D-450). 러닝 저장 트랜잭션 안에서 작업(PENDING)을
 * 남기고, 커밋 뒤 비동기로 ai에 제출한다. pollDue(5초)가 제출 대기·분석 중 작업을 진행시킨다. 제출 실패는 작업 상태로만 남기고 러닝 저장을 실패시키지 않는다.
 * 결과는 서버에 저장하지 않고 조회 때 ai에서 읽는다(D-442). 운영 기준값 RUNBODY_ENABLED가 꺼져 있으면 작업을 만들지 않고 제출·조회도 하지
 * 않는다(D-440).
 */
@Service
public class RunBodyService {

    /** pollDue 한 번에 다룰 작업 수. */
    static final int POLL_BATCH = 50;

    /** 러닝 선택 목록 한 쪽의 최대 줄 수(common.PageCursor.MAX_SIZE와 같다). */
    public static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(RunBodyService.class);

    private final RunBodyJobRepository jobs;
    private final RunBodyGateway gateway;
    private final RunBodyRequestFactory requests;
    private final OperationPolicy policy;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public RunBodyService(
            RunBodyJobRepository jobs,
            RunBodyGateway gateway,
            RunBodyRequestFactory requests,
            OperationPolicy policy,
            Clock clock,
            PlatformTransactionManager manager) {
        this.jobs = jobs;
        this.gateway = gateway;
        this.requests = requests;
        this.policy = policy;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 러닝 저장 트랜잭션 안(커밋 직전)에서 작업을 PENDING으로 남긴다. 커밋 뒤 제출이 유실되어도 pollDue가 잇는다. 집계 제외 기록(valid false,
     * FR-225)은 만들지 않는다(D-445). 운영 기준값을 읽지 못하는 등의 오류는 기록만 하고 러닝 저장을 막지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRunRecordSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        try {
            if (!enabled() || jobs.find(e.recordId()).isPresent()) {
                return;
            }
            jobs.save(RunBodyJob.pending(e.recordId(), e.memberId(), now(), e.startedAt()));
        } catch (RuntimeException error) {
            log.error("runbody_prepare_failed error={}", error.getClass().getSimpleName());
        }
    }

    /** 커밋 뒤 비동기로 제출한다. 요청 스레드(러닝 업로드 응답)를 ai 응답에 묶지 않는다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void submitAfterCommit(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        try {
            Optional<RunBodyJob> job = read(e.recordId());
            if (job.isPresent() && job.get().status() == RunBodyStatus.PENDING && enabled()) {
                submit(job.get().recordId(), job.get().memberId());
            }
        } catch (RuntimeException error) {
            log.error("runbody_submit_failed error={}", error.getClass().getSimpleName());
        }
    }

    /**
     * 회원이 출생연도를 넣으면 출생연도가 없어 실패로 닫은 그 회원의 작업을 PENDING으로 되돌린다. 다음 pollDue(5초)가 제출한다(MR !91 e2e 1).
     * 오류는 기록만 하고 프로필 편집을 막지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBirthYearSet(BirthYearSet e) {
        try {
            List<RunBodyJob> stuck =
                    transactions.execute(
                            tx -> jobs.failedFor(e.memberId(), RunBodyJob.BIRTH_YEAR_MISSING));
            for (RunBodyJob job : stuck) {
                update(job.recordId(), j -> j.retryAfterBirthYear(now()));
            }
            if (!stuck.isEmpty()) {
                log.info("runbody_birth_year_retry count={}", stuck.size());
            }
        } catch (RuntimeException error) {
            log.error("runbody_birth_year_retry_failed error={}", error.getClass().getSimpleName());
        }
    }

    /** 집계 제외 기록을 지우면(FR-225) 그 작업도 지운다. 대개 작업이 없다(집계 제외 기록은 제출하지 않음). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunRecordDeleted(RunRecordDeleted e) {
        try {
            onRunRecordRemoved(e.recordId());
        } catch (RuntimeException error) {
            log.error("runbody_job_delete_failed error={}", error.getClass().getSimpleName());
        }
    }

    public void onRunRecordRemoved(String recordId) {
        transactions.executeWithoutResult(tx -> jobs.delete(recordId));
    }

    /**
     * 다음 조회 시각이 지난 작업을 진행시킨다(D-443, 5초 일정). PENDING은 제출, ANALYZING은 상태 조회. 한 작업의 실패가 다음 작업을 막지 않는다.
     */
    public void pollDue() {
        if (!enabled()) {
            return;
        }
        LocalDateTime now = now();
        List<String> due = transactions.execute(tx -> jobs.dueRecordIds(now, POLL_BATCH));
        for (String recordId : due) {
            try {
                advance(recordId);
            } catch (RuntimeException error) {
                log.error("runbody_poll_failed error={}", error.getClass().getSimpleName());
            }
        }
    }

    private void advance(String recordId) {
        Optional<RunBodyJob> found = read(recordId);
        if (found.isEmpty() || !found.get().duePoll(now())) {
            return;
        }
        RunBodyJob job = found.get();
        if (job.status() == RunBodyStatus.PENDING) {
            submit(job.recordId(), job.memberId());
        } else {
            poll(job.recordId());
        }
    }

    private void submit(String recordId, long memberId) {
        try {
            gateway.submit(requests.create(recordId, memberId));
            update(recordId, job -> job.submitted(now()));
            log.info("runbody_submitted");
        } catch (RunBodyRejectedException rejected) {
            log.warn("runbody_rejected reason={}", rejected.getMessage());
            update(recordId, job -> job.rejected(rejected.getMessage()));
        } catch (RunBodyUnavailableException unavailable) {
            log.warn(
                    "runbody_submit_unavailable reason={} connection={}",
                    unavailable.getMessage(),
                    unavailable.connectionFailed());
            update(recordId, job -> retryLater(job, unavailable));
        }
    }

    private void poll(String recordId) {
        RunBodyStatus status;
        try {
            status = gateway.statusOf(recordId);
        } catch (RunBodyUnavailableException unavailable) {
            log.warn(
                    "runbody_status_unavailable reason={} connection={}",
                    unavailable.getMessage(),
                    unavailable.connectionFailed());
            update(recordId, job -> retryLater(job, unavailable));
            return;
        }
        update(
                recordId,
                job -> {
                    switch (status) {
                        case DONE -> job.done();
                        case FAILED -> job.failed(now());
                        case PENDING, ANALYZING -> job.stillAnalyzing(now());
                    }
                });
        if (status == RunBodyStatus.FAILED) {
            log.warn("runbody_analysis_failed");
        }
        if (status == RunBodyStatus.DONE) {
            recordScore(recordId);
        }
    }

    /** 분석이 끝난 작업에 러닝 점수를 남긴다(G-30). 결과 읽기가 실패해도 작업은 DONE 그대로이고, 점수는 결과를 처음 조회할 때(sessionOf) 채운다. */
    private void recordScore(String recordId) {
        try {
            storeScore(recordId, gateway.sessionOf(recordId));
        } catch (RuntimeException error) {
            log.warn("runbody_score_unavailable error={}", error.getClass().getSimpleName());
        }
    }

    /** DONE 결과이고 본인 결과이며 점수가 있을 때만 남긴다. */
    private void storeScore(String recordId, RunBodySession session) {
        if (session.status() != RunBodyStatus.DONE || session.score() == null) {
            return;
        }
        update(
                recordId,
                job -> {
                    if (job.status() == RunBodyStatus.DONE
                            && Long.toString(job.memberId()).equals(session.userId())) {
                        job.scored(session.score());
                    }
                });
    }

    private void retryLater(RunBodyJob job, RunBodyUnavailableException unavailable) {
        if (unavailable.connectionFailed()) {
            job.unreachable(now());
        } else {
            job.failed(now());
        }
    }

    /**
     * 기록 하나의 분석 상태와 결과(FR-266). 작업이 DONE이면 ai에서 결과를 읽는다(D-442). ai가 아직 분석 중이면 ANALYZING, 분석 실패·세션
     * 없음이면 FAILED로 내려 준다(앱 계약 1.2).
     *
     * @throws BusinessRuleException FEATURE_CLOSED(기능 닫힘), RUNBODY_UNAVAILABLE(ai 읽기 실패)
     * @throws NotFoundRuleException RUNBODY_NOT_FOUND(작업 없음·남의 기록)
     */
    public RunBodySessionView sessionOf(long memberId, String recordId) {
        requireEnabled();
        RunBodyJob job =
                read(recordId)
                        .filter(j -> j.ownedBy(memberId))
                        .orElseThrow(() -> new NotFoundRuleException(ReasonCode.RUNBODY_NOT_FOUND));
        if (job.status() != RunBodyStatus.DONE) {
            return new RunBodySessionView(recordId, job.status(), null, job.failureReason());
        }
        RunBodySession session;
        try {
            session = gateway.sessionOf(recordId);
        } catch (RunBodyUnavailableException unavailable) {
            log.warn("runbody_session_unavailable reason={}", unavailable.getMessage());
            throw new BusinessRuleException(ReasonCode.RUNBODY_UNAVAILABLE);
        }
        if (session.status() != RunBodyStatus.DONE) {
            return new RunBodySessionView(recordId, session.status(), null);
        }
        if (!Long.toString(memberId).equals(session.userId())) {
            // 같은 세션 ID를 다른 회원이 먼저 맡긴 경우(ai 409 SESSION_CONFLICT와 같은 경우). 남의 결과를 내주지 않는다
            log.error("runbody_session_owner_mismatch");
            return new RunBodySessionView(recordId, RunBodyStatus.FAILED, null);
        }
        if (job.score() == null && session.score() != null) {
            storeScore(recordId, session);
        }
        return new RunBodySessionView(recordId, RunBodyStatus.DONE, session.body());
    }

    /**
     * 러닝 선택 목록(G-30)의 분석 상태와 러닝 점수, 러닝 시작 최신순. 점수는 작업에 남긴 값만 쓰고 ai를 부르지 않는다. 변화는 이 회원의 시작 순서로 직전인
     * 점수 있는 러닝 대비다(MR !91 e2e 3: 올린 순서가 아니다).
     *
     * @param cursor 직전 쪽의 nextCursor(첫 쪽이면 null)
     * @param size 1~MAX_PAGE_SIZE
     * @throws BusinessRuleException FEATURE_CLOSED(기능 닫힘)
     * @throws NotFoundRuleException RUNBODY_NOT_FOUND(커서가 본인 작업이 아님·지워짐)
     */
    public RunBodySessionPage sessionsOf(long memberId, String cursor, int size) {
        requireEnabled();
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("쪽 크기는 1~" + MAX_PAGE_SIZE + ": " + size);
        }
        RunBodyJob after =
                cursor == null
                        ? null
                        : read(cursor)
                                .filter(j -> j.ownedBy(memberId))
                                .orElseThrow(
                                        () ->
                                                new NotFoundRuleException(
                                                        ReasonCode.RUNBODY_NOT_FOUND));
        List<RunBodyJob> rows =
                transactions.execute(tx -> jobs.latestOf(memberId, after, size + 1));
        boolean hasMore = rows.size() > size;
        List<RunBodyJob> page = hasMore ? rows.subList(0, size) : rows;
        if (page.isEmpty()) {
            return new RunBodySessionPage(List.of(), null);
        }
        Integer older =
                page.stream().anyMatch(j -> j.score() != null)
                        ? transactions
                                .execute(tx -> jobs.previousScore(page.get(page.size() - 1)))
                                .orElse(null)
                        : null;
        RunBodySessionItem[] items = new RunBodySessionItem[page.size()];
        for (int i = page.size() - 1; i >= 0; i--) {
            RunBodyJob job = page.get(i);
            Integer score = job.status() == RunBodyStatus.DONE ? job.score() : null;
            items[i] =
                    new RunBodySessionItem(
                            job.recordId(), job.status(), score, RunBodyScore.change(score, older));
            if (score != null) {
                older = score;
            }
        }
        return new RunBodySessionPage(
                List.of(items), hasMore ? page.get(page.size() - 1).recordId() : null);
    }

    /**
     * 월간 집계(FR-267). 러닝이 없는 달도 ai가 본문(sessionCount 0)을 준다.
     *
     * @throws BusinessRuleException FEATURE_CLOSED(기능 닫힘), RUNBODY_UNAVAILABLE(ai 읽기 실패)
     */
    public RunBodyMonthView monthOf(long memberId, YearMonth month) {
        requireEnabled();
        RunBodyMonth found;
        try {
            found = gateway.monthOf(memberId, month);
        } catch (RunBodyUnavailableException unavailable) {
            log.warn("runbody_month_unavailable reason={}", unavailable.getMessage());
            throw new BusinessRuleException(ReasonCode.RUNBODY_UNAVAILABLE);
        }
        if (!Long.toString(memberId).equals(found.userId())) {
            log.error("runbody_month_owner_mismatch");
            throw new BusinessRuleException(ReasonCode.RUNBODY_UNAVAILABLE);
        }
        return new RunBodyMonthView(found.body());
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new BusinessRuleException(ReasonCode.FEATURE_CLOSED);
        }
    }

    private boolean enabled() {
        return policy.features().runbodyEnabled();
    }

    private Optional<RunBodyJob> read(String recordId) {
        return transactions.execute(tx -> jobs.find(recordId));
    }

    /**
     * 작업 하나를 새 트랜잭션에서 바꾼다. 그 사이 지워졌으면(탈퇴·기록 삭제) 넘어가고, 동시에 바뀌었으면(낙관적 잠금, 이미 다른 상태) 다음 pollDue에 맡긴다.
     */
    private void update(String recordId, Consumer<RunBodyJob> change) {
        try {
            transactions.executeWithoutResult(
                    tx ->
                            jobs.find(recordId)
                                    .ifPresent(
                                            job -> {
                                                change.accept(job);
                                                jobs.save(job);
                                            }));
        } catch (OptimisticLockingFailureException | IllegalStateException concurrent) {
            log.info(
                    "runbody_job_concurrent_update type={}", concurrent.getClass().getSimpleName());
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
