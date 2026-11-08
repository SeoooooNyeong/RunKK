package com.a207.runkk.common;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 고른 크루 태그 묶음이 개수 규칙(1~3개)을 지키게 한다(설계 6.1.1, 피그마 F-04 "태그 최대 3개", S15P31A207-236). */
public final class TagSet {

    private static final int MAX_TAGS = 3;

    private final Set<CrewTag> tags;
    private final TagOwner owner;

    private TagSet(Set<CrewTag> tags, TagOwner owner) {
        this.tags = Set.copyOf(tags);
        this.owner = owner;
    }

    /** 1~3개. 같은 요일 묶음에서 여러 시간대를 골라도 된다. 위반이면 BusinessRuleException(TAG_INVALID). */
    public static TagSet of(Set<CrewTag> tags, TagOwner owner) {
        Objects.requireNonNull(owner, "owner");
        if (tags == null || tags.isEmpty() || tags.size() > MAX_TAGS) {
            throw new BusinessRuleException(ReasonCode.TAG_INVALID);
        }
        return new TagSet(tags, owner);
    }

    /** 태그 없음(크루 찾기 필터를 비운 경우, D-91). */
    public static TagSet empty(TagOwner owner) {
        return new TagSet(Set.of(), Objects.requireNonNull(owner, "owner"));
    }

    /** 모두 포함(FR-117 AND 검색). */
    public boolean containsAll(Set<CrewTag> wanted) {
        return tags.containsAll(wanted);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public Set<CrewTag> tags() {
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
