package com.a207.runkk.running;

import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.RunScoreInput;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * v0.10 유효 판정 변경(FR-047)에 따른 기존 무효 기록의 일회성 재판정(사용자 결정). V28이 배포 시점의 무효 기록에 PENDING 표시를 세우고, 기동 뒤
 * batch.running.RunRejudgeJob이 이 서비스를 한 번 부른다.
 *
 * <p>1단계: PENDING 기록을 하나씩 새 트랜잭션에서 행 잠금으로 읽어 RunRecord.rejudgeInvalid로 다시 판정한다. 경로가 없으면(파기·미수집) 판정할
 * 수 없어 SKIPPED. 유효가 되면 최고 기록 장부를 갱신하고(신기록 표시는 붙이지 않는다: 그때 알리지 않은 소식이다) VALID, 무효 그대로면 DONE(초과 구간을 뺀
 * 거리·시간은 남긴다). 2단계: VALID 기록마다 새 트랜잭션에서 티어에 늦은 반영(TierApplier, 반영 표시가 이중 반영을 막는다)을 하고 DONE으로 바꾼 뒤
 * RunRecordRejudged를 발행한다(커밋 뒤 리워드 적립·배경 해금, 알림 없음). 탈퇴·비활성 회원은 부수 처리 없이 DONE.
 *
 * <p>표시가 바뀐 행은 다시 고르지 않으므로 몇 번 돌려도 결과가 같고(멱등), 중간에 멈추거나 한 번 실행의 상한(Budget, D-199)에 닿으면 다음 기동이 남은
 * PENDING·VALID부터 잇는다. 한 기록의 실패는 로그만 남기고 그 기록은 표시를 그대로 둬 다음 기동에 다시 한다. 이미 끝난 대결·크루런 판정, 지난 주 크루
 * 레이스, Runimal, 리포트는 바꾸지 않는다. 이번 주·지난 주 크루 레이스와 랭킹·대시보드·달력은 기록을 읽어 다시 세므로 따로 할 일이 없다.
 */
@Service
public class RunRejudgeService {

    private static final Logger log = LoggerFactory.getLogger(RunRejudgeService.class);

    /** 한 번에 고르는 기록 수. 기록마다 트랜잭션은 따로다. */
    static final int BATCH = 100;

    private final RunRecordRepository runRecordRepository;
    private final PersonalRecordBookRepository personalRecordBookRepository;
    private final TierApplier tierApplier;
    private final MemberQueryService memberQueryService;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate transaction;

