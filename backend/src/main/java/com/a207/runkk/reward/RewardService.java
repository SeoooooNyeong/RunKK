package com.a207.runkk.reward;

import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.Side;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.RewardRule;
import com.a207.runkk.running.RunRecordSaved;
import java.util.function.ToIntFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리워드 적립·조회 유스케이스(설계 6.1.8, srs.md 6.3 6.4). 적립은 기록 저장·대결 판정 커밋 뒤 동기로 받는다(D-03: 결과 화면에 바로 필요). 지갑
 * 행을 잠근 뒤 사건 발생일의 합계를 계산하고 (회원, 사유, 근거) 고유 제약이 이중 적립을 한 번 더 막는다(5.3). 수신자는 새 트랜잭션에서 적립하고 일시 오류(잠금
 * 대기·충돌·연결 일시 장애)면 3회까지 다시 한다. 그래도 실패하면 reward_accrual_failed 로그(회원·사유·근거)만 남기고 삼켜 업로드 응답과 뒤 수신자를
 * 막지 않는다(코드 리뷰 2차 M4). 유실분은 0시 일 단위 보정(RewardReconcileService.reconcile)이 같은 연산으로 채운다. 하루 상한의 "그날"은
 * 적립을 처리한 날이 아니라 사건 발생일(기록 저장 일시, 판정 시각)이라 수신자와 보정 배치가 같은 날 상한 안에서 센다.
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);

    /** 일시 오류 재시도 횟수(5.3과 같다). */
    static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_PAUSE_MILLIS = 20;

    private final RewardWalletRepository rewardWalletRepository;
    private final OperationPolicy operationPolicy;
    private final TransactionTemplate newTransaction;

    public RewardService(
            RewardWalletRepository rewardWalletRepository,
            OperationPolicy operationPolicy,
            PlatformTransactionManager transactionManager) {
        this.rewardWalletRepository = rewardWalletRepository;
        this.operationPolicy = operationPolicy;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 유효 러닝만. ⌊km × perKm⌋을 기록 ID 근거로 적립한다(FR-159). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (!e.valid()) {
            return;
        }
        accrueWithRetry(
                new AccrualSource(
                        e.memberId(), AccrualReason.VALID_RUN, e.recordId(), e.occurredAt()),
                rule -> rule.amountForRun(e.distance()));
    }

    /**
     * 이긴 회원에게 BATTLE_WIN을 대결 ID 근거로 적립한다. 운영자가 RewardRule을 바꾸면 다음 판정부터 바뀐 값이다(design-review.md
     * M13). 정정(corrected)으로 승자가 바뀌면 새 승자에게 적립하고 이전 승자의 적립은 회수하지 않는다(srs.md 6.3 6.4 제안). 같은 회원·대결은
     * 정정 사건이 다시 와도 한 번만 적립한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBattleJudged(BattleJudged e) {
        e.judgement()
                .winnerSide()
                .filter(Side.MemberSide.class::isInstance)
                .map(side -> ((Side.MemberSide) side).memberId())
                .ifPresent(
                        winner ->
                                accrueWithRetry(
                                        new AccrualSource(
                                                winner,
                                                AccrualReason.BATTLE_WIN,
                                                String.valueOf(e.battleId()),
                                                e.judgement().at()),
                                        RewardRule::battleWin));
    }

    /**
     * 새 트랜잭션에서 근거 source를 사건 발생 시각 기준으로, 현재 RewardRule에 amountOf를 적용한 양만큼 적립하고 실제 적립량을 돌려준다. 일시 오류면
     * 다시 하고, 그래도 실패하면 실패 로그만 남기고 0이다(M4). 수신자와 일 단위 보정(RewardReconcileService)이 함께 쓴다. 같은 (회원, 사유,
     * 근거)가 이미 있으면 0이다.
     */
    int accrueWithRetry(AccrualSource source, ToIntFunction<RewardRule> amountOf) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                Integer amount =
                        newTransaction.execute(
                                status -> {
                                    RewardRule rule = operationPolicy.rewardRule();
                                    return accrue(
                                            source.memberId(),
                                            source.request(amountOf.applyAsInt(rule)),
                                            rule);
                                });
                return amount == null ? 0 : amount;
            } catch (TransientDataAccessException e) {
                log.info(
                        "reward_accrual_retry memberId={} reason={} sourceId={} attempt={}",
                        source.memberId(),
                        source.reason(),
                        source.sourceId(),
                        attempt);
                if (attempt == MAX_ATTEMPTS) {
                    failed(source, e);
                } else {
                    pause(attempt);
                }
            } catch (RuntimeException e) {
                failed(source, e);
                return 0;
            }
        }
        return 0;
    }

    /** 일시 오류 재시도 사이에 잠깐 쉰다(20·40ms, 잠금을 쥔 쪽이 끝낼 틈). */
    private static void pause(int attempt) {
        try {
            Thread.sleep(RETRY_PAUSE_MILLIS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void failed(AccrualSource source, RuntimeException e) {
        log.error(
                "reward_accrual_failed memberId={} reason={} sourceId={} error={}",
                source.memberId(),
                source.reason(),
                source.sourceId(),
                e.getClass().getSimpleName(),
                e);
    }

    private int accrue(long memberId, AccrualRequest req, RewardRule rule) {
        RewardWallet wallet = rewardWalletRepository.findOrCreateForUpdate(memberId);
        int amount = wallet.accrue(req, rule);
        rewardWalletRepository.save(wallet);
        log.info("reward_accrued memberId={} reason={} amount={}", memberId, req.reason(), amount);
        return amount;
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
