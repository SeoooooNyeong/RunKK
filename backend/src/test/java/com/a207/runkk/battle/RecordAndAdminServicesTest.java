package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.BOB;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.audit.AuditAction;
import com.a207.runkk.audit.AuditService;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberWithdrawn;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingChanged;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 기록 대결·이력·가상 기록·유명인 기록·탈퇴 포트(14장 단위 응용 서비스, SQ-07, srs.md 6.3 4.6·4.7·7.1·7.5). */
@org.junit.jupiter.api.DisplayName("기록 대결·이력·운영 서비스")
class RecordAndAdminServicesTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Distance FIVE_K = StandardDistance.KM5.distance();

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Celebrities celebrities =
            new InMemoryBattleStores.Celebrities();
    private final InMemoryBattleStores.Virtuals virtuals = new InMemoryBattleStores.Virtuals();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final OperationPolicy policy = mock(OperationPolicy.class);
    private final AuditService audit = mock(AuditService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private RecordBattleService records;

    @BeforeEach
    void wire() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenReturn(Map.of(ALICE, BattleFixtures.summary(ALICE, Tier.GOLD)));
        records = new RecordBattleService(members, runs, celebrities, battles, clock);
        when(policy.tierBoundary()).thenReturn(VirtualRecordFactoryTest.BOUNDARY);
        when(policy.virtualRecordRule()).thenReturn(VirtualRecordFactoryTest.RULE);
    }

    private void bestFiveK() {
        when(runs.best(ALICE, StandardDistance.KM5))
                .thenReturn(
                        Optional.of(
                                new BestEntry(
                                        "best",
                                        Duration.ofMinutes(25).toMillis(),
                                        null,
                                        LocalDate.of(2026, 10, 3),
                                        false)));
        when(runs.comparisonOf("best"))
                .thenReturn(
                        new RunComparison(Duration.ofMinutes(25), new Pace(300), new Pace(290)));
        when(runs.curve(eq("best"), any()))
                .thenReturn(
                        new RunCurve(
                                List.of(
                                        new CurvePoint(Duration.ZERO, Distance.ZERO),
                                        new CurvePoint(Duration.ofMinutes(25), FIVE_K)),
                                FIVE_K));
    }

    private CelebrityRecord celebrity(List<TrackSample> splits) {
        return celebrities.save(
                CelebrityRecord.register(
                        new CelebrityInput(
                                "마라톤전설", FIVE_K, Duration.ofMinutes(15), splits, "대회 공식 기록", null),
                        NOW));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("나와의 싸움 후보: 3·5·10km·하프, 기록이 없으면 \"아직 기록이 없어요\"")
    void selfCandidates() {
        bestFiveK();
        List<RecordBattleCandidates.Candidate> candidates =
                records.selfCandidates(ALICE).candidates();
        assertThat(candidates).hasSize(4);
        assertThat(candidates.get(0).displayName()).isEqualTo(RecordBattleService.NO_RECORD_YET);
        assertThat(candidates.get(1).displayName()).isEqualTo("10/03의 나");
        assertThat(candidates.get(1).finishTime()).isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "나와의 싸움 시작: 거리·동의·기록 검사 → IN_PROGRESS(시작 보고 대기) → markStarted")
    void startSelfBattle_andMarkStarted() {
        assertThatThrownBy(() -> records.startSelfBattle(ALICE, StandardDistance.KM1))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.BATTLE_DISTANCE_INVALID);
        assertThatThrownBy(() -> records.startSelfBattle(ALICE, StandardDistance.KM5))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.NO_RECORD);
        when(members.hasConsent(BOB, ConsentItem.LOCATION)).thenReturn(false);
        assertThatThrownBy(() -> records.startSelfBattle(BOB, StandardDistance.KM5))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.LOCATION_CONSENT_REQUIRED);

        bestFiveK();
        RecordBattleStart start = records.startSelfBattle(ALICE, StandardDistance.KM5);
        assertThat(start.opponentName().value()).isEqualTo("10/03의 나");
        assertThat(start.track().arrivalTime(FIVE_K)).contains(Duration.ofMinutes(25));
        Battle battle = battles.findById(start.battleId()).orElseThrow();
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.IN_PROGRESS);
        assertThat(battle.getStartAt()).isEmpty();

        assertThatThrownBy(() -> records.markStarted(BOB, start.battleId()))
                .isInstanceOf(com.a207.runkk.common.PermissionDeniedException.class);
        records.markStarted(ALICE, start.battleId());
        records.markStarted(ALICE, start.battleId()); // 앱 재시도는 무시(멱등)
        assertThat(battle.getStartAt()).contains(NOW);
        Battle random =
                battles.save(
                        Battle.random(
                                BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                                BattleFixtures.assigned(BOB, StandardDistance.KM5),
                                NOW));
        assertThatThrownBy(() -> records.markStarted(ALICE, random.getId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(battle.getOpponentArrivesAt()).contains(NOW.plusMinutes(25));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "유명인 대결: 목록·출처·추정 여부, 지워진 기록은 CELEBRITY_REMOVED, 대결 거리 = 기록 거리")
    void celebrityBattle() {
        CelebrityRecord estimated = celebrity(List.of());
        RecordBattleCandidates list =
                records.celebrityCandidates(ALICE, Optional.of(StandardDistance.KM5));
        assertThat(list.candidates())
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.estimated()).isTrue();
                            assertThat(c.source().label()).isEqualTo("공개 기록 기준 · 대회 공식 기록");
                        });
        assertThat(
                        records.celebrityCandidates(ALICE, Optional.of(StandardDistance.KM10))
                                .candidates())
                .isEmpty();

        RecordBattleStart start = records.startCelebrityBattle(ALICE, estimated.getId());
        assertThat(start.estimated()).isTrue();
        assertThat(start.distance()).isEqualTo(FIVE_K);
        assertThat(
                        battles.findById(start.battleId())
                                .orElseThrow()
                                .getOpponent()
                                .orElseThrow()
                                .displayTier())
                .isEqualTo(Tier.UNRANKED);
        assertThatThrownBy(() -> records.startCelebrityBattle(ALICE, 404L))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.CELEBRITY_REMOVED);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "CelebrityRecord.register: 출처·익명 닉네임·값·구간 검사(srs.md 6.3 7.1)")
    void celebrityRegister_validation() {
        assertThatThrownBy(
                        () ->
                                CelebrityRecord.register(
                                        new CelebrityInput(
                                                "a",
                                                FIVE_K,
                                                Duration.ofMinutes(15),
                                                null,
                                                " ",
                                                null),
                                        NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.SOURCE_REQUIRED);
        assertThatThrownBy(
                        () ->
                                CelebrityRecord.register(
                                        new CelebrityInput(
                                                " ",
                                                FIVE_K,
                                                Duration.ofMinutes(15),
                                                null,
                                                "s",
                                                null),
                                        NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ANONYMOUS_NAME_REQUIRED);
        assertThatThrownBy(
                        () ->
                                CelebrityRecord.register(
                                        new CelebrityInput(
                                                "a",
                                                Distance.ZERO,
                                                Duration.ofMinutes(1),
                                                null,
                                                "s",
                                                null),
                                        NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.VALUE_INVALID);
        List<TrackSample> shrinking =
                List.of(
                        new TrackSample(Duration.ofMinutes(5), Distance.ofMeters(2_000)),
                        new TrackSample(Duration.ofMinutes(6), Distance.ofMeters(1_900)));
        assertThatThrownBy(
                        () ->
                                CelebrityRecord.register(
                                        new CelebrityInput(
                                                "a",
                                                FIVE_K,
                                                Duration.ofMinutes(15),
                                                shrinking,
                                                "s",
                                                null),
                                        NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.SPLITS_INVALID);

        List<TrackSample> splits =
                List.of(
                        new TrackSample(Duration.ofMinutes(3), Distance.ofMeters(1_000)),
                        new TrackSample(
                                Duration.ofMinutes(5).plusSeconds(50), Distance.ofMeters(2_000)),
                        new TrackSample(Duration.ofMinutes(15), FIVE_K));
        CelebrityRecord withSplits = celebrity(splits);
        assertThat(withSplits.isEstimated()).isFalse();
        assertThat(withSplits.comparisonValues(FIVE_K).fastestKm().secondsPerKm()).isEqualTo(170);
        assertThat(celebrity(List.of()).comparisonValues(FIVE_K).fastestKmValue()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "CelebrityRecordService: 등록·삭제 감사(행위자 actorId·OPERATOR), 없으면 기록 없음")
    void celebrityService_audits() {
        CelebrityRecordService service = new CelebrityRecordService(celebrities, audit, clock);
        CelebrityResult registered =
                service.register(
                        "op@runkk",
                        new CelebrityInput(
                                "전설",
                                FIVE_K,
                                Duration.ofMinutes(14),
                                List.of(),
                                "출처",
                                "https://x"));
        assertThat(registered.outcome()).isEqualTo(CelebrityResult.Outcome.REGISTERED);
        verify(audit)
                .record(
                        argThat(
                                e ->
                                        e.action() == AuditAction.CELEBRITY_REGISTER
                                                && e.actorId().equals("op@runkk")));

        assertThat(service.delete("op@runkk", registered.celebrityId(), "권리자 요청").outcome())
                .isEqualTo(CelebrityResult.Outcome.DELETED);
        verify(audit)
                .record(
                        argThat(
                                e ->
                                        e.action() == AuditAction.CELEBRITY_DELETE
                                                && "권리자 요청".equals(e.after())));
        assertThat(celebrities.findById(registered.celebrityId())).isEmpty();
        assertThatThrownBy(() -> service.delete("op@runkk", registered.celebrityId(), "x"))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.RECORD_NOT_FOUND);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "이력: 최신순, 고스트는 RANDOM으로 보이고 탈퇴 상대는 \"탈퇴한 사용자\", 필터 RANDOM은 고스트 포함")
    void history() {
        Battle random =
                Battle.random(
                        BattleFixtures.assigned(ALICE, StandardDistance.KM5),
                        BattleFixtures.assigned(BOB, StandardDistance.KM5),
                        NOW);
        random.scheduleStart(NOW);
        random.start(NOW);
        random.forfeit(BOB, NOW.plusMinutes(1));
        battles.save(random);
        Battle ghost =
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.GHOST, Duration.ofMinutes(25)),
                        NOW.plusHours(1));
        ghost.scheduleStart(NOW.plusHours(1));
        ghost.start(NOW.plusHours(1));
        ghost.forfeit(ALICE, NOW.plusHours(1).plusMinutes(1));
        battles.save(ghost);
        when(members.summaries(anyCollection())).thenReturn(Map.of());
        CelebrityRecord legend = celebrity(List.of());
        RecordBattleStart celebrityStart = records.startCelebrityBattle(BOB, legend.getId());
        Battle celebrityBattle = battles.findById(celebrityStart.battleId()).orElseThrow();
        celebrityBattle.forfeit(BOB, NOW.plusMinutes(2));
        battles.save(celebrityBattle);

        BattleHistoryService history = new BattleHistoryService(battles, members, celebrities);
        BattleHistoryView bob =
                history.history(
                        BOB,
                        new DateRange(NOW.toLocalDate(), NOW.toLocalDate()),
                        Optional.of(BattleType.CELEBRITY));
        assertThat(bob.entries()).hasSize(1);
        assertThat(bob.entries().get(0).sourceLabel()).isEqualTo("공개 기록 기준 · 대회 공식 기록");
        assertThat(bob.entries().get(0).gap()).isNull();
        DateRange range = new DateRange(NOW.toLocalDate().minusDays(1), NOW.toLocalDate());
        BattleHistoryView all = history.history(ALICE, range, Optional.empty());
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.wins()).isEqualTo(1);
        assertThat(all.losses()).isEqualTo(1);
        assertThat(all.entries().get(0).type()).isEqualTo(BattleType.RANDOM);
        assertThat(all.entries().get(0).opponentName().value()).isEqualTo("가상러너");
        assertThat(all.entries().get(1).opponentName().value()).isEqualTo("탈퇴한 사용자");
        assertThat(history.history(ALICE, range, Optional.of(BattleType.RANDOM)).total())
                .isEqualTo(2);
        assertThat(history.history(ALICE, range, Optional.of(BattleType.SELF)).total()).isZero();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "VirtualRecordService: 100개가 아니면 만들고, TIER_BOUNDARY·VIRTUAL_RECORD_RULE 변경 때만 다시 만든다")
    void virtualRecords_regenerate() {
        VirtualRecordService service =
                new VirtualRecordService(
                        virtuals, new VirtualRecordFactory(new Random(5)), members, policy);
        service.ensureGenerated();
        assertThat(virtuals.count()).isEqualTo(100);
        String before =
                virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM5)
                        .get(0)
                        .getNickname();
        service.ensureGenerated();
        assertThat(
                        virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM5)
                                .get(0)
                                .getNickname())
                .isEqualTo(before);

        service.onOperationSettingChanged(
                new OperationSettingChanged(SettingKey.MATCH_CONDITION, NOW));
        assertThat(
                        virtuals.findByTierAndDistance(Tier.GOLD, StandardDistance.KM5)
                                .get(0)
                                .getNickname())
                .isEqualTo(before);
        service.onOperationSettingChanged(
                new OperationSettingChanged(SettingKey.VIRTUAL_RECORD_RULE, NOW));
        service.onOperationSettingChanged(
                new OperationSettingChanged(SettingKey.TIER_BOUNDARY, NOW));
        assertThat(virtuals.count()).isEqualTo(100);
        verify(members, org.mockito.Mockito.atLeast(300)).isNicknameTaken(anyString());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("탈퇴 포트: 대기·활성 대결이면 탈퇴 거부, 가상 닉네임은 예약됨, 탈퇴하면 대기열에서 뺀다")
    void withdrawalPorts() {
        ActiveBattleGuard guard = new ActiveBattleGuard(queue, battles);
        assertThat(guard.check(ALICE)).isEmpty();
        queue.enqueue(MatchTicket.open(ALICE, Tier.GOLD, StandardDistance.KM5, NOW));
        assertThat(guard.check(ALICE)).contains(ReasonCode.BATTLE_IN_PROGRESS);
        new BattleWithdrawalListener(queue).onMemberWithdrawn(new MemberWithdrawn(ALICE, NOW));
        assertThat(guard.check(ALICE)).isEmpty();
        battles.save(
                Battle.againstRecord(
                        BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(20)), NOW));
        assertThat(guard.check(ALICE)).isPresent();

        virtuals.replaceAll(
                List.of(
                        new VirtualRecord(
                                Tier.GOLD, StandardDistance.KM5, "달빛여우", Duration.ofMinutes(25))));
        VirtualNicknameChecker checker = new VirtualNicknameChecker(virtuals);
        assertThat(checker.isReserved("달빛여우")).isTrue();
        assertThat(checker.isReserved("새벽곰")).isFalse();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "BattleStartupListener: 가상 기록 생성 → 세션 이어 가기, 한쪽이 실패해도 다른 쪽은 돈다")
    void startupListener() {
        VirtualRecordService virtualService = mock(VirtualRecordService.class);
        BattleClockService clockService = mock(BattleClockService.class);
        doThrow(new IllegalStateException("db")).when(virtualService).ensureGenerated();
        new BattleStartupListener(virtualService, clockService, clock).onApplicationReady(null);
        verify(clockService).resumeLiveSessions(NOW);
    }
}
