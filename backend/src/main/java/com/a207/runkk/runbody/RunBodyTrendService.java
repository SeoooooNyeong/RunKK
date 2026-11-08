package com.a207.runkk.runbody;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.ValidRunStat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 런바디 요약 "최근 10회 변화"(G-14)를 러닝 기록에서 셈한다(RunBodyTrend). ai를 부르지 않는다. 운영 기준값 RUNBODY_ENABLED가 꺼져 있으면
 * 다른 런바디 조회와 같이 FEATURE_CLOSED다(D-440).
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
     * @throws BusinessRuleException FEATURE_CLOSED(기능 닫힘)
     */
    public RunBodyTrend trendOf(long memberId) {
        if (!policy.features().runbodyEnabled()) {
            throw new BusinessRuleException(ReasonCode.FEATURE_CLOSED);
        }
        int count = runs.validRunCount(memberId);
        int round = RunBodyTrend.roundOf(count);
        List<ValidRunStat> recent = round >= 1 ? window(memberId, round) : List.of();
        List<ValidRunStat> previous = round >= 2 ? window(memberId, round - 1) : List.of();
        return RunBodyTrend.of(count, recent, previous);
    }

    private List<ValidRunStat> window(long memberId, int round) {
        int to = round * RunBodyTrend.WINDOW;
        return runs.validRunStats(memberId, to - RunBodyTrend.WINDOW + 1, to);
    }
}
