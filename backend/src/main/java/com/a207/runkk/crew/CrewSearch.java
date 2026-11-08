package com.a207.runkk.crew;

import com.a207.runkk.common.CrewTag;
import java.util.Set;

/**
 * 크루 검색 조건(자료사전 크루검색조건, FR-117). 태그는 모두 가진 크루만(AND), 키워드는 크루명·한줄소개에서 찾는다.
 *
 * @param tags 고른 태그, 비면 거르지 않음
 * @param keyword 키워드, 비면 거르지 않음
 */
public record CrewSearch(Set<CrewTag> tags, String keyword) {

    public CrewSearch {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
    }

    /** 조건 없는 공개 크루 전체. */
    public static CrewSearch all() {
        return new CrewSearch(Set.of(), null);
    }
}
