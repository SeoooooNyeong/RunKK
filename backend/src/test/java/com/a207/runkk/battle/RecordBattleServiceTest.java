package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.AvatarLook;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.StandardDistance;
import com.a207.runkk.common.Tier;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import com.a207.runkk.running.BestEntry;
import com.a207.runkk.running.CurvePoint;
import com.a207.runkk.running.RunComparison;
import com.a207.runkk.running.RunCurve;
import com.a207.runkk.running.RunQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 기록 대결 후보와 시작 응답의 모습(설계 5.31 B, [임시:D-성-251]·[임시:D-성-252] ④). 나와의 싸움 상대는 그 기록을 달린 날의 내 모습이고, 모습이
 * 없는 옛 기록이면 지금 모습으로 대신한다. 유명인 상대는 모습이 없다([임시:D-성-253]).
 */
@org.junit.jupiter.api.DisplayName("battle RecordBattleService 후보·시작 응답의 모습")
class RecordBattleServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Distance FIVE_K = StandardDistance.KM5.distance();
    private static final AvatarLook THAT_DAY =
            new AvatarLook("FEMALE", Map.of(ItemSlot.TOP, 12L), 2, 80, "head:1:old");
    private static final AvatarLook TODAY =
            new AvatarLook("FEMALE", Map.of(ItemSlot.SHOES, 31L), 0, 10, "head:1:new");

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Celebrities celebrities =
            new InMemoryBattleStores.Celebrities();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final List<Long> askedCurrent = new ArrayList<>();
    private boolean currentLookFails;
    private RecordBattleService records;

    @BeforeEach
    void wire() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenReturn(Map.of(ALICE, BattleFixtures.summary(ALICE, Tier.GOLD)));
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
        OpponentLooks looks =
                new OpponentLooks(
                        memberId -> {
                            askedCurrent.add(memberId);
                            if (currentLookFails) {
                                throw new IllegalStateException("avatar down");
                            }
                            return Optional.of(TODAY);
                        },
                        runs);
        records =
                new RecordBattleService(
                        members,
                        runs,
                        celebrities,
                        battles,
                        new InMemoryBattleStores.Queue(),
                        Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL),
                        looks);
    }

    private RecordBattleCandidates.Candidate fiveK() {
        return records.selfCandidates(ALICE).candidates().stream()
                .filter(c -> c.distance().equals(FIVE_K))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("셀프런 후보는 기록의 그날 모습을 싣는다. 기록이 없는 거리의 후보는 모습이 없다")
    void 셀프런_후보는_기록의_그날_모습을_싣는다() {
        when(runs.lookOf("best")).thenReturn(Optional.of(THAT_DAY));

        assertThat(fiveK().look()).isEqualTo(THAT_DAY);
        assertThat(askedCurrent).as("그날 모습이 있으면 지금 모습을 읽지 않는다").isEmpty();
        assertThat(records.selfCandidates(ALICE).candidates())
                .filteredOn(c -> c.finishTime() == null)
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.look()).isNull());
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "그날의 나는 본인만 보므로 내 실제 성별로 그린다: 기록에 남은 표시 성별(비공개면 임의 값)을 쓰지 않는다. 성별을 입력하지 않았으면 그대로 둔다")
    void 그날의_나는_내_실제_성별로_그린다() {
        when(runs.lookOf("best")).thenReturn(Optional.of(THAT_DAY));
        OpponentLooks male =
                new OpponentLooks(
                        memberId -> Optional.of(TODAY),
                        runs,
                        memberId -> memberId == ALICE ? Optional.of("MALE") : Optional.empty());

        assertThat(male.selfLook(ALICE, "best")).isEqualTo(THAT_DAY.withGender("MALE"));
        assertThat(male.selfLook(ALICE + 1, "best")).as("성별 미입력").isEqualTo(THAT_DAY);
        when(runs.lookOf("best")).thenReturn(Optional.empty());
        assertThat(male.selfLook(ALICE, "best"))
                .as("옛 기록이라 지금 모습으로 대신할 때도")
                .isEqualTo(TODAY.withGender("MALE"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("옛 기록이면 현재 모습으로 대신한다")
    void 옛_기록이면_현재_모습으로_대신한다() {
        when(runs.lookOf("best")).thenReturn(Optional.empty());

        assertThat(fiveK().look()).isEqualTo(TODAY);
        assertThat(askedCurrent).containsExactly(ALICE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "나와의 싸움 시작 응답은 후보와 같은 모습을 싣고, 유명인 후보와 유명인 대결 시작 응답은 모습이 null이다")
    void 시작_응답의_모습() {
        when(runs.lookOf("best")).thenReturn(Optional.of(THAT_DAY));
        CelebrityRecord coach =
                celebrities.save(
                        CelebrityRecord.register(
                                new CelebrityInput(
                                        "주코치",
                                        FIVE_K,
                                        Duration.ofMinutes(15),
                                        List.of(),
                                        "대회 공식 기록",
                                        null,
                                        null,
                                        "joo"),
                                NOW));

        assertThat(records.celebrityCandidates(ALICE, Optional.empty()).candidates())
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.look()).isNull());
        assertThat(records.startCelebrityBattle(ALICE, coach.getId()).opponentLook()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("나와의 싸움 시작 응답은 후보와 같은 그날 모습을 싣는다")
    void 나와의_싸움_시작_응답은_그날_모습을_싣는다() {
        when(runs.lookOf("best")).thenReturn(Optional.of(THAT_DAY));

        assertThat(records.startSelfBattle(ALICE, StandardDistance.KM5).opponentLook())
                .isEqualTo(THAT_DAY);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("기록의 그날 모습을 읽다 실패하면 지금 모습으로 대신해 기록 대결을 시작한다")
    void 그날_모습_읽기가_실패하면_지금_모습으로_시작한다() {
        when(runs.lookOf("best")).thenThrow(new IllegalStateException("record look down"));

        assertThat(records.startSelfBattle(ALICE, StandardDistance.KM5).opponentLook())
                .isEqualTo(TODAY);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("그날 모습도 지금 모습도 읽지 못하면 모습 없이(null) 기록 대결을 시작한다")
    void 모습_읽기가_실패해도_기록_대결을_시작한다() {
        when(runs.lookOf("best")).thenThrow(new IllegalStateException("record look down"));
        currentLookFails = true;

        RecordBattleStart start = records.startSelfBattle(ALICE, StandardDistance.KM5);

        assertThat(start.opponentLook()).isNull();
        assertThat(start.battleId()).isPositive();
        assertThat(start.track()).isNotNull();
        assertThat(battles.all()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("모습 읽기가 실패해도 셀프런 후보 조회는 정상 응답하고 그 후보의 모습만 null이다")
    void 모습_읽기가_실패해도_후보를_돌려준다() {
        when(runs.lookOf("best")).thenThrow(new IllegalStateException("record look down"));
        currentLookFails = true;

        assertThat(fiveK().finishTime()).isNotNull();
        assertThat(fiveK().look()).isNull();
    }

    @Test
    @org.junit.jupiter.api.DisplayName(
            "모습 읽기 자리가 다 차면 기다리지 않고 모습 없이(null) 돌려준다: 연결을 쥔 채 두 번째 연결을 기다리며 멈추지 않는다")
    void 모습_읽기_자리가_없으면_기다리지_않는다() throws Exception {
        java.util.concurrent.CountDownLatch reading = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        OpponentLooks one =
                new OpponentLooks(
                        memberId -> {
                            reading.countDown();
                            try {
                                release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return Optional.of(TODAY);
                        },
                        runs,
                        5);
        when(runs.lookOf("best")).thenReturn(Optional.empty());
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<AvatarLook> first =
                    pool.submit(() -> one.selfLook(ALICE, "best"));
            assertThat(reading.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            assertThat(one.selfLook(ALICE, "best")).as("자리 1개가 차 있다").isNull();

            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(TODAY);
            assertThat(one.selfLook(ALICE, "best")).as("자리가 비면 다시 읽는다").isEqualTo(TODAY);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
