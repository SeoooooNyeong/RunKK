package com.a207.runkk.common;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 고른 성향 태그 묶음이 개수 규칙을 지키게 한다(설계 6.1.1, FR-007, Q-82). */
public final class TagSet {

    private static final int MAX_TAGS = 5;

    private final Set<PersonalityTag> tags;
    private final TagOwner owner;

    private TagSet(Set<PersonalityTag> tags, TagOwner owner) {
        this.tags = Set.copyOf(tags);
        this.owner = owner;
    }

    /** 1~5개, 분류마다 1개 이하, owner.allows(category). 위반이면 BusinessRuleException(TAG_INVALID). */
    public static TagSet of(Set<PersonalityTag> tags, TagOwner owner) {
        Objects.requireNonNull(owner, "owner");
        if (tags == null || tags.isEmpty() || tags.size() > MAX_TAGS) {
            throw new BusinessRuleException(ReasonCode.TAG_INVALID);
        }
        Set<TagCategory> categories = EnumSet.noneOf(TagCategory.class);
        for (PersonalityTag tag : tags) {
            if (!owner.allows(tag.category()) || !categories.add(tag.category())) {
                throw new BusinessRuleException(ReasonCode.TAG_INVALID);
            }
        }
        return new TagSet(tags, owner);
    }

    /** 태그 없음(가입 전·태그 미선택 러너). */
    public static TagSet empty(TagOwner owner) {
        return new TagSet(Set.of(), Objects.requireNonNull(owner, "owner"));
    }

    /** 겹치는 태그 수(FR-118 일치도). */
    public int overlapCount(TagSet other) {
        return (int) tags.stream().filter(other.tags::contains).count();
    }

    /** 모두 포함(FR-117 AND 검색). */
    public boolean containsAll(Set<PersonalityTag> wanted) {
        return tags.containsAll(wanted);
    }

    /** 분류의 선택값(FR-152 임시 유형의 동행·코스 축). */
    public Optional<PersonalityTag> valueOf(TagCategory c) {
        return tags.stream().filter(tag -> tag.category() == c).findFirst();
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
