package com.a207.runkk.crew;

import com.a207.runkk.common.PageCursor;
import java.util.Set;

/**
 * 게시글 목록 조건(FR-212).
 *
 * @param kinds 종류, 비면 전부
 * @param pinnedOnly 고정 공지만
 * @param page 페이지
 */
public record PostQuery(Set<PostKind> kinds, boolean pinnedOnly, PageCursor page) {

    public PostQuery {
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        page = page == null ? PageCursor.all() : page;
    }

    /** 모든 종류, 나누지 않음. */
    public static PostQuery all() {
        return new PostQuery(Set.of(), false, PageCursor.all());
    }
}
