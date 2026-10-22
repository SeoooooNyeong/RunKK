package com.a207.runkk.batch.reward;

import com.a207.runkk.reward.RewardReconcileService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 0시 10분에 리워드 적립 일 단위 보정을 부른다(코드 리뷰 2차 M4 결정, D-38·D-39와 같은 방식). now만 넘기고 대상 기록·대결은
 * RewardReconcileService가 고른다. 실패는 운영 로그로 남긴다.
 */
@Component
public class RewardReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(RewardReconcileJob.class);

    private final RewardReconcileService rewardReconcileService;
    private final Clock clock;

    public RewardReconcileJob(RewardReconcileService rewardReconcileService, Clock clock) {
        this.rewardReconcileService = rewardReconcileService;
        this.clock = clock;
    }

    public void run() {
        try {
            rewardReconcileService.reconcile(LocalDateTime.now(clock));
        } catch (RuntimeException e) {
            log.error("reward_reconcile_failed error={}", e.getClass().getSimpleName(), e);
        }
    }
}
