package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.battle.live.MatchFound;
import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.InMemoryMemberRepository;
import com.a207.runkk.member.InMemoryPlacementProgressRepository;
import com.a207.runkk.member.MemberFixtures;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.member.MemberSettingService;
import com.a207.runkk.member.PlacementProgress;
import com.a207.runkk.member.PlacementRoundInput;
import com.a207.runkk.member.PlacementService;
import com.a207.runkk.member.PlacementVerdict;
import com.a207.runkk.policy.InMemoryOperationSettingRepository;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.RunQueryService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** PlacementMatcher(05-21 2.2, SQ-49): 배치고사 대결을 대기열 없이 만든다. 인메모리 저장소와 실제 PlacementService. */
@org.junit.jupiter.api.DisplayName("PlacementMatcher 배치고사 대결 생성(SQ-49)")
class PlacementMatcherTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final RandomGenerator FEMALE_PICK = () -> -1L;

    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private final InMemoryMemberRepository members = new InMemoryMemberRepository();
    private final InMemoryPlacementProgressRepository progress =
            new InMemoryPlacementProgressRepository();
    private final OperationPolicy policy =
            new OperationPolicy(new InMemoryOperationSettingRepository());
    private final PlacementService placementService =
            new PlacementService(members, progress, policy);
    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final InMemoryBattleStores.Invites invites = new InMemoryBattleStores.Invites();
    private final InMemoryBattleStores.Virtuals virtuals = new InMemoryBattleStores.Virtuals();
    private final InMemoryBattleStores.Messenger messenger = new InMemoryBattleStores.Messenger();
    private final MemberQueryService memberQuery = mock(MemberQueryService.class);
    private final MemberSettingService settings = mock(MemberSettingService.class);
    private final RunnerAvailability availability = new RunnerAvailability(queue, battles, invites);
    private PlacementMatcher matcher;
    private long runner;

    @BeforeEach
    void wire() {
        virtuals.replaceAll(
                new VirtualRecordFactory(new Random(7))
                        .generate(policy.tierBoundary(), policy.virtualRecordRule(), n -> false));
        BattleStartService starts =
                new BattleStartService(
                        battles,
                        memberQuery,
                        mock(RunQueryService.class),
                        messenger,
                        new InMemoryBattleStores.Sessions(),
                        clock,
                        InMemoryBattleStores.transactions());
        matcher =
                new PlacementMatcher(
                        placementService,
                        new PlacementOpponentFactory(virtuals, new Random(5)),
                        policy,
                        battles,
                        starts,
                        availability,
                        FEMALE_PICK);
        runner = members.save(MemberFixtures.member("배치곰")).getId();
        when(memberQuery.summaries(anyCollection())).thenReturn(java.util.Map.of());
        when(memberQuery.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(memberQuery.summaries(List.of(runner)))
                .thenReturn(
                        java.util.Map.of(runner, BattleFixtures.summary(runner, Tier.UNRANKED)));
    }

    private static double secondsPerKm(Battle battle) {
        return battle.getOpponent().orElseThrow().arrivalElapsed().toMillis()
                / 1000.0
                / battle.getDistance().km();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5773 언랭 요청: 대기열 없이 GHOST 대결(판 1, 출발 예약 13초 뒤)을 만들고 진행 행을 만든다. 상대는 점수 27 일정 페이스")
    void unrankedRequest_createsPlacementBattleWithoutQueue() {
        Battle battle = matcher.start(runner, StandardDistance.KM5, NOW).orElseThrow();

        assertThat(battle.getType()).isEqualTo(BattleType.GHOST);
        assertThat(battle.placementRound()).hasValue(1);
        assertThat(battle.getStatus()).isEqualTo(BattleStatus.WAITING_START);
        assertThat(battle.getStartAt()).contains(NOW.plusSeconds(13));
        assertThat(battle.memberIds()).containsExactly(runner);
        assertThat(queue.findWaiting(runner)).isEmpty();
        assertThat(progress.has(runner)).isTrue();
        assertThat(secondsPerKm(battle)).isCloseTo(402, within(2.0));
        assertThat(battle.opponentGenderFor(runner)).isPresent();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5776 이미 대기·대결·초대 대기 중이면 ALREADY_IN_BATTLE, 대결을 만들지 않는다")
    void busyMember_alreadyInBattle() {
        assertThat(runner).isEqualTo(BattleFixtures.ALICE);
        Battle self =
                battles.save(
                        Battle.againstRecord(
                                BattleFixtures.spec(BattleType.SELF, Duration.ofMinutes(30)), NOW));
        assertThat(self.isActive()).isTrue();

        assertThatThrownBy(() -> matcher.start(runner, StandardDistance.KM5, NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);
        assertThat(battles.all()).containsExactly(self);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5777 활성 칸 고유 제약 위반(셀프런 시작과 경합)은 ALREADY_IN_BATTLE로 바꾼다, 다른 무결성 위반은 그대로(D-515)")
    void activeSlotUniqueViolation_mapsToAlreadyInBattle() {
        battles.failNextSave(
                new DataIntegrityViolationException(
                        "dup", new SQLException("duplicate key", "23505")));
        assertThatThrownBy(() -> matcher.start(runner, StandardDistance.KM5, NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.ALREADY_IN_BATTLE);

        DataIntegrityViolationException notNull =
                new DataIntegrityViolationException("null", new SQLException("not null", "23502"));
        battles.failNextSave(notNull);
        assertThatThrownBy(() -> matcher.start(runner, StandardDistance.KM5, NOW))
                .isSameAs(notNull);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5781 2판 상대는 임시 티어(1판 점수 27 → 브론즈) 가운데 점수 28.5의 페이스")
    void secondRound_opponentAtProvisionalMidPace() {
        PlacementProgress p = PlacementProgress.start(runner);
        p.count(
                new PlacementRoundInput(
                        9,
                        27,
                        StandardDistance.KM5,
                        Distance.ofKm(5),
                        Duration.ofMinutes(33),
                        null,
                        PlacementVerdict.LOSE,
                        1,
                        NOW.minusDays(1)),
                policy.tierBoundary());
        progress.put(p);

        Battle battle = matcher.start(runner, StandardDistance.KM5, NOW).orElseThrow();

        assertThat(battle.placementRound()).hasValue(2);
        assertThat(secondsPerKm(battle)).isCloseTo(384, within(2.0));
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5782 판마다 거리를 자유롭게 고르고(1·10km) 고른 거리를 기본 대결 거리로 기억한다(브리프 6)")
    void distanceFreePerRound_savesDefault() {
        when(memberQuery.summaries(List.of(runner)))
                .thenReturn(
                        java.util.Map.of(runner, BattleFixtures.summary(runner, Tier.UNRANKED)));
        MatchingService matching =
                new MatchingService(memberQuery, settings, queue, availability, matcher, clock);

        MatchAcceptance first = matching.requestMatch(runner, StandardDistance.KM1);
        assertThat(first.distance()).isEqualTo(StandardDistance.KM1);
        assertThat(battles.findById(first.battleId()).orElseThrow().getDistance())
                .isEqualTo(Distance.ofMeters(1_000));
        verify(settings).rememberBattleDistance(runner, StandardDistance.KM1);

        battles.findById(first.battleId()).orElseThrow().cancelUnstarted(NOW.plusSeconds(1));
        MatchAcceptance next = matching.requestMatch(runner, StandardDistance.KM10);
        assertThat(battles.findById(next.battleId()).orElseThrow().getDistance())
                .isEqualTo(Distance.ofMeters(10_000));
        verify(settings).rememberBattleDistance(runner, StandardDistance.KM10);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "TC-5783 MatchFound는 placementRound를 싣고 상대 티어를 숨긴다(UNRANKED), 상대 기록 = 상대 완주 시간(J24-3)")
    void matchFound_carriesPlacementRoundAndHidesTier() {
        Battle battle = matcher.start(runner, StandardDistance.KM3, NOW).orElseThrow();

        MatchFound found = (MatchFound) messenger.to(runner, "MatchFound").get(0);
        assertThat(found.placementRound()).isEqualTo(1);
        assertThat(found.opponentTier()).isEqualTo(Tier.UNRANKED);
        assertThat(found.opponentBest())
                .isEqualTo(battle.getOpponent().orElseThrow().arrivalElapsed());
        assertThat(found.opponentGender()).isEqualTo("FEMALE");
        assertThat(messenger.to(runner, "BattleStart")).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("TC-5784 상대를 만들지 못하면(가상 기록 없음) MATCH_FAILED, 대결 없음")
    void noOpponent_matchFailed() {
        virtuals.replaceAll(List.of());

        assertThatThrownBy(() -> matcher.start(runner, StandardDistance.KM5, NOW))
                .extracting(e -> ((BusinessRuleException) e).reason())
                .isEqualTo(ReasonCode.MATCH_FAILED);
        assertThat(battles.all()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("잠근 뒤 회원이 이미 랭크 티어면 비어 있고 대결을 만들지 않는다(D-518)")
    void rankedAfterLock_isEmpty() {
        members.findById(runner).orElseThrow().confirmPlacement(Tier.GOLD);

        assertThat(matcher.start(runner, StandardDistance.KM5, NOW)).isEmpty();
        assertThat(battles.all()).isEmpty();
    }
}
