package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.live.BattleEnded;
import com.a207.runkk.battle.live.BattleGuidance;
import com.a207.runkk.battle.live.BattleStart;
import com.a207.runkk.battle.live.CountdownStarted;
import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.battle.live.OpponentProgress;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.DomainEvent;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ProgressReport;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.GhostOwner;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.notification.NotificationRequest;
import com.a207.runkk.notification.NotificationService;
import com.a207.runkk.notification.NotificationType;
import com.a207.runkk.policy.MatchCondition;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.OwnedRun;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordSaved;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** battle 응용 서비스(14장 단위 마지막 단계: 인메모리 저장소·가짜 메신저·고정 시계). 시퀀스 SQ-03~SQ-07. */
@org.junit.jupiter.api.DisplayName("battle 응용 서비스")
class BattleServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = BattleFixtures.NOW;

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final InMemoryBattleStores.Sessions sessions = new InMemoryBattleStores.Sessions();
    private final InMemoryBattleStores.Virtuals virtuals = new InMemoryBattleStores.Virtuals();
    private final InMemoryBattleStores.Messenger messenger = new InMemoryBattleStores.Messenger();
    private final List<DomainEvent> events = new ArrayList<>();
    private final DomainEventPublisher publisher = events::add;
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final MemberSettingService settings = mock(MemberSettingService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final BattleTransactions tx = InMemoryBattleStores.transactions();
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private BattleStartService startService;
    private Matchmaker matchmaker;
    private LiveBattleService liveService;
    private BattleClockService clockService;
    private BattleResultService resultService;

    @BeforeEach
    void wire() {
        when(policy.matchCondition()).thenReturn(new MatchCondition(0, Duration.ofSeconds(15)));
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(settings.battleSettingsOf(anyLong()))
                .thenReturn(com.a207.runkk.member.BattleSettings.defaults());
        when(members.summaries(anyCollection()))
                .thenAnswer(
                        inv -> {
                            Map<Long, com.a207.runkk.member.MemberSummary> map =
                                    new java.util.HashMap<>();
                            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                                long memberId = (Long) id;
                                map.put(memberId, BattleFixtures.summary(memberId, Tier.GOLD));
                            }
                            return map;
                        });
        startService =
                new BattleStartService(battles, members, runs, messenger, sessions, clock, tx);
        GhostMatcher ghost =
                new GhostMatcher(members, runs, virtuals, battles, startService, new Random(3));
        matchmaker = new Matchmaker(queue, battles, startService, ghost, policy, tx, messenger);
        liveService = new LiveBattleService(sessions, battles, messenger, notifications, tx);
        clockService =
                new BattleClockService(
                        battles, startService, sessions, messenger, notifications, publisher, tx);
        resultService =
                new BattleResultService(
                        battles, runs, members, notifications, publisher, messenger, clock, tx);
    }

    private MatchTicket waiting(long memberId, LocalDateTime since) {
        MatchTicket ticket = MatchTicket.open(memberId, Tier.GOLD, StandardDistance.KM5, since);
        queue.enqueue(ticket);
        return ticket;
    }

    /** 앨리스·밥 랜덤 대결을 성사시키고 출발시킨 뒤 대결을 돌려준다. */
    private Battle startedRandom() {
        waiting(ALICE, NOW);
        waiting(BOB, NOW.plusSeconds(1));
        matchmaker.tick(NOW.plusSeconds(2));
        Battle battle = battles.all().get(0);
        clockService.tick(battle.getStartAt().orElseThrow());
        return battle;
    }

    private static ProgressReport at(long meters, long seconds) {
        return new ProgressReport(Distance.ofMeters(meters), Duration.ofSeconds(seconds), NOW);
    }

    private static RunRecordSaved saved(
            long memberId, Battle battle, String recordId, boolean valid) {
        return new RunRecordSaved(
                recordId,
                memberId,
                NOW,
                battle.getDistance(),
                Duration.ofMinutes(25),
                valid,
                valid ? 38.0 : null,
                RunMode.BATTLE,
                battle.getId(),
                null,
                true,
                1,
                NOW);
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("MatchingService (srs.md 6.3 4.1)")
    class Matching {

        private MatchingService matching() {
            return new MatchingService(members, settings, queue, battles, clock);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "거리 오류·위치 동의 없음·언랭·이미 대기 중을 거부하고, 통과하면 대기 등록 + 기본 대결 거리 기억")
        void requestMatch_decisionTable() {
            MatchingService matching = matching();
            assertThatThrownBy(() -> matching.requestMatch(ALICE, StandardDistance.HALF))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.BATTLE_DISTANCE_INVALID);
            when(members.hasConsent(9L, ConsentItem.LOCATION)).thenReturn(false);
            assertThatThrownBy(() -> matching.requestMatch(9L, StandardDistance.KM5))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.LOCATION_CONSENT_REQUIRED);
            when(members.summaries(List.of(8L)))
                    .thenReturn(Map.of(8L, BattleFixtures.summary(8L, Tier.UNRANKED)));
            assertThatThrownBy(() -> matching.requestMatch(8L, StandardDistance.KM5))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.RANKED_ONLY);

            assertThat(matching.requestMatch(ALICE, StandardDistance.KM5).waiting()).isTrue();
            verify(settings).rememberBattleDistance(ALICE, StandardDistance.KM5);
            assertThatThrownBy(() -> matching.requestMatch(ALICE, StandardDistance.KM5))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "cancel: WAITING이면 대기해제, 배정됐으면 ALREADY_MATCHED, 대기열 장애면 QUEUE_UNAVAILABLE")
        void cancel_andQueueFailure() {
            MatchingService matching = matching();
            waiting(ALICE, NOW);
            assertThat(matching.cancel(ALICE).waiting()).isFalse();
            assertThat(queue.findWaiting(ALICE)).isEmpty();

            waiting(ALICE, NOW);
            waiting(BOB, NOW);
            queue.tryAssignPair(ALICE, BOB);
            assertThatThrownBy(() -> matching.cancel(ALICE))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.ALREADY_MATCHED);

            queue.breakDown();
            assertThatThrownBy(() -> matching.requestMatch(3L, StandardDistance.KM5))
                    .extracting(e -> ((BusinessRuleException) e).reason())
                    .isEqualTo(ReasonCode.QUEUE_UNAVAILABLE);
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName(
            "Matchmaker·GhostMatcher·BattleStartService (A-06, A-07, SQ-03, SQ-06)")
    class MatchmakingFlow {

        @Test
        @org.junit.jupiter.api.DisplayName(
                "같은 거리·티어 둘을 원자적으로 배정 → 랜덤 대결 → 양쪽 MatchFound·BattleStart(13초 뒤 출발)")
        void pairsAndAnnounces() {
            waiting(ALICE, NOW);
            waiting(BOB, NOW.plusSeconds(1));
            matchmaker.tick(NOW.plusSeconds(2));

            assertThat(battles.all()).hasSize(1);
            Battle battle = battles.all().get(0);
            assertThat(battle.getType()).isEqualTo(BattleType.RANDOM);
            assertThat(battle.getStartAt()).contains(NOW.plus(BattleStartService.START_DELAY));
            assertThat(queue.findWaiting(ALICE)).isEmpty();
            MatchFound found = (MatchFound) messenger.to(ALICE, "MatchFound").get(0);
            assertThat(found.opponentName()).isEqualTo(DisplayName.of("러너" + BOB));
            assertThat(found.animationSeconds()).isEqualTo(3);
            BattleStart start = (BattleStart) messenger.to(BOB, "BattleStart").get(0);
            assertThat(start.opponentTrack()).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("고스트 대결을 만들지 못하면 대기에서 빠지고 MatchFailed를 받는다")
        void ghostFailure_notifiesMatchFailed() {
            when(members.ghostEligibleOwners(any(), anyLong())).thenReturn(Map.of());
            waiting(ALICE, NOW);
            matchmaker.tick(NOW.plusSeconds(16));
            assertThat(battles.all()).isEmpty();
            assertThat(queue.findWaiting(ALICE)).isEmpty();
            assertThat(messenger.to(ALICE, "MatchFailed")).hasSize(1);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "15초를 넘긴 대기자는 고스트로: 러너 후보가 없으면 같은 티어 가상 기록, MatchFound 형식은 같다")
        void expiredTicket_getsGhostFromVirtualRecords() {
            virtuals.replaceAll(
                    List.of(
                            new VirtualRecord(
                                    Tier.GOLD,
                                    StandardDistance.KM5,
                                    "달빛여우",
                                    Duration.ofMinutes(26))));
            when(members.ghostEligibleOwners(any(), anyLong())).thenReturn(Map.of());
            waiting(ALICE, NOW);
            matchmaker.tick(NOW.plusSeconds(15));
            assertThat(battles.all()).isEmpty();

            matchmaker.tick(NOW.plusSeconds(16));
            Battle ghost = battles.all().get(0);
            assertThat(ghost.getType()).isEqualTo(BattleType.GHOST);
            assertThat(ghost.getOpponent().orElseThrow().kind()).isEqualTo(OpponentKind.VIRTUAL);
            MatchFound found = (MatchFound) messenger.to(ALICE, "MatchFound").get(0);
            assertThat(found.opponentName()).isEqualTo(DisplayName.of("달빛여우"));
            assertThat(found.opponentTier()).isEqualTo(Tier.GOLD);
            BattleStart start = (BattleStart) messenger.to(ALICE, "BattleStart").get(0);
            assertThat(start.opponentTrack()).isNotEmpty();
            assertThat(queue.findWaiting(ALICE)).isEmpty();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("고스트: 같은 티어 러너 기록이 있으면 그 기록(좌표 없는 표본)으로, 인접 티어로 넓혀 찾는다")
        void ghost_usesRunnerRecord_withLadder() {
            GhostOwner owner = new GhostOwner(7L, DisplayName.of("새벽곰"), Tier.SILVER);
            when(members.ghostEligibleOwners(eq(java.util.Set.of(Tier.GOLD)), anyLong()))
                    .thenReturn(Map.of());
            when(members.ghostEligibleOwners(
                            argThat(tiers -> tiers != null && tiers.contains(Tier.SILVER)),
                            anyLong()))
                    .thenReturn(Map.of(7L, owner));
            when(runs.validRunsReaching(eq(StandardDistance.KM5), anyCollection()))
                    .thenReturn(List.of(new OwnedRun("rec-7", 7L)));
            when(runs.comparisonOf("rec-7"))
                    .thenReturn(
                            new RunComparison(
                                    Duration.ofMinutes(24), new Pace(288), new Pace(280)));
            when(runs.curve(eq("rec-7"), any()))
                    .thenReturn(
                            new RunCurve(
                                    List.of(
                                            new CurvePoint(
                                                    Duration.ofMinutes(12),
                                                    Distance.ofMeters(2_500)),
                                            new CurvePoint(
                                                    Duration.ofMinutes(24),
                                                    Distance.ofMeters(5_000))),
                                    Distance.ofMeters(5_000)));
            waiting(ALICE, NOW);
            matchmaker.tick(NOW.plusSeconds(16));

            Battle ghost = battles.all().get(0);
            OpponentRef ref = ghost.getOpponent().orElseThrow();
            assertThat(ref.kind()).isEqualTo(OpponentKind.RUN_RECORD);
            assertThat(ref.displayName()).isEqualTo(DisplayName.of("새벽곰"));
            assertThat(ref.arrivalElapsed()).isEqualTo(Duration.ofMinutes(24));
            assertThat(ghost.opponentValues().orElseThrow().averagePace()).isEqualTo(new Pace(288));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("출발 시각이 되면 IN_PROGRESS, 랜덤 대결은 실시간 세션을 연다")
        void startDue_opensSession() {
            Battle battle = startedRandom();
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
            assertThat(sessions.activeBattleIds()).containsExactly(battle.getId());
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("LiveBattleService·BattleClockService (A-09, A-11, SQ-04)")
    class LiveAndClock {

        @Test
        @org.junit.jupiter.api.DisplayName("진행값은 상대에게 중계되고, 10m 넘게 선두가 바뀌면 추월·역전 안내")
        void relayAndOvertake() {
            Battle battle = startedRandom();
            long id = battle.getId();
            liveService.onProgress(ALICE, id, at(100, 30), NOW);
            liveService.onProgress(BOB, id, at(80, 30), NOW);
            liveService.onProgress(BOB, id, at(115, 31), NOW);

            OpponentProgress toBob =
                    (OpponentProgress) messenger.to(BOB, "OpponentProgress").get(0);
            assertThat(toBob.opponentDistance()).isEqualTo(Distance.ofMeters(100));
            assertThat(toBob.connection()).isEqualTo(OpponentProgress.Connection.CONNECTED);
            BattleGuidance overtook = (BattleGuidance) messenger.to(BOB, "BattleGuidance").get(0);
            assertThat(overtook.kind()).isEqualTo(BattleGuidance.Kind.OVERTOOK);
            assertThat(((BattleGuidance) messenger.to(ALICE, "BattleGuidance").get(0)).kind())
                    .isEqualTo(BattleGuidance.Kind.OVERTAKEN);
            liveService.onProgress(99L, id, at(1, 1), NOW);
            liveService.onProgress(ALICE, 999L, at(1, 1), NOW);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "첫 도착 → 모두에게 CountdownStarted, 상대에게 OPPONENT_ARRIVED 알림 → 10초 뒤 BattleEnded·판정")
        void arrivalCountdownAndClose() {
            Battle battle = startedRandom();
            long id = battle.getId();
            liveService.onProgress(ALICE, id, at(4_000, 1_200), NOW);
            liveService.onProgress(BOB, id, at(3_900, 1_200), NOW);
            liveService.onProgress(BOB, id, at(4_100, 1_210), NOW);
            LocalDateTime arrival = battle.getStartAt().orElseThrow().plusMinutes(25);
            liveService.onProgress(BOB, id, at(5_000, 1_500), arrival);

            assertThat(battle.getStatus()).isEqualTo(BattleStatus.COUNTDOWN);
            assertThat(messenger.to(ALICE, "CountdownStarted")).hasSize(1);
            assertThat(
                            ((CountdownStarted) messenger.to(BOB, "CountdownStarted").get(0))
                                    .arrivedName())
                    .isEqualTo(DisplayName.of("러너" + BOB));
            verify(notifications)
                    .notify(
                            argThat(
                                    r ->
                                            r.recipientId() == ALICE
                                                    && r.type()
                                                            == NotificationType.OPPONENT_ARRIVED));

            clockService.tick(arrival.plusSeconds(10));
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);
            assertThat(battle.participant(BOB).orElseThrow().getOvertakes()).isEqualTo(1);
            assertThat(((BattleEnded) messenger.to(ALICE, "BattleEnded").get(0)).status())
                    .isEqualTo(BattleEnded.Status.RESULT_PENDING);
            assertThat(sessions.activeBattleIds()).isEmpty();

            when(runs.timeToReach(anyString(), any()))
                    .thenReturn(Optional.of(Duration.ofMinutes(25)));
            resultService.onRunSaved(saved(BOB, battle, "b", true));
            assertThat(battle.getStatus()).isEqualTo(BattleStatus.FINISHED);
            BattleJudged judged = (BattleJudged) events.get(events.size() - 1);
            assertThat(judged.judgement().winnerSide()).contains(Side.member(BOB));
        }

        @Test
        @org.junit.jupiter.api.DisplayName("3초 넘게 진행값이 없으면 상대에게 CHECKING, 대결에 끊김 기록, 다시 오면 복구")
        void timeoutAndReconnect() {
            Battle battle = startedRandom();
            long id = battle.getId();
            LocalDateTime start = battle.getStartAt().orElseThrow();
            liveService.onProgress(ALICE, id, at(10, 3), start.plusSeconds(3));
            liveService.onProgress(BOB, id, at(10, 3), start.plusSeconds(3));
            liveService.detectTimeouts(start.plusSeconds(5));
            assertThat(messenger.to(ALICE, "OpponentProgress")).hasSize(1);

            liveService.onProgress(ALICE, id, at(30, 7), start.plusSeconds(7));
            liveService.detectTimeouts(start.plusSeconds(7));
            List<BattleMessage> toAlice = messenger.to(ALICE, "OpponentProgress");
            assertThat(((OpponentProgress) toAlice.get(toAlice.size() - 1)).connection())
                    .isEqualTo(OpponentProgress.Connection.CHECKING);
            assertThat(battle.participant(BOB).orElseThrow().getDisconnections()).hasSize(1);

            liveService.onProgress(BOB, id, at(40, 8), start.plusSeconds(8));
            assertThat(
                            battle.participant(BOB)
                                    .orElseThrow()
                                    .getDisconnections()
                                    .get(0)
                                    .getReconnectedAt())
                    .isNotNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "기록 상대 도착 시각 → CountdownStarted + 알림, 24시간 보류 만료 → BattleJudged")
        void recordOpponentArrival_andPendingExpiry() {
            Battle self =
                    battles.save(
                            Battle.againstRecord(
                                    BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(25)),
                                    NOW));
            self.start(NOW);
            LocalDateTime arrives = NOW.plusMinutes(25);
            clockService.tick(arrives);
            assertThat(self.getStatus()).isEqualTo(BattleStatus.COUNTDOWN);
            assertThat(messenger.to(ALICE, "CountdownStarted")).hasSize(1);
            verify(notifications)
                    .notify(
                            argThat(
                                    (NotificationRequest r) ->
                                            r.type() == NotificationType.OPPONENT_ARRIVED));

            clockService.tick(arrives.plusSeconds(10));
            assertThat(self.getStatus()).isEqualTo(BattleStatus.RESULT_PENDING);
            clockService.tick(arrives.plusSeconds(10).plusHours(24));
            assertThat(self.getStatus()).isEqualTo(BattleStatus.FINISHED);
            assertThat(events).hasSize(1);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "resumeLiveSessions: 진행 중 대결 세션만 남기고 끝난 대결 세션은 지운다(D-55)")
        void resume_dropsFinishedSessions() {
            Battle running = startedRandom();
            sessions.save(
                    LiveBattleSession.open(
                            999L,
                            Distance.ofMeters(1_000),
                            Map.of(1L, DisplayName.of("a"), 2L, DisplayName.of("b")),
                            NOW));
            assertThat(clockService.resumeLiveSessions(NOW)).isEqualTo(1);
            assertThat(sessions.activeBattleIds()).containsExactly(running.getId());
        }
    }

    @Nested
    @org.junit.jupiter.api.DisplayName("BattleResultService (SQ-05)")
    class Results {

        @Test
        @org.junit.jupiter.api.DisplayName(
                "포기: 상대에게 OPPONENT_FORFEITED, BattleJudged, 참가자가 아니면 권한 없음")
        void forfeit() {
            Battle battle = startedRandom();
            resultService.forfeit(ALICE, battle.getId());
            assertThat(((BattleEnded) messenger.to(BOB, "BattleEnded").get(0)).status())
                    .isEqualTo(BattleEnded.Status.OPPONENT_FORFEITED);
            assertThat(events).singleElement().isInstanceOf(BattleJudged.class);
            assertThatThrownBy(() -> resultService.forfeit(99L, battle.getId()))
                    .isInstanceOf(com.a207.runkk.common.PermissionDeniedException.class);
            assertThatThrownBy(() -> resultService.forfeit(ALICE, 12_345L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @org.junit.jupiter.api.DisplayName(
                "정정: 먼저 도착한 쪽 기록이 뒤늦게 무효로 오면 corrected + BATTLE_CORRECTED 알림")
        void correction_notifies() {
            Battle battle = startedRandom();
            LocalDateTime start = battle.getStartAt().orElseThrow();
            battle.recordArrival(Side.member(ALICE), start.plusMinutes(25));
            battle.recordArrival(Side.member(BOB), start.plusMinutes(25).plusSeconds(5));
            battle.closeCountdown(start.plusMinutes(25).plusSeconds(10));
            when(runs.timeToReach("a", battle.getDistance()))
                    .thenReturn(Optional.of(Duration.ofMinutes(25)));
            when(runs.timeToReach("b", battle.getDistance()))
                    .thenReturn(Optional.of(Duration.ofMinutes(25).plusSeconds(5)));
            when(runs.comparisonOf(anyString()))
                    .thenReturn(
                            new RunComparison(
                                    Duration.ofMinutes(25), new Pace(300), new Pace(290)));

            resultService.onRunSaved(saved(ALICE, battle, "a", true));
            resultService.onRunSaved(saved(BOB, battle, "b", true));
            assertThat(battle.getJudgement().orElseThrow().winnerSide())
                    .contains(Side.member(ALICE));
            verify(notifications, never())
                    .notify(argThat(r -> r.type() == NotificationType.BATTLE_CORRECTED));

            resultService.onRunSaved(saved(ALICE, battle, "a", false));
            assertThat(battle.getJudgement().orElseThrow().corrected()).isTrue();
            verify(notifications)
                    .notify(
                            argThat(
                                    r ->
                                            r.recipientId() == BOB
                                                    && r.type()
                                                            == NotificationType.BATTLE_CORRECTED));

            BattleResultView view = resultService.result(BOB, battle.getId());
            assertThat(view.verdict()).isEqualTo(Verdict.WIN);
            assertThat(view.corrected()).isTrue();
            assertThat(view.comparison().rows()).hasSize(4);
            assertThat(view.weekly().wins()).isEqualTo(1);
            assertThat(view.myRecordId()).isEqualTo("b");
            assertThat(view.opponentName().value()).isEqualTo("러너" + ALICE);

            BattleHistoryView history =
                    new BattleHistoryService(
                                    battles, members, new InMemoryBattleStores.Celebrities())
                            .history(
                                    BOB,
                                    new DateRange(NOW.toLocalDate(), NOW.toLocalDate()),
                                    Optional.empty());
            assertThat(history.entries().get(0).gap()).isEqualTo(Duration.ofSeconds(5));
            assertThat(history.entries().get(0).sourceLabel()).isNull();
        }

        @Test
        @org.junit.jupiter.api.DisplayName("보류 결과: 기록이 없는 본인에게 재시도 안내, 대결 ID 없는 기록은 무시")
        void pendingView_andIgnoredRecord() {
            Battle battle = startedRandom();
            LocalDateTime start = battle.getStartAt().orElseThrow();
            battle.recordArrival(Side.member(ALICE), start.plusMinutes(25));
            battle.closeCountdown(start.plusMinutes(25).plusSeconds(10));
            BattleResultView pending = resultService.result(ALICE, battle.getId());
            assertThat(pending.pending()).isTrue();
            assertThat(pending.retryUpload()).isTrue();
            assertThat(pending.comparison().rows()).isEmpty();

            RunRecordSaved free =
                    new RunRecordSaved(
                            "f",
                            ALICE,
                            NOW,
                            Distance.ofMeters(1),
                            Duration.ofSeconds(1),
                            true,
                            1.0,
                            RunMode.FREE,
                            null,
                            null,
                            false,
                            1,
                            NOW);
            resultService.onRunSaved(free);
            verify(runs, never()).timeToReach(eq("f"), any());
        }
    }
}
