package com.a207.runkk.runimal;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RunimalRule;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runimal 유형 판정(설계 6.1.8, SQ-10, srs.md 6.3 6.2). 저장 사건을 커밋 뒤 비동기로 받아 코스 동일성 판정 → 순번 반영 → 판정 지점이면
 * 표본·태그로 임시·확정 유형을 정한다. 판정 회차는 running이 정한 순번이라 티어 갱신과 같은 회차다(D-37).
 */
@Service
public class RunimalService {

    private static final Logger log = LoggerFactory.getLogger(RunimalService.class);

    private final RunimalProfileRepository runimalProfileRepository;
    private final CourseSimilarityService courseSimilarityService;
    private final RunimalJudge runimalJudge;
    private final RunSampleReader runSampleReader;
    private final OperationPolicy operationPolicy;
    private final Clock clock;
    private final RunimalTransactions transactions;

    RunimalService(
            RunimalProfileRepository runimalProfileRepository,
            CourseSimilarityService courseSimilarityService,
            RunimalJudge runimalJudge,
            RunSampleReader runSampleReader,
            OperationPolicy operationPolicy,
            Clock clock,
            RunimalTransactions transactions) {
        this.runimalProfileRepository = runimalProfileRepository;
        this.courseSimilarityService = courseSimilarityService;
        this.runimalJudge = runimalJudge;
        this.runSampleReader = runSampleReader;
        this.operationPolicy = operationPolicy;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * 비동기. 유효 기록만. 경로가 있으면 코스 판정 → onValidRun → PROVISIONAL·CONFIRM이면 판정(SQ-10). 같은 회원의 동시 처리와 충돌하면
     * 새 트랜잭션으로 다시 읽어 재시도하고(최대 3회), 그래도 실패하면 로그만 남긴다(다음 판정 회차에 다시 판정, D-40).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid() || e.validRunOrdinal() < 1) {
            return;
        }
        try {
            transactions.retrying(
                    () -> {
                        apply(e);
                        return Boolean.TRUE;
                    });
        } catch (RuntimeException failure) {
            log.error(
                    "runimal_judge_failed recordId={} error={}",
                    e.recordId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    private void apply(RunRecordSaved e) {
        if (e.hasRoute()) {
            courseSimilarityService.judge(e.memberId(), e.recordId());
        }
        RunimalProfile profile = runimalProfileRepository.findOrCreate(e.memberId());
        JudgeTrigger trigger = profile.onValidRun(e.validRunOrdinal());
        if (trigger != JudgeTrigger.NONE) {
            judge(profile, trigger);
        }
        runimalProfileRepository.save(profile);
    }

    private void judge(RunimalProfile profile, JudgeTrigger trigger) {
        LocalDateTime now = LocalDateTime.now(clock);
        RunSample sample = runSampleReader.read(profile.getMemberId(), now.toLocalDate());
        RunimalRule rule = operationPolicy.runimalRule();
        if (trigger == JudgeTrigger.PROVISIONAL) {
            Judged judged = runimalJudge.judgeProvisional(sample, rule);
            profile.assignProvisional(judged.type(), judged.ratios(), now);
            return;
        }
        Judged judged = runimalJudge.judgeConfirmed(sample, rule);
        boolean changed = profile.confirm(judged.type(), judged.ratios(), now);
        log.info("runimal_confirmed changed={}", changed);
    }

    /** 유형, 남은 횟수, 카드, 16유형 목록(FR-151~FR-154). */
    @Transactional(readOnly = true)
    public RunimalStatusView status(long memberId) {
        RunimalProfile profile = runimalProfileRepository.findOrCreate(memberId);
        return new RunimalStatusView(
                profile.getProvisionalType().orElse(null),
                profile.getConfirmedType().orElse(null),
                profile.getValidRunCount(),
                profile.remainingToNextJudgement(),
                profile.card().orElse(null),
                List.of(RunimalType.values()));
    }

    /** 공유 카드. 유형이 없으면 NO_TYPE(FR-168). */
    @Transactional(readOnly = true)
    public RunimalCardView shareCard(long memberId) {
        return runimalProfileRepository
                .findOrCreate(memberId)
                .card()
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.NO_TYPE));
    }
}
