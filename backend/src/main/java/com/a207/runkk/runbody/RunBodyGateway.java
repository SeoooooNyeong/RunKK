package com.a207.runkk.runbody;

import java.time.YearMonth;

/**
 * ai 런바디에 제출·조회한다(설계 05-19 2.2, D-449). 구현은 infra.integration.ai.AiServerRunBodyGateway. 재시도하지 않는다.
 * 재시도는 RunBodyService가 작업 상태로 한다.
 */
public interface RunBodyGateway {

    /**
     * POST /sessions/{id}/analyze. 202면 끝난다.
     *
     * @throws RunBodyRejectedException ai가 받지 않는 요청(409·413·422)
     * @throws RunBodyUnavailableException 503·시간 초과·연결 실패·알 수 없는 응답
     */
    void submit(RunBodyRequest request);

    /**
     * GET /sessions/{id}/status. ai pending은 ANALYZING, 404(ai가 세션을 모름)는 FAILED(재제출)로 준다.
     *
     * @throws RunBodyUnavailableException 503·시간 초과·연결 실패·알 수 없는 응답
     */
    RunBodyStatus statusOf(String recordId);

    /**
     * GET /sessions/{id}. 200이면 DONE과 본문, 409 ANALYSIS_PENDING은 ANALYZING, 409 ANALYSIS_FAILED·404는
     * FAILED.
     *
     * @throws RunBodyUnavailableException 503·시간 초과·연결 실패·알 수 없는 응답
     */
    RunBodySession sessionOf(String recordId);

    /**
     * GET /users/{memberId}/months/{yyyy-MM}. 러닝이 없는 달도 본문이 있다.
     *
     * @throws RunBodyUnavailableException 503·시간 초과·연결 실패·알 수 없는 응답
     */
    RunBodyMonth monthOf(long memberId, YearMonth month);
}
