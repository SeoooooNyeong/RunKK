package com.a207.runkk.common;

import static com.a207.runkk.common.PersonalityTag.BEGINNER_WELCOME;
import static com.a207.runkk.common.PersonalityTag.DAWN;
import static com.a207.runkk.common.PersonalityTag.DIET;
import static com.a207.runkk.common.PersonalityTag.FAMILIAR_ROUTE;
import static com.a207.runkk.common.PersonalityTag.LONG_DISTANCE;
import static com.a207.runkk.common.PersonalityTag.ROUTINE_RUN;
import static com.a207.runkk.common.PersonalityTag.SOCIAL_RUN;
import static com.a207.runkk.common.PersonalityTag.SOLO_RUN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("성향 태그 묶음 규칙")
class TagSetTest {

    @Test
    void fixedListHas19TagsIn7Categories() {
        assertThat(PersonalityTag.values()).hasSize(19);
        assertThat(TagCategory.values()).hasSize(7);
        assertThat(SOLO_RUN.category()).isEqualTo(TagCategory.COMPANION);
        assertThat(SOLO_RUN.label()).isEqualTo("솔로런");
    }

    @Test
    void of_acceptsOneToFiveTagsWithOnePerCategory() {
        TagSet tags =
                TagSet.of(Set.of(SOLO_RUN, LONG_DISTANCE, DAWN, BEGINNER_WELCOME), TagOwner.CREW);
        assertThat(tags.isEmpty()).isFalse();
        assertThat(tags.tags()).hasSize(4);
    }

    @Test
    void of_rejectsEmptyOrMoreThanFive() {
        assertRejected(Set.of(), TagOwner.CREW);
        assertRejected(
                Set.of(SOLO_RUN, LONG_DISTANCE, FAMILIAR_ROUTE, ROUTINE_RUN, DAWN, DIET),
                TagOwner.CREW);
    }

    @Test
    void of_rejectsTwoTagsInOneCategory() {
        assertRejected(Set.of(SOLO_RUN, SOCIAL_RUN), TagOwner.CREW);
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
        TagSet crew = TagSet.of(Set.of(SOLO_RUN, DAWN, BEGINNER_WELCOME), TagOwner.CREW);
        assertThat(crew.containsAll(Set.of(SOLO_RUN, BEGINNER_WELCOME))).isTrue();
        assertThat(crew.containsAll(Set.of(SOLO_RUN, LONG_DISTANCE))).isFalse();
    }

    @Test
    void tags_isImmutableAndEqualityByValue() {
        TagSet a = TagSet.of(Set.of(SOLO_RUN), TagOwner.CREW);
        assertThatThrownBy(() -> a.tags().add(DAWN))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(a).isEqualTo(TagSet.of(Set.of(SOLO_RUN), TagOwner.CREW));
        assertThat(a.hashCode()).isEqualTo(TagSet.of(Set.of(SOLO_RUN), TagOwner.CREW).hashCode());
        assertThat(a).isNotEqualTo(TagSet.of(Set.of(DAWN), TagOwner.CREW));
        assertThat(a.toString()).contains("SOLO_RUN");
    }

    private static void assertRejected(Set<PersonalityTag> tags, TagOwner owner) {
        assertThatThrownBy(() -> TagSet.of(tags, owner))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        e -> assertThat(e.reason()).isEqualTo(ReasonCode.TAG_INVALID));
    }
}
