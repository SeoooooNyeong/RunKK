package com.a207.runkk.reward;

import com.a207.runkk.battle.BattleResultService;
import com.a207.runkk.battle.BattleWin;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.SavedRun;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 리워드 적립 일 단위 보정(코드 리뷰 2차 M4 결정, D-38 주간 거리 재합산과 같은 방식). 커밋 뒤 수신자(RewardService.onRunSaved·
 * onBattleJudged)가 재시도 3회까지 실패해 빠진 적립을, batch.reward의 RewardReconcileJob이 매일 0시 10분에 now만 넘겨 채운다.
 * 최근 {@value #LOOKBACK_DAYS}일 0시부터 now까지 저장된 유효 기록과 회원이 이긴 판정을 다시 보고, 수신자와 같은
 * RewardService.accrueWithRetry로 적립한다. 이미 적립된 근거는 지갑 잠금 뒤 accruedFor 확인과 (회원, 사유, 근거) 고유 제약이 막아
 * 0이므로 여러 번 돌려도 같다(멱등). 하루 상한은 사건 발생일(기록 저장 일시, 판정 시각) 기준이다. 적립량은 보정 시점의 RewardRule로 센다(운영자가 바꾸면 다음
 * 적립부터 바뀐 값, design-review.md M13). 탈퇴한 회원은 지갑을 다시 만들지 않도록 건너뛴다.
 */
@Service
public class RewardReconcileService {

    /** 오늘 0시에서 거슬러 볼 날 수. 전날 전체 + 하루 여유(하루 보정이 빠져도 다음 날이 덮는다). */
    static final int LOOKBACK_DAYS = 2;

    private static final Logger log = LoggerFactory.getLogger(RewardReconcileService.class);

    private final RewardService rewardService;
    private final RunQueryService runQueryService;
    private final BattleResultService battleResultService;
    private final MemberQueryService memberQueryService;

    public RewardReconcileService(
            RewardService rewardService,
            RunQueryService runQueryService,
            BattleResultService battleResultService,
            MemberQueryService memberQueryService) {
        this.rewardService = rewardService;
        this.runQueryService = runQueryService;
        this.battleResultService = battleResultService;
        this.memberQueryService = memberQueryService;
    }

    /** 빠진 적립을 채우고 새로 적립한 건수(적립량 &gt; 0)를 돌려준다. 근거 하나의 실패는 로그만 남기고 다음 근거로 넘어간다. */
    public int reconcile(LocalDateTime now) {
        LocalDateTime from = now.toLocalDate().minusDays(LOOKBACK_DAYS).atStartOfDay();
        Map<Long, Boolean> active = new HashMap<>();
        int restored = 0;
        for (SavedRun run : runQueryService.savedBetween(from, now)) {
            if (run.valid()) {
                restored +=
                        restore(
                                active,
                                run.memberId(),
                                AccrualReason.VALID_RUN,
                                run.recordId(),
                                rule -> rule.amountForRun(run.distance()),
                                run.savedAt());
            }
        }
        for (BattleWin win : battleResultService.winsJudgedBetween(from, now)) {
            restored +=
                    restore(
                            active,
                            win.winnerId(),
                            AccrualReason.BATTLE_WIN,
                            String.valueOf(win.battleId()),
                            RewardRule::battleWin,
                            win.judgedAt());
        }
        log.info("reward_reconciled from={} restored={}", from, restored);
        return restored;
    }

    private int restore(
            Map<Long, Boolean> active,
            long memberId,
            AccrualReason reason,
            String sourceId,
            ToIntFunction<RewardRule> amountOf,
            LocalDateTime at) {
        if (!active.computeIfAbsent(memberId, memberQueryService::isActive)) {
            return 0;
        }
        if (rewardService.accruedFor(reason, sourceId, memberId) > 0) {
            return 0; // 이미 적립됨: 지갑 잠금 없이 넘긴다
        }
        int amount = rewardService.accrueWithRetry(memberId, reason, sourceId, amountOf, at);
        if (amount > 0) {
            log.info(
                    "reward_accrual_restored memberId={} reason={} sourceId={} amount={}",
                    memberId,
                    reason,
                    sourceId,
                    amount);
            return 1;
        }
        return 0;
    }
}
