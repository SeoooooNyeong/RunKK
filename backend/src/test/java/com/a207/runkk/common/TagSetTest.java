package com.a207.runkk.common;

import static com.a207.runkk.common.CrewTag.WEEKDAY_DAWN;
import static com.a207.runkk.common.CrewTag.WEEKDAY_EVENING;
import static com.a207.runkk.common.CrewTag.WEEKDAY_MORNING;
import static com.a207.runkk.common.CrewTag.WEEKEND_LUNCH;
import static com.a207.runkk.common.CrewTag.WEEKEND_MORNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("크루 태그 묶음 규칙")
class TagSetTest {

    @Test
    void fixedListHas8WeekdayWeekendSlots() {
        assertThat(CrewTag.values()).hasSize(8);
        assertThat(WEEKDAY_EVENING.label()).isEqualTo("평일 저녁");
        assertThat(WEEKEND_MORNING.label()).isEqualTo("주말 아침");
        assertThat(PersonalityTag.values()).as("Runimal 축 이름은 그대로 둔다").hasSize(19);
    }

    @Test
    void of_acceptsOneToThreeTagsEvenInOneDayGroup() {
        TagSet tags =
                TagSet.of(Set.of(WEEKDAY_EVENING, WEEKDAY_DAWN, WEEKEND_MORNING), TagOwner.CREW);
        assertThat(tags.isEmpty()).isFalse();
        assertThat(tags.tags()).hasSize(3);
    }

    @Test
    void of_rejectsEmptyOrMoreThanThree() {
        assertRejected(Set.of(), TagOwner.CREW);
        assertRejected(
                Set.of(WEEKDAY_MORNING, WEEKDAY_EVENING, WEEKDAY_DAWN, WEEKEND_MORNING),
                TagOwner.CREW);
    }

    @Test
    void empty_hasNoTags() {
        TagSet empty = TagSet.empty(TagOwner.CREW);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.tags()).isEmpty();
        assertThat(empty.owner()).isEqualTo(TagOwner.CREW);
    }

    @Test
    void containsAll() {
        TagSet crew =
                TagSet.of(Set.of(WEEKDAY_EVENING, WEEKDAY_DAWN, WEEKEND_MORNING), TagOwner.CREW);
        assertThat(crew.containsAll(Set.of(WEEKDAY_EVENING, WEEKEND_MORNING))).isTrue();
        assertThat(crew.containsAll(Set.of(WEEKDAY_EVENING, WEEKEND_LUNCH))).isFalse();
    }

    @Test
    void tags_isImmutableAndEqualityByValue() {
        TagSet a = TagSet.of(Set.of(WEEKDAY_EVENING), TagOwner.CREW);
        assertThatThrownBy(() -> a.tags().add(WEEKDAY_DAWN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(a).isEqualTo(TagSet.of(Set.of(WEEKDAY_EVENING), TagOwner.CREW));
        assertThat(a.hashCode())
                .isEqualTo(TagSet.of(Set.of(WEEKDAY_EVENING), TagOwner.CREW).hashCode());
        assertThat(a).isNotEqualTo(TagSet.of(Set.of(WEEKDAY_DAWN), TagOwner.CREW));
        assertThat(a.toString()).contains("WEEKDAY_EVENING");
    }

    private static void assertRejected(Set<CrewTag> tags, TagOwner owner) {
        assertThatThrownBy(() -> TagSet.of(tags, owner))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.TAG_INVALID));
    }
}
