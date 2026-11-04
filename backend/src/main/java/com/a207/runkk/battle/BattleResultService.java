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
import com.a207.runkk.running.RunRecordDeleted;
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
        if (found.isEmpty()
                || !found.get().isParticipant(e.memberId())
                || found.get().isCancelled()) {
            return;
        }
        Battle battle = found.get();
        Distance distance = battle.getDistance();
        Optional<Duration> reach = runQueryService.timeToReach(e.recordId(), distance);
        battle.verify(
                e.memberId(),
                new RecordCheck(
                        e.recordId(),
                        e.valid() && reach.isPresent(),
                        reach.orElse(null),
                        e.validRunOrdinal()));
        battle.getOpponentTrack().ifPresent(track -> recordGaps(battle, e, track));
        markSelfRecord(battle, e);
        boolean running = battle.getStatus().isRunning();
        Optional<Judgement> judgement = battle.judge(LocalDateTime.now(clock));
        battleRepository.save(battle);
        if (running && judgement.isPresent()) {
            endedEarly(battle);
        }
        judgement.ifPresent(j -> announce(battle, j));
        if (judgement.isEmpty() && battle.placementRound().isPresent()) {
            // 판정 없이 기록만 붙었다(유령 먼저 + 마감 뒤 업로드, 만료 뒤 늦은 기록). 판 셈이 기다리지 않게 알린다(D-512)
            eventPublisher.publish(
                    new PlacementRecordAttached(
                            battle.getId(), e.memberId(), LocalDateTime.now(clock)));
        }
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

    /**
     * Battle.forfeit → 상대에게 BattleEnded(OPPONENT_FORFEITED) → BattleJudged(FR-094). 셀프런·셀럽런도 출발했으면
     * 포기자 패로 판정해 이력에 남긴다(2026-10-30 결정, v0.14 D-228의 "진행 중 포기 = 취소"를 대신한다. 기록 없이 끝내려면 cancel). 출발
     * 전(WAITING_START, 시작 보고 전 기록 대결)이면 승패·리워드 없이 취소(CANCELLED)하고 랜덤 상대에게 같은 BattleEnded만 보낸다(배틀런
     * QA 7번 결정). 앱의 BattleEnded.Status에 취소 값이 없어 상대가 그만뒀다는 OPPONENT_FORFEITED를 쓴다.
     */
    public void forfeit(long memberId, long battleId) {
        transactions.retryingRun(
                () -> {
                    Battle battle = participantBattle(memberId, battleId);
                    LocalDateTime now = LocalDateTime.now(clock);
                    boolean cancelled = battle.cancelIfUnstarted(now); // 출발 전(배틀런 QA 7번)
                    if (!cancelled) {
                        battle.forfeit(memberId, now);
                    }
                    battleRepository.save(battle);
                    BattleEnded ended =
                            new BattleEnded(battleId, BattleEnded.Status.OPPONENT_FORFEITED);
                    battle.rivalOf(memberId)
                            .map(BattleParticipant::getMemberId)
                            .ifPresent(
                                    rival ->
                                            AfterCommit.run(
                                                    () -> battleMessenger.send(rival, ended)));
                    if (cancelled) {
                        log.info(
                                "battle_cancelled_by_forfeit battleId={} memberId={}",
                                battleId,
                                memberId);
                        return;
                    }
                    Judgement judgement = battle.getJudgement().orElseThrow();
                    eventPublisher.publish(
                            new BattleJudged(battleId, battle.getType(), judgement, now));
                });
    }

    /**
     * 셀프런·셀럽런 취소(POST /battles/{id}/cancel, 2026-10-30 결정). 출발 여부와 상관없이 승패·리워드·이력 없이 CANCELLED로 바꾸고
     * 활성 칸을 비운다(Battle.cancelByRunner). 실시간 상대가 없어 BattleEnded는 보내지 않는다. 뒤에 같은 대결 ID로 올라온 기록은 대결에
     * 붙지 않는다(applyRecord가 취소된 대결을 건너뛴다). 랜덤·고스트·친구 대결은 ALREADY_MATCHED로 거부한다(성사된 대결은 그만두기로만 끝낸다).
     * 끝난 대결은 IllegalStateException, 이미 취소된 대결은 BATTLE_NOT_FOUND다.
     */
    public void cancel(long memberId, long battleId) {
        transactions.retryingRun(
                () -> {
                    Battle battle = participantBattle(memberId, battleId);
                    if (!battle.getType().allowsCancel()) {
                        throw new BusinessRuleException(ReasonCode.ALREADY_MATCHED);
                    }
                    battle.cancelByRunner(LocalDateTime.now(clock));
                    battleRepository.save(battle);
                    log.info(
                            "battle_cancelled_by_runner battleId={} memberId={}",
                            battleId,
                            memberId);
                });
    }

    /**
     * 기록이 삭제되면(v0.14 D-232) 그 대결 참가자의 기록 ID를 비운다. 결과의 myRecordId가 null이 되고 비교값은 "공개 안 됨"이다. 판정·이력은
     * 그대로다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRunDeleted(RunRecordDeleted e) {
        if (e.battleId() == null) {
            return;
        }
        try {
            transactions.retryingRun(
                    () ->
                            battleRepository
                                    .findById(e.battleId())
                                    .ifPresent(
                                            battle -> {
                                                battle.forgetRecord(e.memberId());
                                                battleRepository.save(battle);
                                            }));
        } catch (RuntimeException failure) {
            log.error(
                    "battle_record_forget_failed battleId={} error={}",
                    e.battleId(),
                    failure.getClass().getSimpleName(),
                    failure);
        }
    }

    /**
     * 판정 시각이 [from, to)이고 회원이 이긴 대결(reward.RewardService.reconcile이 빠진 BATTLE_WIN 적립을 찾는다). 정정된 대결은
     * 지금 승자와 정정 시각이다.
     */
    @Transactional(readOnly = true)
    public List<BattleWin> winsJudgedBetween(LocalDateTime from, LocalDateTime to) {
        return battleRepository.findMemberWinsJudgedBetween(from, to);
    }

    /**
     * 참가자만. 보류면 "보류" + 기록이 없는 본인에게 재시도 안내. 아니면 판정, 비교표, 이번 주 전적(FR-093, FR-108). 내 기록 ID가 지워진
     * 기록이면(삭제 사건 수신자가 실패해 남은 경우) null로 낸다. 앱은 "기록 카드" 대신 "기록 목록"을 보인다(결정 D67).
     */
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
                me.getRecordId().filter(id -> !runQueryService.isDeleted(id)).orElse(null),
                OpponentGenders.nameFor(battle, memberId),
                battle.getOpponentFigure().orElse(null),
                placementRoundOf(battle));
    }

    static Integer placementRoundOf(Battle battle) {
        return battle.placementRound().isPresent() ? battle.placementRound().getAsInt() : null;
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
        try {
            return ComparisonTable.of(
                    battle.getType(),
                    runQueryService.comparisonOf(mine.get()),
                    theirs(battle, me),
                    me);
        } catch (BusinessRuleException removed) {
            // 내 기록이 지워졌는데 기록 ID 정리(onRunDeleted)가 아직 안 됐다(D-232). 비교표 없이 결과를 보인다
            return new ComparisonTable(List.of());
        }
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
                        .filter(b -> !b.isCancelled()) // 출발하지 못해 취소된 대결은 결과가 없다
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.BATTLE_NOT_FOUND));
        if (!battle.isParticipant(memberId)) {
            throw new PermissionDeniedException("대결 참가자가 아니다");
        }
        return battle;
    }
}
