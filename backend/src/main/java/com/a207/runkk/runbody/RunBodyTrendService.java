package com.a207.runkk.runbody;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.ValidRunStat;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 런바디 요약 "최근 10회 변화"·"최근 10회 평균"(G-14)과 첫 유효 러닝 일시를 러닝 기록에서 셈한다(RunBodyTrend). ai를 부르지 않는다. 운영 기준값
 * RUNBODY_ENABLED가 꺼져 있으면 다른 런바디 조회와 같이 FEATURE_CLOSED다(D-440).
 */
@Service
public class RunBodyTrendService {

    private final RunQueryService runs;
    private final OperationPolicy policy;

    public RunBodyTrendService(RunQueryService runs, OperationPolicy policy) {
        this.runs = runs;
        this.policy = policy;
    }

    /**
     * 유효 러닝 수와 회차 목록을 한 읽기 트랜잭션의 같은 스냅숏(REPEATABLE READ)에서 읽어 그 사이 기록 삭제가 끼지 않게 한다(MR !91 BR-3).
     * 그래도 회차가 비면 RunBodyTrend가 그 회차를 null로 둔다.
     *
     * @throws BusinessRuleException FEATURE_CLOSED(기능 닫힘)
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RunBodyTrend trendOf(long memberId) {
        if (!policy.features().runbodyEnabled()) {
            throw new BusinessRuleException(ReasonCode.FEATURE_CLOSED);
        }
        int count = runs.validRunCount(memberId);
        int round = RunBodyTrend.roundOf(count);
        List<ValidRunStat> recent = round >= 1 ? window(memberId, round) : List.of();
        List<ValidRunStat> previous = round >= 2 ? window(memberId, round - 1) : List.of();
        LocalDateTime first =
                count > 0
                        ? runs.validRunStats(memberId, 1, 1).stream()
                                .findFirst()
                                .map(ValidRunStat::startedAt)
                                .orElse(null)
                        : null;
        return RunBodyTrend.of(count, first, recent, previous);
    }

    private List<ValidRunStat> window(long memberId, int round) {
        int to = round * RunBodyTrend.WINDOW;
        return runs.validRunStats(memberId, to - RunBodyTrend.WINDOW + 1, to);
    }
}