    RunRejudgeService(
            RunRecordRepository runRecordRepository,
            PersonalRecordBookRepository personalRecordBookRepository,
            TierApplier tierApplier,
            MemberQueryService memberQueryService,
            DomainEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.runRecordRepository = runRecordRepository;
        this.personalRecordBookRepository = personalRecordBookRepository;
        this.tierApplier = tierApplier;
        this.memberQueryService = memberQueryService;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 재판정 결과 수.
     *
     * @param nowValid 유효로 바뀐 기록
     * @param stillInvalid 무효 그대로인 기록
     * @param skipped 경로가 없어 판정하지 못한 기록
     * @param applied 부수 처리 단계를 마친 기록
     * @param failed 실패해 다음 기동으로 넘긴 기록
     */
    public record Result(int nowValid, int stillInvalid, int skipped, int applied, int failed) {}

    private enum Outcome {
        VALID,
        INVALID,
        SKIPPED,
        APPLIED,
        NONE
    }

    /**
     * 한 번 실행의 처리 상한(D-199). 기동 뒤 실행이 공용 비동기 스레드를 오래 잡지 않게 기록 수와 시간을 함께 묶는다. 상한에 닿으면 남은 기록은 표시를 그대로
     * 두고 다음 기동이 잇는다.
     *
     * @param maxRecords 한 번에 처리할 기록 수(1단계·2단계 합)
     * @param maxDuration 한 번에 쓸 시간
     */
    public record Budget(int maxRecords, Duration maxDuration) {
        public static final Budget PER_RUN = new Budget(1_000, Duration.ofSeconds(60));
    }

    /** PENDING을 다시 판정하고 VALID의 부수 처리를 마친다(상한 PER_RUN). 남은 일이 없으면 바로 돌아온다. */
    public Result run() {
        return run(Budget.PER_RUN);
    }

    /** run()과 같되 상한을 받는다. */
    public Result run(Budget budget) {
        Pass pass = new Pass(budget);
        drain(RejudgeState.PENDING, this::rejudgeOne, pass);
        drain(RejudgeState.VALID, this::applyOne, pass);
        Result result = pass.result();
        if (result.nowValid() + result.stillInvalid() + result.skipped() + result.applied() > 0
                || result.failed() > 0) {
            log.info(
                    "run_rejudged nowValid={} stillInvalid={} skipped={} applied={} failed={}"
                            + " budgetReached={}",
                    result.nowValid(),
                    result.stillInvalid(),
                    result.skipped(),
                    result.applied(),
                    result.failed(),
                    pass.isExhausted());
        }
        return result;
    }

    /** state인 기록을 BATCH개씩 골라 step을 기록마다 새 트랜잭션에서 돌린다. 실패한 기록은 이번 실행에서 다시 고르지 않는다. 상한에 닿으면 멈춘다. */
    private void drain(RejudgeState state, Function<String, Outcome> step, Pass pass) {
        List<String> ids = nextIds(state, pass);
        while (!ids.isEmpty()) {
            for (String id : ids) {
                if (pass.isExhausted()) {
                    return;
                }
                try {
                    pass.count(transaction.execute(status -> step.apply(id)));
                } catch (RuntimeException e) {
                    pass.fail(id);
                    log.error(
                            "run_rejudge_failed recordId={} state={} error={}",
                            id,
                            state,
                            e.getClass().getSimpleName(),
                            e);
                }
            }
            ids = pass.isExhausted() ? List.of() : nextIds(state, pass);
        }
    }

    private List<String> nextIds(RejudgeState state, Pass pass) {
        return runRecordRepository.rejudgeCandidates(state, BATCH + pass.failed.size()).stream()
                .filter(id -> !pass.failed.contains(id))
                .limit(BATCH)
                .toList();
    }

    /** 한 번 실행의 집계와 상한. 시간은 벽시계가 아니라 경과 시간(nanoTime)으로 잰다. */
    private static final class Pass {
        private final int[] counts = new int[Outcome.values().length];
        private final Set<String> failed = new HashSet<>();
        private final int maxRecords;
        private final long deadlineNanos;
        private int processed;

        Pass(Budget budget) {
            this.maxRecords = budget.maxRecords();
            this.deadlineNanos = System.nanoTime() + budget.maxDuration().toNanos();
        }

        boolean isExhausted() {
            return processed >= maxRecords || System.nanoTime() - deadlineNanos >= 0;
        }

        void count(Outcome outcome) {
            processed++;
            counts[(outcome == null ? Outcome.NONE : outcome).ordinal()]++;
        }

        void fail(String id) {
            processed++;
            failed.add(id);
        }

        Result result() {
            return new Result(
                    counts[Outcome.VALID.ordinal()],
                    counts[Outcome.INVALID.ordinal()],
                    counts[Outcome.SKIPPED.ordinal()],
                    counts[Outcome.APPLIED.ordinal()],
                    failed.size());
        }
    }

    /** 1단계: 한 기록을 새 규칙으로 다시 판정한다. 표시가 PENDING이 아니면(다른 인스턴스가 먼저 함) 아무것도 하지 않는다. */
    private Outcome rejudgeOne(String recordId) {
        RunRecord record = runRecordRepository.findForUpdate(recordId).orElse(null);
        if (record == null || record.rejudgeState() != RejudgeState.PENDING) {
            return Outcome.NONE;
        }
        if (!record.hasRoute() || record.isValid()) {
            runRecordRepository.moveRejudgeState(
                    recordId, RejudgeState.PENDING, RejudgeState.SKIPPED);
            return Outcome.SKIPPED;
        }
        boolean valid = record.rejudgeInvalid(ValidityRule.STANDARD);
        if (valid) {
            PersonalRecordBook book =
                    personalRecordBookRepository.findOrCreateForUpdate(record.getMemberId());
            book.apply(record);
            personalRecordBookRepository.save(book);
        }
        runRecordRepository.storeRejudgement(
                record, RejudgeState.PENDING, valid ? RejudgeState.VALID : RejudgeState.DONE);
        return valid ? Outcome.VALID : Outcome.INVALID;
    }

    /**
     * 2단계: 유효가 된 기록을 티어에 늦게 반영하고(이미 반영했으면 건너뜀) DONE으로 바꾼 뒤 리워드·배경 수신자에 알린다. 비활성 회원이면 부수 처리 없이 DONE.
     */
    private Outcome applyOne(String recordId) {
        RunRecord record = runRecordRepository.findForUpdate(recordId).orElse(null);
        if (record == null || record.rejudgeState() != RejudgeState.VALID) {
            return Outcome.NONE;
        }
        long memberId = record.getMemberId();
        boolean active = memberQueryService.isActive(memberId);
        if (active && !record.isTierApplied()) {
            ValidRunScore score =
                    runRecordRepository
                            .validRunScoreOf(recordId)
                            .orElseThrow(() -> new IllegalStateException("유효 기록 없음"));
            tierApplier.apply(
                    memberId,
                    recordId,
                    new RunScoreInput(score.score(), score.distance(), score.ordinal()));
        }
        runRecordRepository.moveRejudgeState(recordId, RejudgeState.VALID, RejudgeState.DONE);
        if (active) {
            eventPublisher.publish(
                    new RunRecordRejudged(
                            recordId, memberId, record.getDistance(), record.getSavedAt()));
        }
        return Outcome.APPLIED;
    }
}
