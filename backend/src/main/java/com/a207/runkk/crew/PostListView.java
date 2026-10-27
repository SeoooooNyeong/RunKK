package com.a207.runkk.crew;

import com.a207.runkk.common.DisplayName;
import com.a207.runkk.common.Distance;
import java.util.List;

/**
 * 크루 게시글 목록(자료사전 크루게시목록, FR-131, FR-133, FR-212). 보이는 글(삭제·숨김 제외) 최신순.
 *
 * @param posts 글
 * @param nextCursor 다음 페이지 커서(게시글 ID), 마지막 페이지거나 나누지 않았으면 null
 */
public record PostListView(List<Line> posts, Long nextCursor) {

    public PostListView {
        posts = List.copyOf(posts);
    }

    /**
     * 목록 한 줄.
     *
     * @param summary 글 요약
     * @param authorName 작성자 표시명(탈퇴면 "탈퇴한 사용자")
     * @param crewContribution 지난 크루런이면 크루 주간 거리에 반영된 거리 합, 아니면 null
     * @param canDelete 열람자가 지울 수 있는가(본인 글이거나 열람자가 크루장·운영진, FR-213)
     */
    public record Line(
            PostSummary summary,
            DisplayName authorName,
            Distance crewContribution,
            boolean canDelete) {}
}
