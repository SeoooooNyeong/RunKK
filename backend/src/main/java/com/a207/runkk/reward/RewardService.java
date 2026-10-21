package com.a207.runkk.reward;

import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.Side;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 리워드 적립·조회 유스케이스(설계 6.1.8, srs.md 6.3 6.4). 적립은 기록 저장·대결 판정 커밋 뒤 동기로 받는다(D-03: 결과 화면에 바로 필요). 지갑
 * 행을 잠근 뒤 오늘 합계를 계산하고 (회원, 사유, 근거) 고유 제약이 이중 적립을 한 번 더 막는다(5.3).
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    private final RewardWalletRepository rewardWalletRepository;
    private final OperationPolicy operationPolicy;
    private final Clock clock;

    public RewardService(
            RewardWalletRepository rewardWalletRepository,
            OperationPolicy operationPolicy,
            Clock clock) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.operationPolicy = operationPolicy;
        this.clock = clock;
    }

    /** 유효 러닝만. ⌊km × perKm⌋을 기록 ID 근거로 적립한다(FR-159). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        RewardRule rule = operationPolicy.rewardRule();
        accrue(
                e.memberId(),
                new AccrualRequest(
                        AccrualReason.VALID_RUN,
                        e.recordId(),
                        rule.amountForRun(e.distance()),
                        LocalDateTime.now(clock)),
                rule);
    }

    /**
     * 이긴 회원에게 BATTLE_WIN을 대결 ID 근거로 적립한다. 운영자가 RewardRule을 바꾸면 다음 판정부터 바뀐 값이다(design-review.md
     * M13). 정정(corrected)으로 승자가 바뀌면 새 승자에게 적립하고 이전 승자의 적립은 회수하지 않는다(srs.md 6.3 6.4 제안). 같은 회원·대결은
     * 정정 사건이 다시 와도 한 번만 적립한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onBattleJudged(BattleJudged e) {
        e.judgement()
                .winnerSide()
                .filter(Side.MemberSide.class::isInstance)
                .map(side -> ((Side.MemberSide) side).memberId())
                .ifPresent(winner -> accrueBattleWin(winner, e.battleId()));
    }

    private void accrueBattleWin(long winner, long battleId) {
        RewardRule rule = operationPolicy.rewardRule();
        accrue(
                winner,
                new AccrualRequest(
                        AccrualReason.BATTLE_WIN,
                        String.valueOf(battleId),
                        rule.battleWin(),
                        LocalDateTime.now(clock)),
                rule);
    }

    private void accrue(long memberId, AccrualRequest req, RewardRule rule) {
        RewardWallet wallet = rewardWalletRepository.findOrCreateForUpdate(memberId);
        int amount = wallet.accrue(req, rule);
        rewardWalletRepository.save(wallet);
        log.info("reward_accrued memberId={} reason={} amount={}", memberId, req.reason(), amount);
    }

    /** 근거 하나의 적립량. analysis(기록 상세)와 web.battle(대결 결과 화면)이 부른다. */
    @Transactional(readOnly = true)
    public int accruedFor(AccrualReason reason, String sourceId, long memberId) {
        return rewardWalletRepository
                .findByMember(memberId)
                .map(w -> w.accruedFor(reason, sourceId))
                .orElse(0);
    }

    /** 보유 확인. avatar.AvatarService가 착용 전에 부른다. */
    @Transactional(readOnly = true)
    public boolean owns(long memberId, long itemId) {
        return rewardWalletRepository.findByMember(memberId).map(w -> w.owns(itemId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public int balanceOf(long memberId) {
        return rewardWalletRepository.findByMember(memberId).map(RewardWallet::balance).orElse(0);
    }
}
