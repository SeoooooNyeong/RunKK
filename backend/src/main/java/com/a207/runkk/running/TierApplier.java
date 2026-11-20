package com.a207.runkk.running;

import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.RunScoreInput;
import com.a207.runkk.member.TierChange;
import com.a207.runkk.member.TierLevelService;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 유효 기록 하나를 티어에 반영하고 기록별 반영 표시(tier_applied)를 세운다(총괄 결정, 설계 쟁점 7-3). TierUpdateListener와
 * TierReconcileService가 같은 규칙을 쓰게 묶은 패키지 전용 보조다. 부르는 쪽 트랜잭션 안에서 돌아야 한다: 표시를 세우지 못하면(이미 반영)
 * IllegalStateException으로 그 트랜잭션의 티어 반영까지 되돌린다.
 */
@Component
class TierApplier {

    private final TierLevelService tierLevelService;
    private final MemberQueryService memberQueryService;
    private final RunRecordRepository runRecordRepository;

    TierApplier(
            TierLevelService tierLevelService,
            MemberQueryService memberQueryService,
            RunRecordRepository runRecordRepository) {
        this.tierLevelService = tierLevelService;
        this.memberQueryService = memberQueryService;
        this.runRecordRepository = runRecordRepository;
    }

    /**
     * 순번이 이미 반영한 순번보다 크면 recordValidRun(판정 지점이면 판정), 아니면 뒤 기록이 먼저 반영된 구멍이라 recordLateValidRun(판정 없이
     * 점수·거리만)으로 넘긴 뒤 표시를 세운다. 이미 서 있으면 IllegalStateException.
     */
    void apply(long memberId, String recordId, RunScoreInput input) {
        TierChange change =
                input.validRunOrdinal() > memberQueryService.appliedValidRunCount(memberId)
                        ? tierLevelService.recordValidRun(memberId, input)
                        : tierLevelService.recordLateValidRun(memberId, input);
        // 평가(첫 배치·재평가)는 member.TierProgress가 정하고 여기서는 반영 표시와 함께 저장만 한다(v0.14 D-291·D-294)
        // infra.running은 member를 읽지 않으므로 running 값(EvaluationView)으로 넘긴다(13.1 규칙 1)
        Optional<EvaluationView> evaluation =
                change == null
                        ? Optional.empty()
                        : change.evaluation()
                                .map(
                                        e ->
                                                new EvaluationView(
                                                        e.kind().name(),
                                                        change.tier(),
                                                        e.validRuns(),
                                                        null,
                                                        e.previousTier()));
        boolean marked = runRecordRepository.markTierApplied(recordId, evaluation);
        if (!marked) {
            throw new IllegalStateException("이미 티어에 반영한 기록");
        }
    }
}
