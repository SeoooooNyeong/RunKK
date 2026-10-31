package com.a207.runkk.battle;

import static com.a207.runkk.battle.BattleFixtures.ALICE;
import static com.a207.runkk.battle.BattleFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.Distance;
import com.a207.runkk.common.Pace;
import com.a207.runkk.common.ReasonCode;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 워치 상대 기록 미리 받기(D-377, FR-260, SQ-46): RecordBattleService.recordTracks. 대결을 만들지 않는다. */
@DisplayName("RecordBattleService.recordTracks: 워치 셀프·셀럽 상대 기록")
class RecordTracksTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Distance ONE_K = StandardDistance.KM1.distance();
    private static final Distance FIVE_K = StandardDistance.KM5.distance();
    private static final Distance TEN_K = StandardDistance.KM10.distance();

    private final InMemoryBattleStores.Battles battles = new InMemoryBattleStores.Battles();
    private final InMemoryBattleStores.Celebrities celebrities =
            new InMemoryBattleStores.Celebrities();
    private final InMemoryBattleStores.Queue queue = new InMemoryBattleStores.Queue();
    private final MemberQueryService members = mock(MemberQueryService.class);
    private final RunQueryService runs = mock(RunQueryService.class);
    private final Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private RecordBattleService records;

    @BeforeEach
    void wire() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(true);
        when(members.summaries(anyCollection()))
                .thenReturn(Map.of(ALICE, BattleFixtures.summary(ALICE, Tier.GOLD)));
        records = new RecordBattleService(members, runs, celebrities, battles, queue, clock);
    }

    /** 그 거리 최고 기록 하나(기록 ID = "best-미터"), 표본은 시작·중간·도착 세 점. */
    private void best(StandardDistance d, Duration time, LocalDate on) {
        String id = "best-" + d.distance().meters();
        when(runs.best(ALICE, d))
                .thenReturn(Optional.of(new BestEntry(id, time.toMillis(), null, on, false)));
        when(runs.hasCurve(id)).thenReturn(true);
        when(runs.comparisonOf(id))
                .thenReturn(new RunComparison(time, new Pace(300), new Pace(290)));
        when(runs.curve(id, d.distance()))
                .thenReturn(
                        new RunCurve(
                                List.of(
                                        new CurvePoint(Duration.ZERO, Distance.ZERO),
                                        new CurvePoint(
                                                time.dividedBy(2),
                                                Distance.ofMeters(d.distance().meters() / 2)),
                                        new CurvePoint(time, d.distance())),
                                d.distance()));
    }

    private CelebrityRecord celebrity(
            String name, Distance d, Duration finish, List<TrackSample> splits, String figure) {
        return celebrities.save(
                CelebrityRecord.register(
                        new CelebrityInput(name, d, finish, splits, "대회 공식 기록", null, null, figure),
                        NOW));
    }

    @Test
    @DisplayName("SELF: 기록 있는 거리만 거리 오름차순, 표시명 \"MM/dd의 나\", 셀럽 칸은 null")
    void self_onlyDistancesWithRecord_sorted() {
        best(StandardDistance.KM5, Duration.ofMinutes(25), LocalDate.of(2026, 10, 3));
        best(StandardDistance.KM1, Duration.ofMinutes(4), LocalDate.of(2026, 9, 30));

        List<RecordTrack> tracks = records.recordTracks(ALICE, BattleType.SELF);

        assertThat(tracks).extracting(RecordTrack::distance).containsExactly(ONE_K, FIVE_K);
        RecordTrack five = tracks.get(1);
        assertThat(five.type()).isEqualTo(BattleType.SELF);
        assertThat(five.opponentName().value()).isEqualTo("10/03의 나");
        assertThat(five.finishTime()).isEqualTo(Duration.ofMinutes(25));
        assertThat(five.celebrityId()).isNull();
        assertThat(five.source()).isNull();
        assertThat(five.figureKey()).isNull();
        assertThat(five.estimated()).isFalse();
        assertThat(five.track().arrivalTime(FIVE_K)).contains(Duration.ofMinutes(25));
        assertThat(battles.all()).isEmpty(); // 대결 레코드를 만들지 않는다
    }

    @Test
    @DisplayName("SELF: track은 POST /battles/self(startSelfBattle)가 주는 것과 같다")
    void self_trackMatchesStartSelfBattle() {
        best(StandardDistance.KM5, Duration.ofMinutes(25), LocalDate.of(2026, 10, 3));

        RecordTrack track = records.recordTracks(ALICE, BattleType.SELF).get(0);
        RecordBattleStart start = records.startSelfBattle(ALICE, StandardDistance.KM5);

        assertThat(track.track()).isEqualTo(start.track());
        assertThat(track.opponentName()).isEqualTo(start.opponentName());
    }

    @Test
    @DisplayName("SELF: 경로가 파기돼 표를 만들 수 없는 거리는 빼고 나머지는 준다")
    void self_skipsPurgedRoute() {
        best(StandardDistance.KM5, Duration.ofMinutes(25), LocalDate.of(2026, 10, 3));
        best(StandardDistance.KM10, Duration.ofMinutes(55), LocalDate.of(2026, 10, 4));
        when(runs.hasCurve("best-10000")).thenReturn(false);
        when(runs.curve("best-10000", TEN_K))
                .thenThrow(new BusinessRuleException(ReasonCode.ROUTE_PURGED));

        assertThat(records.recordTracks(ALICE, BattleType.SELF))
                .extracting(RecordTrack::distance)
                .containsExactly(FIVE_K);
    }

    @Test
    @DisplayName("SELF: 기록이 하나도 없으면 빈 목록, 위치 동의가 없어도 읽을 수 있다")
    void self_emptyWithoutRecords_andNoConsentNeeded() {
        when(members.hasConsent(anyLong(), eq(ConsentItem.LOCATION))).thenReturn(false);

        assertThat(records.recordTracks(ALICE, BattleType.SELF)).isEmpty();
    }

    @Test
    @DisplayName("CELEBRITY: 공개 셀럽 기록 전부, 거리 → 완주 시간 오름차순, 출처·그림 키·균등 페이스 표시")
    void celebrity_allSortedWithEstimatedUniformPace() {
        Duration slow = Duration.ofMinutes(40);
        CelebrityRecord tenK = celebrity("느린전설", TEN_K, slow, List.of(), null);
        CelebrityRecord fiveSlow =
                celebrity(
                        "구간전설",
                        FIVE_K,
                        Duration.ofMinutes(16),
                        List.of(
                                new TrackSample(Duration.ofMinutes(8), Distance.ofMeters(2_500)),
                                new TrackSample(Duration.ofMinutes(16), FIVE_K)),
                        "legend");
        CelebrityRecord fiveFast =
                celebrity("빠른전설", FIVE_K, Duration.ofMinutes(15), List.of(), null);

        List<RecordTrack> tracks = records.recordTracks(ALICE, BattleType.CELEBRITY);

        assertThat(tracks)
                .extracting(RecordTrack::celebrityId)
                .containsExactly(fiveFast.getId(), fiveSlow.getId(), tenK.getId());
        RecordTrack estimated = tracks.get(0);
        assertThat(estimated.type()).isEqualTo(BattleType.CELEBRITY);
        assertThat(estimated.estimated()).isTrue();
        assertThat(estimated.track())
                .isEqualTo(OpponentTrack.uniform(FIVE_K, Duration.ofMinutes(15)).cut(FIVE_K));
        assertThat(estimated.source().label()).isEqualTo("출처: 대회 공식 기록");
        RecordTrack splits = tracks.get(1);
        assertThat(splits.estimated()).isFalse();
        assertThat(splits.figureKey()).isEqualTo("legend");
        assertThat(splits.opponentName().value()).isEqualTo("구간전설");
        assertThat(splits.finishTime()).isEqualTo(Duration.ofMinutes(16));
        assertThat(battles.all()).isEmpty();
    }

    @Test
    @DisplayName("CELEBRITY: track은 POST /battles/celebrity(startCelebrityBattle)가 주는 것과 같다")
    void celebrity_trackMatchesStartCelebrityBattle() {
        CelebrityRecord c = celebrity("전설", FIVE_K, Duration.ofMinutes(15), List.of(), "fig");

        RecordTrack track = records.recordTracks(ALICE, BattleType.CELEBRITY).get(0);
        RecordBattleStart start = records.startCelebrityBattle(ALICE, c.getId());

        assertThat(track.track()).isEqualTo(start.track());
        assertThat(track.opponentName()).isEqualTo(start.opponentName());
        assertThat(track.figureKey()).isEqualTo(start.figureKey());
        assertThat(track.estimated()).isEqualTo(start.estimated());
    }

    @Test
    @DisplayName("SELF·CELEBRITY가 아닌 종류(RANDOM·GHOST·FRIEND)는 IllegalArgumentException(400)")
    void otherKinds_areRejected() {
        for (BattleType kind : List.of(BattleType.RANDOM, BattleType.GHOST, BattleType.FRIEND)) {
            assertThatThrownBy(() -> records.recordTracks(ALICE, kind))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
