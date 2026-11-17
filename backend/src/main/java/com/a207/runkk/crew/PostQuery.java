package com.a207.runkk.crew;

import com.a207.runkk.common.PageCursor;
import java.util.Set;

/**
 * 게시글 목록 조건(FR-212, 설계 05-32 4.5). 날짜순·진행 중만은 크루런만 볼 때(kinds = {CREW_RUN})만 받고 아니면
 * IllegalArgumentException(400 BAD_REQUEST)이다.
 *
 * @param kinds 종류, 비면 전부
 * @param pinnedOnly 고정 공지만
 * @param page 페이지
 * @param order 순서, null이면 최신순
 * @param activeOnly 진행 중인 크루런만(VOTING·LOBBY_OPEN·RUNNING)
 */
public record PostQuery(
        Set<PostKind> kinds,
        boolean pinnedOnly,
        PageCursor page,
        PostOrder order,
        boolean activeOnly) {

    public PostQuery {
        kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
        page = page == null ? PageCursor.all() : page;
        order = order == null ? PostOrder.NEWEST : order;
        if ((order == PostOrder.SCHEDULED_ASC || activeOnly)
                && (pinnedOnly || !kinds.equals(Set.of(PostKind.CREW_RUN)))) {
            throw new IllegalArgumentException("날짜순·진행 중만은 크루런 목록에만 쓴다");
        }
    }

    /** 최신순, 진행 상태 거르지 않음(기존 조건). */
    public PostQuery(Set<PostKind> kinds, boolean pinnedOnly, PageCursor page) {
        this(kinds, pinnedOnly, page, PostOrder.NEWEST, false);
    }

    /** 모든 종류, 나누지 않음. */
    public static PostQuery all() {
        return new PostQuery(Set.of(), false, PageCursor.all());
    }

    /** 페이지만 바꾼 조건. */
    public PostQuery withPage(PageCursor newPage) {
        return new PostQuery(kinds, pinnedOnly, newPage, order, activeOnly);
    }
}
