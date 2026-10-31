package com.a207.runkk.runimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.a207.runkk.common.DateRange;
import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.FeatureToggles;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.running.CardKind;
import com.a207.runkk.running.RunQueryService;
import com.a207.runkk.running.RunRecordDeleted;
import com.a207.runkk.running.RunSampleRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/**
 * v0.14 Runimal(D-245~D-247): 유형 비율 스냅숏·재계산, 16유형 목록·상세, 확정 이력, 동행 축 배틀런 비율(CREW_RELEASED 기준).
 * test.md 12.2.5 TC-9391~TC-9416, TC-9356.
 */
@DisplayName("v0.14 Runimal 유형 비율·확정 이력·동행 축")
class RunimalV014Test {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 0, 0);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 인메모리 유형 비율 저장소. */
    static final class Shares implements RunimalTypeShareRepository {
        final Map<RunimalType, RunimalTypeShare> rows = new EnumMap<>(RunimalType.class);
        final Map<RunimalType, Integer> confirmed = new EnumMap<>(RunimalType.class);
        int replaceCalls;

        @Override
        public void replaceAll(List<RunimalTypeShare> shares) {
            replaceCalls++;
            rows.clear();
            shares.forEach(s -> rows.put(s.type(), s));
        }

        @Override
        public Map<RunimalType, RunimalTypeShare> findAll() {
            return Map.copyOf(rows);
        }

        @Override
        public Map<RunimalType, Integer> countConfirmedByType() {
            return Map.copyOf(confirmed);
        }
    }

    @ParameterizedTest(name = "TC-9391~9394 {0}/{1} → {2}")
    @CsvSource({
        "0,0,", "0,100,", "1,3,33", "2,3,67", "1,8,13", "1,200,1", "1,201,0", "3,8,38", "5,5,100"
    })
    @DisplayName("TC-9391~TC-9394 percent: 확정자 0이면 비어 있고 반올림(0.5 올림)")
    void tc9391_percent(int count, int total, Integer expected) {
        OptionalInt percent =
                RunimalTypeShare.of(RunimalType.LONE_WOLF, count, total, NOW).percent();
        if (expected == null) {
            assertThat(percent).isEmpty();
        } else {
            assertThat(percent).hasValue(expected);
        }
    }

    private final Shares shares = new Shares();
    private final RunimalServicesTest.Profiles profiles = new RunimalServicesTest.Profiles();
    private final RunimalTypeService types =
            new RunimalTypeService(
                    profiles, shares, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    @Test
    @DisplayName("TC-9395·TC-9398 재계산: 16행, A 3명 75%·B 1명 25%·나머지 비어 있음, 두 번 돌려도 같다")
    void tc9395_recompute() {
        shares.confirmed.put(RunimalType.LONE_WOLF, 3);
        shares.confirmed.put(RunimalType.WILD_HORSE, 1);
        types.recomputeShares(NOW);
        assertThat(shares.rows).hasSize(16);
        assertThat(shares.rows.get(RunimalType.LONE_WOLF).percent()).hasValue(75);
        assertThat(shares.rows.get(RunimalType.LONE_WOLF).totalConfirmed()).isEqualTo(4);
        assertThat(shares.rows.get(RunimalType.WILD_HORSE).percent()).hasValue(25);
        assertThat(shares.rows.get(RunimalType.MORNING_SPARROW).percent()).isEmpty();
        assertThat(shares.rows.get(RunimalType.MORNING_SPARROW).computedAt()).isEqualTo(NOW);

        types.recomputeShares(NOW.plusDays(1));
        assertThat(shares.rows).hasSize(16);
        assertThat(shares.rows.get(RunimalType.LONE_WOLF).percent()).hasValue(75);
        assertThat(shares.rows.get(RunimalType.LONE_WOLF).computedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("TC-9396 확정자가 없으면 16행 모두 0·비율 없음(0 나누기 없음)")
    void tc9396_noneConfirmed() {
        types.recomputeShares(NOW);
        assertThat(shares.rows.values())
                .hasSize(16)
                .allMatch(
                        s ->
                                s.confirmedCount() == 0
                                        && s.totalConfirmed() == 0
                                        && s.percent().isEmpty());
    }

    @Test
    @DisplayName(
            "TC-9399 상세: 특징 3줄(16유형 모두 빈 줄 없음), 확정자 0이면 sharePercent null, 계산 전이면 computedAt null")
    void tc9399_detail() {
        for (RunimalType t : RunimalType.values()) {
            assertThat(t.traits()).as(t.name()).hasSize(3).allMatch(line -> !line.isBlank());
        }
        assertThat(RunimalType.COMMUTER_TURTLE.traits())
                .containsExactly(
                        "혼자 정해 둔 코스를 반복해서 달려요", "5km 이상 긴 거리를 편하게 소화해요", "비슷한 요일·시간대에 꾸준히 기록을 남겨요");
        RunimalTypeDetail before = types.detail(RunimalType.LONE_WOLF);
        assertThat(before.sharePercent()).isNull();
        assertThat(before.computedAt()).isNull();

        shares.confirmed.put(RunimalType.LONE_WOLF, 1);
        types.recomputeShares(NOW);
        assertThat(types.detail(RunimalType.LONE_WOLF).sharePercent()).isEqualTo(100);
        assertThat(types.detail(RunimalType.WILD_HORSE).sharePercent()).isNull();
        assertThat(types.detail(RunimalType.WILD_HORSE).computedAt()).isEqualTo(NOW);
    }

    private static RunimalProfile confirmedProfile(long memberId, RunimalType... confirmations) {
        RunimalProfile profile = RunimalProfile.create(memberId);
        int ordinal = 0;
        for (RunimalType t : confirmations) {
            ordinal += 10;
            profile.onValidRun(ordinal);
            Map<RunimalAxis, Double> ratios = new EnumMap<>(RunimalAxis.class);
            for (RunimalAxis axis : RunimalAxis.values()) {
                ratios.put(axis, 0.5);
            }
            profile.confirm(t, new AxisRatios(ratios), NOW);
        }
        return profile;
    }

    @Test
    @DisplayName("TC-9401 확정 이력: A → B → A 확정이면 {A, B}, 사본을 바꿔도 내부 값 그대로")
    void tc9401_confirmedHistory() {
        RunimalProfile profile =
                confirmedProfile(
                        1, RunimalType.LONE_WOLF, RunimalType.WILD_HORSE, RunimalType.LONE_WOLF);
        assertThat(profile.confirmedHistory())
                .containsExactlyInAnyOrder(RunimalType.LONE_WOLF, RunimalType.WILD_HORSE);
        assertThat(profile.hasEverConfirmed(RunimalType.WILD_HORSE)).isTrue();
        assertThat(profile.hasEverConfirmed(RunimalType.SKY_EAGLE)).isFalse();
        assertThat(profile.getConfirmedType()).contains(RunimalType.LONE_WOLF);
        Set<RunimalType> copy = profile.confirmedHistory();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> copy.add(RunimalType.SKY_EAGLE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("TC-9400·TC-9402 목록: 지금 유형과 확정받은 적 있는 유형만 everConfirmed, 확정 전이면 current null")
    void tc9400_catalog() {
        profiles.save(confirmedProfile(1, RunimalType.WILD_HORSE, RunimalType.LONE_WOLF));
        RunimalTypeCatalog catalog = types.catalog(1);
        assertThat(catalog.current()).isEqualTo(RunimalType.LONE_WOLF);
        assertThat(catalog.types()).hasSize(16);
        assertThat(catalog.types())
                .filteredOn(RunimalTypeCatalog.Entry::everConfirmed)
                .extracting(RunimalTypeCatalog.Entry::type)
                .containsExactlyInAnyOrder(RunimalType.WILD_HORSE, RunimalType.LONE_WOLF);

        RunimalTypeCatalog fresh = types.catalog(2);
        assertThat(fresh.current()).isNull();
        assertThat(fresh.types()).noneMatch(RunimalTypeCatalog.Entry::everConfirmed);
    }

    private static RunSample.SampleRun sampleRun(int i, boolean crew, boolean battle) {
        return new RunSample.SampleRun(
                "r" + i, NOW.minusDays(i), Distance.ofKm(5), null, crew, battle);
    }

    private static double togetherRatio(List<RunSample.SampleRun> lastMonth) {
        RunSample sample = new RunSample(lastMonth, lastMonth);
        return 1
                - new CompanionAxisEvaluator()
                        .ratioOfA(sample, RunimalJudgeTest.RULE)
                        .orElseThrow();
    }

    private static AxisLean companionLean(List<RunSample.SampleRun> lastMonth) {
        return RunimalAxis.COMPANION.lean(1 - togetherRatio(lastMonth), RunimalJudgeTest.RULE);
    }

    private static List<RunSample.SampleRun> runs(int total, int battle, int crew, int both) {
        List<RunSample.SampleRun> runs = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            boolean isBoth = i < both;
            boolean isBattle = isBoth || (i >= both && i < both + battle);
            boolean isCrew = isBoth || (i >= both + battle && i < both + battle + crew);
            runs.add(sampleRun(i, isCrew, isBattle));
        }
        return runs;
    }

    @ParameterizedTest(name = "TC-9404~9407 {0}건 중 배틀런 {1}건 → {2}")
    @CsvSource({"10,4,A", "10,5,B", "10,6,B", "3,1,A", "3,2,B", "10,0,A"})
    @DisplayName("TC-9404~TC-9407·TC-9409 동행 축: 배틀런 비율 50% 이상이면 소셜런(B)")
    void tc9404_battleRunRatio(int total, int battle, AxisLean expected) {
        assertThat(companionLean(runs(total, battle, 0, 0))).isEqualTo(expected);
    }

    @Test
    @DisplayName("TC-9412·TC-9413 배틀런 3 + 크루런 2 = 50%(소셜), 겹친 기록은 한 번만 센다(4건 = 40%, 솔로)")
    void tc9412_togetherCountedOnce() {
        assertThat(togetherRatio(runs(10, 3, 2, 0))).isEqualTo(0.5);
        assertThat(companionLean(runs(10, 3, 2, 0))).isEqualTo(AxisLean.B);
        assertThat(togetherRatio(runs(10, 3, 0, 1))).isEqualTo(0.4);
        assertThat(companionLean(runs(10, 3, 0, 1))).isEqualTo(AxisLean.A);
    }

    @Test
    @DisplayName("TC-9408 RunSampleRow.battleRun: BATTLE_RUN이고 대결 ID가 있을 때만")
    void tc9408_battleRunFlag() {
        assertThat(RunSampleRow.of("a", NOW, Distance.ofKm(5), CardKind.BATTLE_RUN, 1L).battleRun())
                .isTrue();
        assertThat(RunSampleRow.of("b", NOW, Distance.ofKm(5), CardKind.SELF_RUN, 1L).battleRun())
                .isFalse();
        assertThat(RunSampleRow.of("c", NOW, Distance.ofKm(5), CardKind.CELEB_RUN, 1L).battleRun())
                .isFalse();
        assertThat(
                        RunSampleRow.of("d", NOW, Distance.ofKm(5), CardKind.BATTLE_RUN, null)
                                .battleRun())
                .isFalse();
        assertThat(RunSampleRow.of("e", NOW, Distance.ofKm(5), CardKind.FREE_RUN, null).battleRun())
                .isFalse();
    }

    @Test
    @DisplayName("TC-9410·TC-9411 RunSampleReader: CREW_RELEASED 꺼짐이면 크루런 조회 0회, 켜짐이면 1회 반영")
    void tc9410_crewReleasedSwitch() {
        RunQueryService queries = mock(RunQueryService.class);
        List<RunSampleRow> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(new RunSampleRow("r" + i, NOW.minusDays(i), Distance.ofKm(5)));
        }
        when(queries.runSamples(anyLong(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(rows);
        when(queries.runSamples(anyLong(), any(DateRange.class))).thenReturn(rows);
        CrewRunParticipationQuery crew = mock(CrewRunParticipationQuery.class);
        when(crew.linkedRecordIds(anyLong(), any()))
                .thenReturn(Set.of("r0", "r1", "r2", "r3", "r4"));
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("crew", crew);
        OperationPolicy policy = mock(OperationPolicy.class);
        RunSampleReader reader =
                new RunSampleReader(
                        queries,
                        new RunimalServicesTest.Verdicts(),
                        beans.getBeanProvider(CrewRunParticipationQuery.class),
                        policy);

        when(policy.features()).thenReturn(new FeatureToggles(true, true, false));
        RunSample closed = reader.read(1, NOW.toLocalDate());
        verify(crew, never()).linkedRecordIds(anyLong(), any());
        assertThat(togetherRatio(closed.lastMonth())).isZero();

        when(policy.features()).thenReturn(new FeatureToggles(true, true, true));
        RunSample open = reader.read(1, NOW.toLocalDate());
        verify(crew, times(1)).linkedRecordIds(anyLong(), any());
        assertThat(togetherRatio(open.lastMonth())).isEqualTo(0.5);
    }

    @Test
    @DisplayName("TC-9356 기록 삭제 사건: 그 기록의 코스 판정 행만 지운다")
    void tc9356_onRunDeleted() {
        RunimalServicesTest.Verdicts verdicts = new RunimalServicesTest.Verdicts();
        verdicts.save(new CourseVerdict("r1", 1, true, NOW));
        verdicts.save(new CourseVerdict("r2", 1, false, NOW));
        RunimalService service =
                new RunimalService(
                        profiles,
                        null,
                        null,
                        null,
                        mock(OperationPolicy.class),
                        Clock.systemDefaultZone(),
                        new RunimalTransactions(com.a207.runkk.NoTransactions.manager()),
                        mock(com.a207.runkk.running.RunEvaluationNote.class),
                        verdicts);
        service.onRunDeleted(new RunRecordDeleted("r1", 1, null, null, NOW));
        service.onRunDeleted(new RunRecordDeleted("none", 1, null, null, NOW));
        assertThat(verdicts.rows).containsOnlyKeys("r2");
    }

    @Test
    @DisplayName("TC-9416 OperationPolicy.features: CREW_RELEASED 기본 false")
    void tc9416_crewReleasedDefault() {
        assertThat(com.a207.runkk.policy.SettingKey.CREW_RELEASED.defaultValue())
                .isEqualTo("false");
        assertThat(new FeatureToggles(true, true).crewReleased()).isFalse();
    }
}
