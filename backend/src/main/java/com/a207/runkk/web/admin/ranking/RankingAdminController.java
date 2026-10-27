package com.a207.runkk.web.admin.ranking;

import com.a207.runkk.analysis.RankingRecomputeResult;
import com.a207.runkk.analysis.RankingService;
import com.a207.runkk.audit.ActorRole;
import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditEntry;
import com.a207.runkk.audit.AuditService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 랭킹 다시 계산(POST /admin/rankings/recompute → RankingService.recompute, 12.2 D77, D-136:
 * web.admin에서 web.admin.ranking으로 옮겼다. 경로는 그대로). 개인·크루 랭킹 스냅숏은 0시 작업과 첫 시작 때만 집계되므로(D-25) 시연·운영 중
 * 데이터를 넣은 직후 바로 반영하려고 둔다. 권한은 OperatorCheckInterceptor가 먼저 확인하고(D-50) 확인된 운영자 계정(actorId)으로 실행마다 감사
 * RANKING_RECOMPUTED를 한 건 남긴다. 다른 집계가 돌고 있으면 RankingService가 IllegalStateException을 던져 409
 * INVALID_STATE다(진행 중이면 막는 것 말고 따로 횟수 제한은 두지 않는다: 운영자 화이트리스트 전용이고 한 번에 하나만 돈다). 요청 본문은 없다.
 *
 * <p>analysis는 Fan-Out 상한(7, 설계 12장)에 걸려 있어 analysis → audit을 더하지 않고, 감사는 이 컨트롤러가 집계가 끝난 뒤 남긴다(집계
 * 자체가 종류·기간별 트랜잭션 4개라 감사와 한 트랜잭션으로 묶을 수 없다).
 */
@RestController
@RequestMapping("/api/v1/admin/rankings")
public class RankingAdminController {

    /** web.admin OperatorCheckInterceptor가 붙이는 요청 속성 이름(D-50). web.admin을 import하지 않는다(D-136). */
    private static final String ACTOR_ID = "actorId";

    /** 감사 기록 대상 ID(스냅숏 4개 전체). */
    static final String TARGET = "RANKING";

    private final RankingService rankingService;
    private final AuditService auditService;

    public RankingAdminController(RankingService rankingService, AuditService auditService) {
        this.rankingService = rankingService;
        this.auditService = auditService;
    }

    /**
     * 지금 시각으로 개인·크루 × 주간·월간 스냅숏 4개를 다시 만든다.
     *
     * @return 집계 시각(한국 시간)·개인 회원 수·크루 수·저장 실패 스냅숏 수
     */
    @PostMapping("/recompute")
    RankingRecomputeResult recompute(@RequestAttribute(ACTOR_ID) String actorId) {
        RankingRecomputeResult result = rankingService.recompute();
        auditService.record(
                new AuditEntry(
                        actorId,
                        ActorRole.OPERATOR,
                        AuditAction.RANKING_RECOMPUTED,
                        TARGET,
                        null,
                        "computedAt="
                                + result.computedAt()
                                + ",personal="
                                + result.personalCount()
                                + ",crew="
                                + result.crewCount()
                                + ",failed="
                                + result.failedSnapshots()));
        return result;
    }
}
