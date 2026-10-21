package com.a207.runkk.battle;

import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.Week;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSummary;
import com.a207.runkk.notification.NavigationTarget;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.notification.Screen;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대결 결과 확정(설계 6.1.6, SQ-05, srs.md 6.3 4.5). 대결 ID가 붙은 기록이 저장되면 검증(유효 AND 대결 거리 도달)해 판정하고, 정정이면
 * 알린다(FR-103). 적립 리워드는 이 결과에 없다(web.battle이 붙인다, D-33). 같은 대결을 LiveBattleService와 동시에 바꾸면 다시 읽어
 * 재시도한다(5.3).
 */
@Service
public class BattleResultService {

    private static final Logger log = LoggerFactory.getLogger(BattleResultService.class);
    private static final String CORRECTED_TEXT = "대결 결과가 정정되었어요.";

    private final BattleRepository battleRepository;
    private final RunQueryService runQueryService;
    private final MemberQueryService memberQueryService;
    private final NotificationService notificationService;
    private final DomainEventPublisher eventPublisher;
    private final BattleMessenger battleMessenger;
    private final Clock clock;
    private final BattleTransactions transactions;

    public BattleResultService(
            BattleRepository battleRepository,
            RunQueryService runQueryService,
            MemberQueryService memberQueryService,
            NotificationService notificationService,
            DomainEventPublisher eventPublisher,
            BattleMessenger battleMessenger,
            Clock clock,
            BattleTransactions transactions) {
        this.battleRepository = battleRepository;
        this.runQueryService = runQueryService;
        this.memberQueryService = memberQueryService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.battleMessenger = battleMessenger;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * 대결 ID가 있는 기록만. 사건의 valid와 timeToReach로 verify → judge → 판정되면 BattleJudged, 정정이면
     * BATTLE_CORRECTED(D-51).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunSaved(RunRecordSaved e) {
        if (e.battleId() == null) {
            return;
        }
        try {
            transactions.retryingRun(() -> applyRecord(e));
        } catch (RuntimeException failure) {
            // 저장은 이미 커밋됐다. 판정은 카운트다운 종료·24시간 만료 때 다시 한다(A-11).
            log.error(
                    "battle_record_failed battleId={} error={}",
                    e.battleId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    private void applyRecord(RunRecordSaved e) {
        Optional<Battle> found = battleRepository.findById(e.battleId());
        if (found.isEmpty() || !found.get().isParticipant(e.memberId())) {
            return;
        }
        Battle battle = found.get();
        Distance distance = battle.getDistance();
        Optional<Duration> reach = runQueryService.timeToReach(e.recordId(), distance);
        battle.verify(
                e.memberId(), e.recordId(), e.valid() && reach.isPresent(), reach.orElse(null));
        battle.getOpponentTrack().ifPresent(track -> recordGaps(battle, e, track));
        markSelfRecord(battle, e);
        boolean running = battle.getStatus().isRunning();
        Optional<Judgement> judgement = battle.judge(LocalDateTime.now(clock));
        battleRepository.save(battle);
        if (running && judgement.isPresent()) {
            endedEarly(battle);
        }
        judgement.ifPresent(j -> announce(battle, j));
    }

    /**
     * 카운트다운이 끝나기 전에 기록이 모두 와 진행 중에 판정됐다. 시계의 close가 돌지 않으므로 여기서 커밋 뒤 BattleEnded(FINISHED)를 보낸다(코드
     * 리뷰 2차 L15). 실시간 세션은 끊김 감시(detectTimeouts)가 대결이 끝난 것을 보고 지운다.
     */
    private void endedEarly(Battle battle) {
        List<Long> recipients = battle.memberIds();
        BattleEnded ended = new BattleEnded(battle.getId(), BattleEnded.Status.FINISHED);
        AfterCommit.run(() -> battleMessenger.sendAll(recipients, ended));
    }

    /** 기록 상대와의 거리 차 최댓값을 러너 기록 표본으로 구한다(비교표 MAX_LEAD·MAX_GAP). 경로가 파기됐으면 건너뛴다. */
    private void recordGaps(Battle battle, RunRecordSaved e, OpponentTrack opponent) {
        RunCurve mine;
        try {
            mine = runQueryService.curve(e.recordId(), battle.getDistance());
        } catch (BusinessRuleException purged) {
            return;
        }
        long lead = 0;
        long trail = 0;
        for (CurvePoint point : mine.samples()) {
            long gap = point.distance().meters() - opponent.distanceAt(point.elapsed()).meters();
            lead = Math.max(lead, gap);
            trail = Math.max(trail, -gap);
        }
        battle.recordGaps(e.memberId(), Distance.ofMeters(lead), Distance.ofMeters(trail));
    }

    /** 나와의 싸움 기록이 그 거리 최고 기록이 되었는가(이력 신기록 여부, srs.md 6.3 4.7). */
    private void markSelfRecord(Battle battle, RunRecordSaved e) {
        if (battle.getType() != BattleType.SELF) {
            return;
        }
        boolean best =
                battle.standardDistance()
                        .flatMap(d -> runQueryService.best(e.memberId(), d))
                        .map(BestEntry::recordId)
                        .filter(e.recordId()::equals)
                        .isPresent();
        if (best) {
            battle.markNewRecord(e.memberId());
        }
    }

    private void announce(Battle battle, Judgement judgement) {
        eventPublisher.publish(
                new BattleJudged(battle.getId(), battle.getType(), judgement, judgement.at()));
        if (!judgement.corrected()) {
            return;
        }
        for (long memberId : battle.memberIds()) {
            notificationService.notify(
                    new NotificationRequest(
                            memberId,
                            NotificationType.BATTLE_CORRECTED,
                            CORRECTED_TEXT,
                            new NavigationTarget(
                                    Screen.BATTLE_RESULT, String.valueOf(battle.getId()))));
        }
    }

    /** Battle.forfeit → 상대에게 BattleEnded(OPPONENT_FORFEITED) → BattleJudged(FR-094). */
    public void forfeit(long memberId, long battleId) {
        transactions.retryingRun(
                () -> {
                    Battle battle = participantBattle(memberId, battleId);
                    LocalDateTime now = LocalDateTime.now(clock);
                    battle.forfeit(memberId, now);
                    battleRepository.save(battle);
                    BattleEnded ended =
                            new BattleEnded(battleId, BattleEnded.Status.OPPONENT_FORFEITED);
                    battle.rivalOf(memberId)
                            .map(BattleParticipant::getMemberId)
                            .ifPresent(
                                    rival ->
                                            AfterCommit.run(
                                                    () -> battleMessenger.send(rival, ended)));
                    Judgement judgement = battle.getJudgement().orElseThrow();
                    eventPublisher.publish(
                            new BattleJudged(battleId, battle.getType(), judgement, now));
                });
    }

    /** 참가자만. 보류면 "보류" + 기록이 없는 본인에게 재시도 안내. 아니면 판정, 비교표, 이번 주 전적(FR-093, FR-108). */
    @Transactional(readOnly = true)
    public BattleResultView result(long memberId, long battleId) {
        Battle battle = participantBattle(memberId, battleId);
        BattleParticipant me = battle.participant(memberId).orElseThrow();
        Optional<Judgement> judgement =
                battle.getStatus() == BattleStatus.FINISHED
                        ? battle.getJudgement()
                        : Optional.empty();
        boolean pending = judgement.isEmpty();
        Verdict verdict = judgement.flatMap(j -> j.verdictOf(Side.member(memberId))).orElse(null);
        WeeklyRecord weekly =
                battleRepository.weeklyRecord(memberId, Week.of(LocalDateTime.now(clock)));
        return new BattleResultView(
                battleId,
                battle.getType(),
                pending,
                pending && me.getVerified().isEmpty(),
                verdict,
                me.getReachElapsed().orElse(null),
                opponentReach(battle, memberId).orElse(null),
                judgement.map(Judgement::corrected).orElse(false),
                comparison(battle, me),
                weekly,
                me.isNewRecord() && verdict == Verdict.WIN,
                OpponentNames.of(battle, memberId, rivalNames(battle, memberId)),
                me.getRecordId().orElse(null));
    }

    private Map<Long, MemberSummary> rivalNames(Battle battle, long memberId) {
        return battle.rivalOf(memberId)
                .map(rival -> memberQueryService.summaries(List.of(rival.getMemberId())))
                .orElse(Map.of());
    }

    private Optional<Duration> opponentReach(Battle battle, long memberId) {
        Optional<OpponentRef> record = battle.getOpponent();
        if (record.isPresent()) {
            return Optional.of(record.get().arrivalElapsed());
        }
        return battle.rivalOf(memberId).flatMap(BattleParticipant::getReachElapsed);
    }

    private ComparisonTable comparison(Battle battle, BattleParticipant me) {
        Optional<String> mine = me.getRecordId();
        if (mine.isEmpty()) {
            return new ComparisonTable(List.of());
        }
        return ComparisonTable.of(
                battle.getType(), runQueryService.comparisonOf(mine.get()), theirs(battle, me), me);
    }

    private ComparisonValues theirs(Battle battle, BattleParticipant me) {
        Optional<ComparisonValues> record = battle.opponentValues();
        if (record.isPresent()) {
            return me.maxTrail().map(record.get()::withMaxLead).orElse(record.get());
        }
        Optional<BattleParticipant> rival = battle.rivalOf(me.getMemberId());
        Optional<String> rivalRecord = rival.flatMap(BattleParticipant::getRecordId);
        if (rivalRecord.isEmpty()) {
            return ComparisonValues.undisclosed();
        }
        try {
            return ComparisonValues.ofRival(
                    runQueryService.comparisonOf(rivalRecord.get()), rival.orElseThrow());
        } catch (BusinessRuleException removed) {
            return ComparisonValues.undisclosed(); // 상대가 탈퇴해 기록이 지워졌다(FR-021)
        }
    }

    private Battle participantBattle(long memberId, long battleId) {
        Battle battle =
                battleRepository
                        .findById(battleId)
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_NOT_FOUND));
        if (!battle.isParticipant(memberId)) {
            throw new PermissionDeniedException("대결 참가자가 아니다");
        }
        return battle;
    }
}
