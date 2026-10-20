package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 14장 순서 1 열거형 다형성: RecordVisibility, StandardDistance, Tier, TagOwner, AggregationPeriod,
 * DistanceUnit.
 */
class EnumPolymorphismTest {

    @ParameterizedTest
    @CsvSource({
        "PUBLIC, OWNER, true",
        "PUBLIC, SAME_CREW, true",
        "PUBLIC, OTHER, true",
        "CREW, OWNER, true",
        "CREW, SAME_CREW, true",
        "CREW, OTHER, false",
        "PRIVATE, OWNER, true",
        "PRIVATE, SAME_CREW, false",
        "PRIVATE, OTHER, false"
    })
    void recordVisibility_allows(
            RecordVisibility visibility, ViewerRelation relation, boolean expected) {
        assertThat(visibility.allows(relation)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"PUBLIC, true, true", "CREW, true, false", "PRIVATE, false, false"})
    void recordVisibility_rankingAndGhost(
            RecordVisibility visibility, boolean ranking, boolean ghost) {
        assertThat(visibility.includedInPersonalRanking()).isEqualTo(ranking);
        assertThat(visibility.ghostEligible()).isEqualTo(ghost);
    }

    @ParameterizedTest
    @CsvSource({
        "KM1, 1000, true, false, true, true",
        "KM3, 3000, true, true, true, true",
        "KM5, 5000, true, true, true, true",
        "KM10, 10000, true, true, true, true",
        "HALF, 21097, false, true, false, true",
        "FULL, 42195, false, false, false, false"
    })
    void standardDistance_flags(
            StandardDistance d,
            long meters,
            boolean random,
            boolean self,
            boolean crewRun,
            boolean recordBase) {
        assertThat(d.distance()).isEqualTo(Distance.ofMeters(meters));
        assertThat(d.isRandomBattle()).isEqualTo(random);
        assertThat(d.isSelfBattle()).isEqualTo(self);
        assertThat(d.isCrewRun()).isEqualTo(crewRun);
        assertThat(d.isRecordBase()).isEqualTo(recordBase);
    }

    @Test
    void standardDistance_nearestAtMost() {
        assertThat(StandardDistance.nearestAtMost(Distance.ofMeters(999))).isEmpty();
        assertThat(StandardDistance.nearestAtMost(Distance.ofMeters(1000)))
                .contains(StandardDistance.KM1);
        assertThat(StandardDistance.nearestAtMost(Distance.ofMeters(7500)))
                .contains(StandardDistance.KM5);
        assertThat(StandardDistance.nearestAtMost(Distance.ofMeters(50_000)))
                .contains(StandardDistance.FULL);
    }

    @Test
    void tier_rankedAndAdjacent() {
        assertThat(Tier.UNRANKED.isRanked()).isFalse();
        assertThat(Tier.BRONZE.isRanked()).isTrue();
        assertThat(Tier.BRONZE.adjacent()).containsExactly(Tier.SILVER);
        assertThat(Tier.GOLD.adjacent()).containsExactlyInAnyOrder(Tier.SILVER, Tier.PLATINUM);
        assertThat(Tier.MASTER.adjacent()).containsExactly(Tier.PLATINUM);
        assertThat(Tier.UNRANKED.adjacent()).isEmpty();
    }

    @Test
    void tier_searchLadder_sameThenAdjacentThenAllRanked() {
        Set<Tier> allRanked =
                Set.of(Tier.BRONZE, Tier.SILVER, Tier.GOLD, Tier.PLATINUM, Tier.MASTER);
        assertThat(Tier.GOLD.searchLadder())
                .containsExactly(Set.of(Tier.GOLD), Set.of(Tier.SILVER, Tier.PLATINUM), allRanked);
        assertThat(Tier.UNRANKED.searchLadder()).isEqualTo(List.of(allRanked));
    }

    @Test
    void tagOwner_allowsCategories() {
        assertThat(TagOwner.RUNNER.allows(TagCategory.GOAL)).isTrue();
        assertThat(TagOwner.RUNNER.allows(TagCategory.CREW_MOOD)).isFalse();
        assertThat(TagOwner.CREW.allows(TagCategory.CREW_MOOD)).isTrue();
    }

    @Test
    void aggregationPeriod_rangeOf() {
        LocalDate thursday = LocalDate.of(2026, 10, 22);
        assertThat(AggregationPeriod.WEEKLY.rangeOf(thursday))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)));
        assertThat(AggregationPeriod.MONTHLY.rangeOf(thursday))
                .isEqualTo(new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void distanceUnit_convert() {
        assertThat(DistanceUnit.KM.convert(Distance.ofMeters(1500))).isEqualTo(1.5);
        assertThat(DistanceUnit.MI.convert(Distance.ofMeters(1609))).isCloseTo(1.0, within(0.001));
    }

    @Test
    void itemSlot_hasFourSlots() {
        assertThat(ItemSlot.values())
                .containsExactly(ItemSlot.TOP, ItemSlot.BOTTOM, ItemSlot.SHOES, ItemSlot.HAT);
    }
}
