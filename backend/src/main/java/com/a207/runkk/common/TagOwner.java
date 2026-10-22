package com.a207.runkk.common;

/** 태그를 가진 쪽. 러너는 1~6분류, 크루는 1~7분류에서 고른다(Q-82). */
public enum TagOwner {
    RUNNER(6),
    CREW(7);

    private final int lastCategory;

    TagOwner(int lastCategory) {
        this.lastCategory = lastCategory;
    }

    public boolean allows(TagCategory category) {
        return category.number() <= lastCategory;
    }
}
