package com.a207.runkk.event;

import com.a207.runkk.battle.BattleHistoryService;
import com.a207.runkk.battle.BattleJudged;
import com.a207.runkk.battle.BattleType;
import com.a207.runkk.battle.CelebrityChallenge;
import com.a207.runkk.battle.Side;
import com.a207.runkk.reward.RewardService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 코치 승리 포인트(S15P31A207-275). 셀럽 대결 판정(BattleJudged)을 커밋 뒤 받아, 회원이 이겼고 판정 시각이 공개 중인 COACH_CELEB 이벤트
 * 기간 안이면 그 이벤트 셀럽 중 기간 안에 이겼는데 아직 포인트를 받지 않은 셀럽마다 pointPerWin을 이벤트 포인트(적립 사유 EVENT, 근거 "이벤트
 * ID:celebrity-ID")로 주고 event_point_grant에 남긴다. 적립은 reward_accrual 고유 제약과 지갑 잠금이 한 번만 되게 막고, 지급 이력은
 * (회원, 이벤트, 대상) 고유 제약으로 한 번만 남는다. 어떤 실패도 대결 결과 확정을 막지 않는다(event_point_failed 로그만).
 */
@Component
public class CoachWinPointListener {

    private static final Logger log = LoggerFactory.getLogger(CoachWinPointListener.class);

    private final EventRepository eventRepository;
    private final BattleHistoryService battleHistoryService;
    private final RewardService rewardService;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public CoachWinPointListener(
            EventRepository eventRepository,
            BattleHistoryService battleHistoryService,
            RewardService rewardService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.eventRepository = eventRepository;
        this.battleHistoryService = battleHistoryService;
        this.rewardService = rewardService;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBattleJudged(BattleJudged e) {
        if (e.type() != BattleType.CELEBRITY) {
            return;
        }
        e.winnerSide()
                .filter(Side.MemberSide.class::isInstance)
                .map(side -> ((Side.MemberSide) side).memberId())
                .ifPresent(memberId -> grantSafely(memberId, e.battleId(), e.judgedAt()));
    }

    private void grantSafely(long memberId, long battleId, LocalDateTime judgedAt) {
        try {
            grant(memberId, judgedAt);
        } catch (RuntimeException failure) {
            log.error(
                    "event_point_failed memberId={} battleId={} error={}",
                    memberId,
                    battleId,
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    /** 줄 포인트를 새 트랜잭션에서 찾고, 하나씩 적립한 뒤 지급 이력을 새 트랜잭션에서 남긴다. */
    void grant(long memberId, LocalDateTime judgedAt) {
        List<Due> dues = newTransaction.execute(status -> duePoints(memberId, judgedAt));
        if (dues == null) {
            return;
        }
        for (Due due : dues) {
            int amount =
                    rewardService.accrueEvent(
                            memberId, due.eventId() + ":" + due.key(), due.amount(), due.wonAt());
            if (amount <= 0) {
                log.warn(
                        "event_point_not_accrued memberId={} eventId={} key={}",
                        memberId,
                        due.eventId(),
                        due.key());
                continue;
            }
            newTransaction.executeWithoutResult(
                    status ->
                            eventRepository.savePointGrant(
                                    new EventPointGrant(
                                            memberId,
                                            due.eventId(),
                                            due.key(),
                                            amount,
                                            LocalDateTime.now(clock))));
            log.info(
                    "event_point_granted memberId={} eventId={} key={} amount={}",
                    memberId,
                    due.eventId(),
                    due.key(),
                    amount);
        }
    }

    private List<Due> duePoints(long memberId, LocalDateTime judgedAt) {
        List<Due> dues = new ArrayList<>();
        for (Event event : eventRepository.findAll()) {
            if (event.getKind() != EventKind.COACH_CELEB || !event.isOpenAt(judgedAt)) {
                continue;
            }
            EventConfig config = event.parsedConfig();
            if (config.pointPerWin() <= 0) {
                continue;
            }
            for (CelebrityChallenge c :
                    battleHistoryService.celebrityChallenges(
                            memberId,
                            config.celebrityIds(),
                            event.getStartsAt(),
                            event.getEndsAt())) {
                String key = EventService.celebrityKey(c.celebrityId());
                if (c.firstWonAt() != null
                        && !eventRepository.pointGranted(memberId, event.getId(), key)) {
                    dues.add(new Due(event.getId(), key, config.pointPerWin(), c.firstWonAt()));
                }
            }
        }
        return dues;
    }

    /** 줄 포인트 하나. */
    private record Due(long eventId, String key, int amount, LocalDateTime wonAt) {}
}
