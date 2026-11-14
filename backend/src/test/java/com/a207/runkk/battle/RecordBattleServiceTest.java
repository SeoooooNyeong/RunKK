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
}
