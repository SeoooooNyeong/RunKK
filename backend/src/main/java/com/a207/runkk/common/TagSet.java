package com.a207.runkk.common;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 고른 크루 성향 태그 묶음이 개수 규칙을 지키게 한다(설계 6.1.1, Q-82, v0.11 D-91). */
public final class TagSet {

    private static final int MAX_TAGS = 5;

    private final Set<PersonalityTag> tags;
    private final TagOwner owner;

    private TagSet(Set<PersonalityTag> tags, TagOwner owner) {
        this.tags = Set.copyOf(tags);
        this.owner = owner;
    }

    /** 1~5개, 분류마다 1개 이하. 위반이면 BusinessRuleException(TAG_INVALID). */
    public static TagSet of(Set<PersonalityTag> tags, TagOwner owner) {
        Objects.requireNonNull(owner, "owner");
        if (tags == null || tags.isEmpty() || tags.size() > MAX_TAGS) {
            throw new BusinessRuleException(ReasonCode.TAG_INVALID);
        }
        Set<TagCategory> categories = EnumSet.noneOf(TagCategory.class);
        for (PersonalityTag tag : tags) {
            if (!categories.add(tag.category())) {
                throw new BusinessRuleException(ReasonCode.TAG_INVALID);
            }
        }
        return new TagSet(tags, owner);
    }

    /** 태그 없음(크루 찾기 필터를 비운 경우, D-91). */
    public static TagSet empty(TagOwner owner) {
        return new TagSet(Set.of(), Objects.requireNonNull(owner, "owner"));
    }

    /** 모두 포함(FR-117 AND 검색). */
    public boolean containsAll(Set<PersonalityTag> wanted) {
        return tags.containsAll(wanted);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public Set<PersonalityTag> tags() {
        return tags;
    }

    public TagOwner owner() {
        return owner;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TagSet other && tags.equals(other.tags) && owner == other.owner;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, owner);
    }

    @Override
    public String toString() {
        return "TagSet"
                + tags.stream().map(Enum::name).sorted().collect(Collectors.toList())
                + "("
                + owner
                + ")";
    }
}
